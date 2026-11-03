/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.symbol.emdk.EMDKManager
import com.symbol.emdk.EMDKResults
import com.symbol.emdk.barcode.BarcodeManager
import com.symbol.emdk.barcode.Scanner
import com.symbol.emdk.barcode.ScannerConfig
import com.symbol.emdk.barcode.StatusData

/*******************************************************************************
 ** Loaded only after checking Zebra's device-provided library and preinstalled service.
 *******************************************************************************/
internal class ZebraScannerDriver(private val context: Context) : ScannerDriver {
   /*******************************************************************************
    ** Serialize the application-wide EMDK manager claim across asynchronous driver lifetimes.
    *******************************************************************************/
   companion object {
      // EMDK supports one manager per application, including a bind that has not completed yet.
      private var managerClaimed = false
   }

   private val main = Handler(Looper.getMainLooper())
   private var listener: ScannerDriver.Listener? = null
   private var manager: EMDKManager? = null
   private var scanner: Scanner? = null
   private var closed = false
   private var opening = false
   private var ownsClaim = false

   /*******************************************************************************
    ** The scanner's output format as this driver has proven it. Text is accepted only in AIM (prefix present and
    ** confirmed by read-back) or PLAIN (unchanged or restored, confirmed by read-back); PENDING and FAILED are
    ** indeterminate, so any decode, and any readiness signal, is withheld.
    *******************************************************************************/
   private enum class Profile { PENDING, AIM, PLAIN, FAILED }

   // Application-owned, temporary: AIM is set only after applying codeIdType=AIM and reading the same value back.
   // The original value is restored (and read back) before the scanner is released.
   @Volatile private var profile = Profile.PENDING
   private var idleSeenBeforeProfile = false
   private var originalCodeIdType: ScannerConfig.CodeIdType? = null
   private var codeIdTypeChanged = false
   private val dataListener = Scanner.DataListener { collection ->
      // Copy SDK-owned data now; the collection must not be read after this callback returns. AIM is read only
      // under the confirmed profile, by AimOutputProfile; without it the text is user payload and stays untouched.
      // This runs on an SDK thread, possibly while the open sequence is still configuring: an unresolved or failed
      // format drops the decode instead of guessing. The hardware read completes and the next IDLE re-arms it.
      val mode = profile
      if (mode != Profile.AIM && mode != Profile.PLAIN) return@DataListener
      val scans = collection?.scanData
      var capture: ScannerCapture? = null
      var multiple = false
      if (collection?.result == com.symbol.emdk.barcode.ScannerResults.SUCCESS && !scans.isNullOrEmpty()) {
         if (scans.size != 1) multiple = true
         else {
            val scan = scans.single()
            val barcode = scan?.data
            val label = scan?.labelType?.toString() ?: "UNKNOWN"
            if (!barcode.isNullOrEmpty()) {
               if (mode == Profile.PLAIN) capture = ScannerCapture.of(barcode, label, scan.rawData)
               else {
                  // A missing or inconsistent prefix under the confirmed profile drops the read; it is never guessed.
                  // Raw bytes stay exactly what the SDK returned, whether or not they carry the same prefix.
                  val split = AimOutputProfile.split(barcode, label)
                  if (split != null) capture = ScannerCapture.of(split.payload, label, scan.rawData, split.aimIdentifier)
               }
            }
         }
      }
      val delivered = capture
      main.post {
         if (!closed) {
            if (delivered != null) listener?.scanned(delivered) else listener?.noData(multiple)
         }
      }
   }
   private val statusListener = Scanner.StatusListener { status ->
      main.post {
         if (!closed) {
            if (status.state == StatusData.ScannerStates.IDLE) {
               // Readiness arms a read. It is withheld until the output format is proven, then replayed once.
               if (profile == Profile.AIM || profile == Profile.PLAIN) listener?.idle()
               else if (profile == Profile.PENDING) idleSeenBeforeProfile = true
            } else if (status.state == StatusData.ScannerStates.ERROR) listener?.failed()
         }
      }
   }

   /*******************************************************************************
    ** Claim the single EMDK manager before asynchronous binding and report unavailable ownership explicitly.
    *******************************************************************************/
   override fun open(listener: ScannerDriver.Listener) {
      check(!closed && this.listener == null)
      this.listener = listener
      if (managerClaimed) {
         listener.failed(); return
      }
      managerClaimed = true
      ownsClaim = true
      opening = true
      val result = try {
         EMDKManager.getEMDKManager(context, object : EMDKManager.EMDKListener {
            /*******************************************************************************
             ** Release a late EMDK bind after closure; otherwise attach scanner listeners only for the current owner.
             *******************************************************************************/
            override fun onOpened(opened: EMDKManager) {
               main.post {
                  opening = false
                  if (closed) {
                     // The asynchronous bind can complete after the widget has already left.
                     try {
                        opened.release(); releaseClaim()
                     } catch (exception: Exception) {
                        android.util.Log.w("Scanner", "Late scanner release could not be confirmed")
                     }
                  } else {
                     manager = opened
                     try {
                        val barcodeManager = opened.getInstance(EMDKManager.FEATURE_TYPE.BARCODE) as BarcodeManager
                        val device = barcodeManager.getDevice(BarcodeManager.DeviceIdentifier.DEFAULT)
                        scanner = device
                        device.addDataListener(dataListener)
                        device.addStatusListener(statusListener)
                        device.triggerType = Scanner.TriggerType.HARD
                        device.enable()
                        if (!applyAimProfile(device)) {
                           profile = Profile.FAILED
                           this@ZebraScannerDriver.listener?.failed()
                        } else if (idleSeenBeforeProfile) {
                           idleSeenBeforeProfile = false
                           this@ZebraScannerDriver.listener?.idle()
                        }
                     } catch (exception: Exception) {
                        profile = Profile.FAILED
                        this@ZebraScannerDriver.listener?.failed()
                     }
                  }
               }
            }

            /*******************************************************************************
             ** Resolve asynchronous manager loss on the main thread without reviving the released scanner.
             *******************************************************************************/
            override fun onClosed() {
               main.post {
                  opening = false
                  if (closed) releaseClaim() else this@ZebraScannerDriver.listener?.failed()
               }
            }
         })
      } catch (exception: Exception) {
         opening = false
         releaseClaim()
         throw exception
      }
      if (result.statusCode != EMDKResults.STATUS_CODE.SUCCESS) {
         opening = false
         releaseClaim()
         listener.failed()
      }
   }

   /*******************************************************************************
    ** Read EMDK's pending state rather than inferring it from callback order.
    *******************************************************************************/
   override fun isReadPending(): Boolean = scanner?.isReadPending ?: false

   /*******************************************************************************
    ** Delegate a read only after the session has acquired and enabled the hardware scanner.
    *******************************************************************************/
   override fun read() {
      requireNotNull(scanner).read()
   }

   /*******************************************************************************
    ** Avoid cancelling an idle scanner while still stopping any active hardware read.
    *******************************************************************************/
   override fun cancelRead() {
      scanner?.let { if (it.isReadPending) it.cancelRead() }
   }

   /*******************************************************************************
    ** Detach every listener and release device/manager resources before relinquishing the application-wide claim.
    *******************************************************************************/
   override fun close() {
      if (closed) return
      closed = true
      listener = null
      val device = scanner
      val owner = manager
      scanner = null
      manager = null
      var failure: Exception? = null
      var releaseFailure: Exception? = null

      /*******************************************************************************
       ** Attempt every resource release even when an earlier cleanup fails, then propagate the recorded failure.
       ** Only a failed device or manager release means ownership is unknown; other failures are still reported.
       *******************************************************************************/
      fun attempt(isRelease: Boolean = false, action: () -> Unit) {
         try {
            action()
         } catch (exception: Exception) {
            failure = exception
            if (isRelease) releaseFailure = exception
         }
      }
      if (device != null) {
         attempt { device.removeDataListener(dataListener) }
         attempt { device.removeStatusListener(statusListener) }
         attempt { if (device.isReadPending) device.cancelRead() }
         attempt { check(restoreCodeIdType(device)) { "Scanner output profile restoration could not be verified" } }
         attempt(isRelease = true) { device.release() }
      }
      if (owner != null) {
         attempt(isRelease = true) { owner.release(EMDKManager.FEATURE_TYPE.BARCODE) }
         attempt(isRelease = true) { owner.release() }
      }
      // A confirmed device and manager release frees the application-wide claim even when restoring the scanner's
      // output format failed; an unconfirmed release keeps it, and a pending asynchronous bind still resolves it later.
      if (!opening && releaseFailure == null) releaseClaim()
      failure?.let { throw it }
   }

   /*******************************************************************************
    ** Ask the enabled scanner to prefix each decode with its AIM identifier and prove the resulting format by
    ** reading it back, whatever the setter reported. A setter that threw is not trusted either way: the read-back
    ** decides. AIM confirmed -> AIM; unchanged or restored-and-verified -> PLAIN; anything else -> false (the caller
    ** fails the open), because prefixed text would then be indistinguishable from user data. An unsupported setting
    ** changes nothing and leaves AIM absent (PLAIN). Reading the original value may throw; the caller fails the open.
    *******************************************************************************/
   private fun applyAimProfile(device: Scanner): Boolean {
      val config = device.config
      if (!config.isParamSupported("config.scanParams.codeIdType")) {
         profile = Profile.PLAIN
         return true
      }
      val original = config.scanParams.codeIdType
      originalCodeIdType = original
      codeIdTypeChanged = true
      config.scanParams.codeIdType = ScannerConfig.CodeIdType.AIM
      try {
         device.config = config
      } catch (exception: Exception) {
         android.util.Log.w("Scanner", "AIM output profile could not be applied")
      }
      val effective = try {
         device.config.scanParams.codeIdType
      } catch (exception: Exception) {
         null
      }
      return when (classifyAimReadback(original, ScannerConfig.CodeIdType.AIM, effective)) {
         AimReadback.CONFIRMED -> {
            // Nothing to undo when the scanner already reported AIM before this driver touched it.
            codeIdTypeChanged = original != ScannerConfig.CodeIdType.AIM
            profile = Profile.AIM
            true
         }
         AimReadback.UNCHANGED -> {
            codeIdTypeChanged = false
            profile = Profile.PLAIN
            true
         }
         AimReadback.INDETERMINATE -> restoreCodeIdType(device).also { if (it) profile = Profile.PLAIN }
      }
   }

   /*******************************************************************************
    ** Put back the value read before this driver changed it and prove it by reading it back; setter success alone is
    ** not proof. The profile is FAILED until proven, so no decode is trusted while the format is indeterminate.
    *******************************************************************************/
   private fun restoreCodeIdType(device: Scanner): Boolean {
      val original = originalCodeIdType
      if (!codeIdTypeChanged || original == null) return true
      profile = Profile.FAILED
      val verified = try {
         val config = device.config
         config.scanParams.codeIdType = original
         device.config = config
         device.config.scanParams.codeIdType == original
      } catch (exception: Exception) {
         false
      }
      if (verified) codeIdTypeChanged = false
      return verified
   }

   /*******************************************************************************
    ** Relinquish only this driver's manager claim after asynchronous opening and cleanup are resolved.
    *******************************************************************************/
   private fun releaseClaim() {
      if (ownsClaim) {
         ownsClaim = false; managerClaimed = false
      }
   }
}
