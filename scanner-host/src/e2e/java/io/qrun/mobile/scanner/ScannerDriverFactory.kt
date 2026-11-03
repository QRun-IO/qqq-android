/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

internal const val HARDWARE_SCAN_SOURCE = "e2e-fixture"
internal const val E2E_RAW_DATA_EXTRA = "io.qrun.mobile.scanner.e2e.raw_data"
internal const val E2E_AIM_IDENTIFIER_EXTRA = "io.qrun.mobile.scanner.e2e.aim_identifier"

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
         // Test-only fixture metadata: exact bytes and an explicit AIM identifier, never derived from the text.
         val raw = intent.getByteArrayExtra(E2E_RAW_DATA_EXTRA)
         val aim = intent.getStringExtra(E2E_AIM_IDENTIFIER_EXTRA)
         listener?.scanned(ScannerCapture.of(data, intent.getStringExtra("com.symbol.datawedge.label_type") ?: "UNKNOWN", raw, aim))
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
