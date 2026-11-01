/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

/** Device boundary; implementations deliver callbacks on the main thread. */
internal interface ScannerDriver
{
   interface Listener
   {
      fun idle()
      fun scanned(barcode: String, symbology: String)
      fun noData(multipleBarcodes: Boolean)
      fun failed()
   }
   fun open(listener: Listener)
   fun isReadPending(): Boolean
   fun read()
   fun cancelRead()
   fun close()
}

/** One visible, resumed scanner owns the hardware; released generations cannot submit scans. */
internal class ScannerInputSession(
   private val createDriver: () -> ScannerDriver?,
   private val onScan: (String, String) -> Unit,
   private val onStatus: (String, Boolean) -> Unit,
   private val canAcceptInput: () -> Boolean = { true },
)
{
   private var driver: ScannerDriver? = null
   private var generation = 0L
   private var active = false
   private var accepting = false
   private var idle = false
   private var armed = false
   private var releaseFailed = false
   private var readNotice: String? = null

   fun setActive(value: Boolean)
   {
      if(active == value) return
      active = value
      generation++
      release()
      if(!value) return
      if(releaseFailed) return
      val current = try { createDriver() } catch(exception: Exception) { null }
      if(current == null)
      {
         onStatus("Hardware scanning is unavailable. Enter a barcode and submit it manually.", false)
         return
      }
      driver = current
      readNotice = null
      val token = generation
      onStatus("Connecting to scanner…", false)
      try
      {
         current.open(object : ScannerDriver.Listener
         {
            private fun isCurrent() = active && generation == token && driver === current
            override fun idle()
            {
               if(!isCurrent()) return
               try
               {
                  // A read can finish without data (for example, trigger release or timeout).
                  if(!current.isReadPending()) armed = false
                  idle = true
                  arm()
               }
               catch(exception: Exception) { fail() }
            }
            override fun scanned(barcode: String, symbology: String)
            {
               if(!isCurrent() || !accepting || !armed || !canAcceptInput()) return
               armed = false
               accepting = false
               readNotice = null
               onStatus("Waiting for scan confirmation…", false)
               onScan(barcode, symbology)
               // IDLE may precede data. Stop any read armed in that interval before accepting more input.
               try { if(driver === current && current.isReadPending()) current.cancelRead() }
               catch(exception: Exception) { fail() }
            }
            override fun noData(multipleBarcodes: Boolean)
            {
               if(!isCurrent() || !accepting) return
               // Cancellation/empty collections are ordinary read completion. Only IDLE
               // and the driver's pending state may rearm; never release the hardware here.
               if(multipleBarcodes)
               {
                  readNotice = "Multiple barcodes were rejected. Scan one barcode at a time."
                  onStatus(requireNotNull(readNotice), false)
               }
            }
            override fun failed() { if(isCurrent()) fail() }
         })
      }
      catch(exception: Exception) { fail() }
   }

   fun reconnect()
   {
      if(!active || releaseFailed) return
      setActive(false)
      setActive(true)
   }

   fun setAccepting(value: Boolean)
   {
      accepting = value
      if(!value && armed)
      {
         armed = false
         try { driver?.cancelRead() } catch(exception: Exception) { fail() }
      }
      if(value) arm()
   }

   private fun arm()
   {
      val current = driver ?: return
      if(!active || !accepting || !idle || armed || !canAcceptInput()) return
      try
      {
         if(current.isReadPending()) return
         armed = true
         idle = false
         current.read()
         if(driver === current && armed) onStatus(readNotice ?: "Scanner ready. Press the handheld trigger.", false)
      }
      catch(exception: Exception) { fail() }
   }

   private fun fail()
   {
      generation++
      val released = release()
      onStatus(if(released) "Hardware scanning is unavailable. Reconnect the scanner or enter a barcode manually."
         else "Scanner release could not be confirmed. Restart QQQ Mobile before using the scanner.", released)
   }

   private fun release(): Boolean
   {
      val previous = driver
      driver = null
      idle = false
      armed = false
      return try { previous?.close(); true }
      catch(exception: Exception)
      {
         releaseFailed = true
         onStatus("Scanner release could not be confirmed. Restart QQQ Mobile before using the scanner.", false)
         false
      }
   }
}
