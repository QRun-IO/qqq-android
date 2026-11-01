/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

/*******************************************************************************
 ** Device boundary; implementations deliver callbacks on the main thread.
 *******************************************************************************/
internal interface ScannerDriver {
   /*******************************************************************************
    ** Deliver scanner lifecycle outcomes on the main thread so widget ownership checks remain serialized.
    *******************************************************************************/
   interface Listener {
      /*******************************************************************************
       ** Use hardware idle and pending-read state together before arming the current visible widget.
       *******************************************************************************/
      fun idle()

      /*******************************************************************************
       ** Preserve raw barcode data and reject callbacks after ownership, readiness or generation changes.
       *******************************************************************************/
      fun scanned(barcode: String, symbology: String)

      /*******************************************************************************
       ** Distinguish ordinary empty reads from rejected multi-barcode reads without fabricating a scan.
       *******************************************************************************/
      fun noData(multipleBarcodes: Boolean)

      /*******************************************************************************
       ** Report hardware failure only for the active generation so stale callbacks cannot disable a replacement.
       *******************************************************************************/
      fun failed()
   }

   /*******************************************************************************
    ** Acquire the device for this listener; implementations must not deliver callbacks from a released owner.
    *******************************************************************************/
   fun open(listener: Listener)

   /*******************************************************************************
    ** Expose the device's actual read state so an idle callback cannot arm a duplicate read.
    *******************************************************************************/
   fun isReadPending(): Boolean

   /*******************************************************************************
    ** Arm hardware capture only after the owning session has established readiness.
    *******************************************************************************/
   fun read()

   /*******************************************************************************
    ** Cancel any outstanding read before leaving the accepting state or releasing scanner ownership.
    *******************************************************************************/
   fun cancelRead()

   /*******************************************************************************
    ** Release callbacks and hardware ownership; failure must remain visible to the session owner.
    *******************************************************************************/
   fun close()
}

/*******************************************************************************
 ** One visible, resumed scanner owns the hardware; released generations cannot submit scans.
 *******************************************************************************/
internal class ScannerInputSession(
   private val createDriver: () -> ScannerDriver?,
   private val onScan: (String, String) -> Unit,
   private val onStatus: (String, Boolean) -> Unit,
   private val canAcceptInput: () -> Boolean = { true },
) {
   private var driver: ScannerDriver? = null
   private var generation = 0L
   private var active = false
   private var accepting = false
   private var idle = false
   private var armed = false
   private var releaseFailed = false
   private var readNotice: String? = null

   /*******************************************************************************
    ** Advance the ownership generation before releasing or acquiring the scanner for a resumed widget.
    *******************************************************************************/
   fun setActive(value: Boolean) {
      if (active == value) return
      active = value
      generation++
      release()
      if (!value) return
      if (releaseFailed) return
      val current = try {
         createDriver()
      } catch (exception: Exception) {
         null
      }
      if (current == null) {
         onStatus("Hardware scanning is unavailable. Enter a barcode and submit it manually.", false)
         return
      }
      driver = current
      readNotice = null
      val token = generation
      onStatus("Connecting to scanner…", false)
      try {
         current.open(object : ScannerDriver.Listener {
            /*******************************************************************************
             ** Bind callbacks to the exact driver and active generation that opened them.
             *******************************************************************************/
            private fun isCurrent() = active && generation == token && driver === current

            /*******************************************************************************
             ** Use hardware idle and pending-read state together before arming the current visible widget.
             *******************************************************************************/
            override fun idle() {
               if (!isCurrent()) return
               try {
                  // A read can finish without data (for example, trigger release or timeout).
                  if (!current.isReadPending()) armed = false
                  idle = true
                  arm()
               } catch (exception: Exception) {
                  fail()
               }
            }

            /*******************************************************************************
             ** Preserve raw barcode data and reject callbacks after ownership, readiness or generation changes.
             *******************************************************************************/
            override fun scanned(barcode: String, symbology: String) {
               if (!isCurrent() || !accepting || !armed || !canAcceptInput()) return
               armed = false
               accepting = false
               readNotice = null
               onStatus("Waiting for scan confirmation…", false)
               onScan(barcode, symbology)
               // IDLE may precede data. Stop any read armed in that interval before accepting more input.
               try {
                  if (driver === current && current.isReadPending()) current.cancelRead()
               } catch (exception: Exception) {
                  fail()
               }
            }

            /*******************************************************************************
             ** Distinguish ordinary empty reads from rejected multi-barcode reads without fabricating a scan.
             *******************************************************************************/
            override fun noData(multipleBarcodes: Boolean) {
               if (!isCurrent() || !accepting) return
               // Cancellation/empty collections are ordinary read completion. Only IDLE
               // and the driver's pending state may rearm; never release the hardware here.
               if (multipleBarcodes) {
                  readNotice = "Multiple barcodes were rejected. Scan one barcode at a time."
                  onStatus(requireNotNull(readNotice), false)
               }
            }

            /*******************************************************************************
             ** Report hardware failure only for the active generation so stale callbacks cannot disable a replacement.
             *******************************************************************************/
            override fun failed() {
               if (isCurrent()) fail()
            }
         })
      } catch (exception: Exception) {
         fail()
      }
   }

   /*******************************************************************************
    ** Retry acquisition only when the prior hardware release was confirmed.
    *******************************************************************************/
   fun reconnect() {
      if (!active || releaseFailed) return
      setActive(false)
      setActive(true)
   }

   /*******************************************************************************
    ** Stop outstanding reads while server acknowledgment or UI readiness prevents another submission.
    *******************************************************************************/
   fun setAccepting(value: Boolean) {
      accepting = value
      if (!value && armed) {
         armed = false
         try {
            driver?.cancelRead()
         } catch (exception: Exception) {
            fail()
         }
      }
      if (value) arm()
   }

   /*******************************************************************************
    ** Start at most one read after ownership, widget readiness and hardware idle agree.
    *******************************************************************************/
   private fun arm() {
      val current = driver ?: return
      if (!active || !accepting || !idle || armed || !canAcceptInput()) return
      try {
         if (current.isReadPending()) return
         armed = true
         idle = false
         current.read()
         if (driver === current && armed) onStatus(readNotice ?: "Scanner ready. Press the handheld trigger.", false)
      } catch (exception: Exception) {
         fail()
      }
   }

   /*******************************************************************************
    ** Invalidate callbacks before release and distinguish retryable hardware loss from unconfirmed ownership release.
    *******************************************************************************/
   private fun fail() {
      generation++
      val released = release()
      onStatus(
         if (released) "Hardware scanning is unavailable. Reconnect the scanner or enter a barcode manually."
         else "Scanner release could not be confirmed. Restart QQQ Mobile before using the scanner.", released
      )
   }

   /*******************************************************************************
    ** Clear local ownership before closing the driver; an unconfirmed close prevents later acquisition.
    *******************************************************************************/
   private fun release(): Boolean {
      val previous = driver
      driver = null
      idle = false
      armed = false
      return try {
         previous?.close(); true
      } catch (exception: Exception) {
         releaseFailed = true
         onStatus("Scanner release could not be confirmed. Restart QQQ Mobile before using the scanner.", false)
         false
      }
   }
}
