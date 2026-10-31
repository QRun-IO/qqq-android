/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kingsrook.qqq.frontend.android.mobileapp.ui.widgets.blocks.WidgetBlockParameters
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.ProcessAcknowledgment

const val SCAN_ACTION = "io.qrun.mobile.scanner.SCAN"

/** A server-defined SCANNER widget consumes typed DataWedge extras only while visible. */
@Composable
fun ScannerWidget(parameters: WidgetBlockParameters, modifier: Modifier, disabled: Boolean)
{
   val context = LocalContext.current
   val focusManager = LocalFocusManager.current
   val keyboard = LocalSoftwareKeyboardController.current
   val lifecycle = LocalLifecycleOwner.current.lifecycle
   var manual by rememberSaveable { mutableStateOf("") }
   var notice by remember { mutableStateOf("") }
   val currentDisabled by rememberUpdatedState(disabled)
   val metadataResult = runCatching { ScannerMetaData.parse(parameters.widgetBlock.values) }
   if(metadataResult.isFailure)
   {
      Text("This scanner control has unsupported metadata. Contact your administrator.", modifier.padding(20.dp))
      return
   }
   val scannerMetaData = metadataResult.getOrThrow()
   val process = parameters.qViewModel?.activeProcessViewModel
   LaunchedEffect(parameters.values["acknowledgedEventId"]) { notice = "" }

   /** Each new physical scan has an identity; retries are handled by ProcessViewModel. */
   fun submit(barcode: String, symbology: String, source: String)
   {
      if(currentDisabled || process?.pendingStepValues != null)
      {
         notice = "Wait for the previous scan to be confirmed."
         return
      }
      if(barcode.isEmpty() || barcode.length > scannerMetaData.maxLength)
      {
         notice = "Scan must contain between 1 and ${scannerMetaData.maxLength} characters."
         return
      }
      notice = "Sending scan…"
      val event = ScanEvent(barcode, symbology, source)
      if(process == null)
      {
         notice = "This scanner control requires an active process."
         return
      }
      process.runStep(event.values(), ProcessAcknowledgment("acknowledgedEventId", event.eventId,
         requiredValues = mapOf("scanSaved" to true), nonEmptyStringFields = setOf("scanId")))
   }

   val currentSubmit by rememberUpdatedState<(String, String, String) -> Unit>(::submit)
   DisposableEffect(context, lifecycle)
   {
      var registered = false
      val receiver = object : BroadcastReceiver()
      {
         override fun onReceive(receiverContext: Context?, intent: Intent?)
         {
            if(intent?.action != SCAN_ACTION) return
            if(intent.getStringExtra("com.symbol.datawedge.decode_mode") == "multiple" || intent.hasExtra("com.symbol.datawedge.barcodes"))
            {
               notice = "Multiple barcode decoding is not supported. Configure DataWedge for a single barcode."
               return
            }
            val barcode = intent.getStringExtra("com.symbol.datawedge.data_string") ?: return
            val symbology = intent.getStringExtra("com.symbol.datawedge.label_type") ?: "UNKNOWN"
            val source = intent.getStringExtra("com.symbol.datawedge.source") ?: "scanner"
            currentSubmit(barcode, symbology, source)
         }
      }
      fun syncRegistration()
      {
         val resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
         if(resumed && !registered)
         {
            ContextCompat.registerReceiver(context, receiver, IntentFilter(SCAN_ACTION).apply { addCategory(Intent.CATEGORY_DEFAULT) }, ContextCompat.RECEIVER_EXPORTED)
            registered = true
         }
         else if(!resumed && registered)
         {
            context.unregisterReceiver(receiver)
            registered = false
         }
      }
      val observer = LifecycleEventObserver { _, _ -> syncRegistration() }
      lifecycle.addObserver(observer)
      syncRegistration()
      onDispose { lifecycle.removeObserver(observer); if(registered) context.unregisterReceiver(receiver) }
   }

   Column(modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp))
   {
      Text(parameters.widgetBlock.values["title"]?.toString() ?: "Scan a barcode")
      Text(parameters.widgetBlock.values["instructions"]?.toString() ?: "Press the handheld scan trigger. Wait for server confirmation before scanning again.")
      if(parameters.values["scanSaved"] == true && notice.isEmpty())
      {
         Text("Saved scan ${parameters.values["scanId"] ?: ""}")
      }
      if(notice.isNotEmpty()) Text(notice)
      OutlinedTextField(manual, { manual = it }, label = { Text(scannerMetaData.manualLabel) }, enabled = !disabled, singleLine = true,
         keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
         keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); keyboard?.hide() }))
      Button(enabled = !disabled && manual.isNotEmpty(), onClick = {
         focusManager.clearFocus(); keyboard?.hide(); submit(manual, "MANUAL", "manual")
      }) { Text(scannerMetaData.submitLabel) }
   }
}
