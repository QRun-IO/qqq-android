/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

internal const val HARDWARE_SCAN_SOURCE = "e2e-fixture"

/*******************************************************************************
 ** Disposable E2E package only. No debug/release source set contains this ingress.
 *******************************************************************************/
internal fun createScannerDriver(context: Context): ScannerDriver = object : ScannerDriver {
   private var listener: ScannerDriver.Listener? = null
   private var registered = false
   private var pending = false
   private val receiver = object : BroadcastReceiver() {
      /*******************************************************************************
       **
       *******************************************************************************/
      override fun onReceive(context: Context?, intent: Intent?) {
         if (!pending || intent?.action != "io.qrun.mobile.scanner.SCAN") return
         val data = intent.getStringExtra("com.symbol.datawedge.data_string") ?: return
         if (intent.getStringExtra("com.symbol.datawedge.decode_mode") == "multiple" || intent.hasExtra("com.symbol.datawedge.barcodes")) return
         pending = false
         listener?.scanned(data, intent.getStringExtra("com.symbol.datawedge.label_type") ?: "UNKNOWN")
         listener?.idle()
      }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   override fun open(listener: ScannerDriver.Listener) {
      this.listener = listener
      ContextCompat.registerReceiver(context, receiver, IntentFilter("io.qrun.mobile.scanner.SCAN").apply {
         addCategory(Intent.CATEGORY_DEFAULT)
      }, ContextCompat.RECEIVER_EXPORTED)
      registered = true
      listener.idle()
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   override fun isReadPending() = pending

   /*******************************************************************************
    **
    *******************************************************************************/
   override fun read() {
      pending = true
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   override fun cancelRead() {
      pending = false; listener?.idle()
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   override fun close() {
      listener = null
      pending = false
      if (registered) {
         context.unregisterReceiver(receiver); registered = false
      }
   }
}
