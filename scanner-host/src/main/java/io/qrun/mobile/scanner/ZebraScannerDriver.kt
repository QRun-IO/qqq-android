/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.symbol.emdk.EMDKManager
import com.symbol.emdk.EMDKResults
import com.symbol.emdk.barcode.BarcodeManager
import com.symbol.emdk.barcode.Scanner
import com.symbol.emdk.barcode.StatusData

/** Loaded only after checking Zebra's device-provided library and preinstalled service. */
internal class ZebraScannerDriver(private val context: Context) : ScannerDriver
{
   companion object
   {
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
   private val dataListener = Scanner.DataListener { collection ->
      main.post {
         if(!closed)
         {
            val scans = collection?.scanData
            if(collection?.result != com.symbol.emdk.barcode.ScannerResults.SUCCESS || scans.isNullOrEmpty())
               listener?.noData(false)
            else if(scans.size != 1) listener?.noData(true)
            else
            {
               val scan = scans.single()
               val barcode = scan?.data
               if(barcode.isNullOrEmpty()) listener?.noData(false)
               else listener?.scanned(barcode, scan.labelType?.toString() ?: "UNKNOWN")
            }
         }
      }
   }
   private val statusListener = Scanner.StatusListener { status ->
      main.post {
         if(!closed)
         {
            if(status.state == StatusData.ScannerStates.IDLE) listener?.idle()
            else if(status.state == StatusData.ScannerStates.ERROR) listener?.failed()
         }
      }
   }

   override fun open(listener: ScannerDriver.Listener)
   {
      check(!closed && this.listener == null)
      this.listener = listener
      if(managerClaimed) { listener.failed(); return }
      managerClaimed = true
      ownsClaim = true
      opening = true
      val result = try { EMDKManager.getEMDKManager(context, object : EMDKManager.EMDKListener
      {
         override fun onOpened(opened: EMDKManager)
         {
            main.post {
               opening = false
               if(closed)
               {
                  // The asynchronous bind can complete after the widget has already left.
                  try { opened.release(); releaseClaim() }
                  catch(exception: Exception) { android.util.Log.w("Scanner", "Late scanner release could not be confirmed") }
               }
               else
               {
                  manager = opened
                  try
                  {
                     val barcodeManager = opened.getInstance(EMDKManager.FEATURE_TYPE.BARCODE) as BarcodeManager
                     val device = barcodeManager.getDevice(BarcodeManager.DeviceIdentifier.DEFAULT)
                     scanner = device
                     device.addDataListener(dataListener)
                     device.addStatusListener(statusListener)
                     device.triggerType = Scanner.TriggerType.HARD
                     device.enable()
                  }
                  catch(exception: Exception) { this@ZebraScannerDriver.listener?.failed() }
               }
            }
         }
         override fun onClosed()
         {
            main.post {
               opening = false
               if(closed) releaseClaim() else this@ZebraScannerDriver.listener?.failed()
            }
         }
      }) }
      catch(exception: Exception)
      {
         opening = false
         releaseClaim()
         throw exception
      }
      if(result.statusCode != EMDKResults.STATUS_CODE.SUCCESS)
      {
         opening = false
         releaseClaim()
         listener.failed()
      }
   }

   override fun isReadPending(): Boolean = scanner?.isReadPending ?: false
   override fun read() { requireNotNull(scanner).read() }
   override fun cancelRead() { scanner?.let { if(it.isReadPending) it.cancelRead() } }

   override fun close()
   {
      if(closed) return
      closed = true
      listener = null
      val device = scanner
      val owner = manager
      scanner = null
      manager = null
      var failure: Exception? = null
      fun attempt(action: () -> Unit) { try { action() } catch(exception: Exception) { failure = exception } }
      if(device != null)
      {
         attempt { device.removeDataListener(dataListener) }
         attempt { device.removeStatusListener(statusListener) }
         attempt { if(device.isReadPending) device.cancelRead() }
         attempt { device.release() }
      }
      if(owner != null)
      {
         attempt { owner.release(EMDKManager.FEATURE_TYPE.BARCODE) }
         attempt { owner.release() }
      }
      if(!opening && failure == null) releaseClaim()
      failure?.let { throw it }
   }

   private fun releaseClaim()
   {
      if(ownsClaim) { ownsClaim = false; managerClaimed = false }
   }
}
