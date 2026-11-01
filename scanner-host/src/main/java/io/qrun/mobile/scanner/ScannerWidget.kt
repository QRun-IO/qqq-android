/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import android.content.Context
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kingsrook.qqq.frontend.android.mobileapp.ui.widgets.blocks.WidgetBlockParameters
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.ProcessAcknowledgment
import com.kingsrook.qqq.frontend.android.mobileapp.ui.LocalProcessInputAllowed

/*******************************************************************************
 ** A server-defined SCANNER widget accepts device-owned callbacks only while visible and resumed.
 *******************************************************************************/
@Composable
internal fun ScannerWidget(
   parameters: WidgetBlockParameters, modifier: Modifier, disabled: Boolean,
   driverFactory: (Context) -> ScannerDriver? = ::createScannerDriver
) {
   val context = LocalContext.current
   val view = LocalView.current
   val windowFocused = LocalWindowInfo.current.isWindowFocused
   val inputAllowed = LocalProcessInputAllowed.current
   val focusManager = LocalFocusManager.current
   val keyboard = LocalSoftwareKeyboardController.current
   val lifecycle = LocalLifecycleOwner.current.lifecycle
   var manual by rememberSaveable { mutableStateOf("") }
   var notice by remember { mutableStateOf("") }
   var scannerStatus by remember { mutableStateOf("") }
   var canReconnect by remember { mutableStateOf(false) }
   val currentDisabled by rememberUpdatedState(disabled)
   val metadataResult = runCatching { ScannerMetaData.parse(parameters.widgetBlock.values) }
   if (metadataResult.isFailure) {
      Text("This scanner control has unsupported metadata. Contact your administrator.", modifier.padding(20.dp))
      return
   }
   val scannerMetaData = metadataResult.getOrThrow()
   val process = parameters.qViewModel?.activeProcessViewModel

   /*******************************************************************************
    ** Bind input eligibility to the same active process and loaded server step that rendered this widget.
    *******************************************************************************/
   fun processReady() = process != null && parameters.qViewModel?.activeProcessViewModel === process &&
      !currentDisabled && !process.cancelling && !process.waitingOnBackend && process.pendingStepValues == null

   /*******************************************************************************
    ** Combine operator focus, lifecycle and process readiness before accepting hardware or manual input.
    *******************************************************************************/
   fun inputReady() = inputAllowed() && view.hasWindowFocus() &&
      lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && processReady()

   val ready = windowFocused && inputAllowed() && processReady()
   val currentInputReady by rememberUpdatedState<() -> Boolean>(::inputReady)
   LaunchedEffect(parameters.values["acknowledgedEventId"]) { notice = "" }

   /*******************************************************************************
    ** Each new physical scan has an identity; retries are handled by ProcessViewModel.
    *******************************************************************************/
   fun submit(barcode: String, symbology: String, source: String): Boolean {
      if (process == null) {
         notice = "This scanner control requires an active process."
         return false
      }
      if (!inputReady()) {
         notice = "Wait for the previous scan to be confirmed."
         return false
      }
      if (barcode.isEmpty() || barcode.length > scannerMetaData.maxLength) {
         notice = "Scan must contain between 1 and ${scannerMetaData.maxLength} characters."
         return false
      }
      notice = "Sending scan…"
      val event = ScanEvent(barcode, symbology, source)
      process.runStep(
         event.values(), ProcessAcknowledgment(
            "acknowledgedEventId", event.eventId,
            requiredValues = mapOf("scanSaved" to true), nonEmptyStringFields = setOf("scanId")
         )
      )
      // runStep can reject local metadata/validation before accepting an event.
      // A synchronous test repository can also finish and acknowledge it immediately.
      val accepted = process.pendingStepValues?.get("eventId") == event.eventId ||
         process.processValues["acknowledgedEventId"] == event.eventId
      if (!accepted) notice = "Scan was not submitted. Review the process message and try again."
      return accepted
   }

   val currentSubmit by rememberUpdatedState<(String, String, String) -> Boolean>(::submit)
   val input = remember(context, process, driverFactory)
   {
      ScannerInputSession(
         { driverFactory(context) },
         { barcode, symbology -> currentSubmit(barcode, symbology, HARDWARE_SCAN_SOURCE) },
         { message, reconnect -> scannerStatus = message; canReconnect = reconnect },
         canAcceptInput = { currentInputReady() })
   }
   SideEffect { input.setAccepting(ready) }
   DisposableEffect(input, lifecycle)
   {
      /*******************************************************************************
       ** Let the current lifecycle decide hardware ownership rather than retaining capture after pause.
       *******************************************************************************/
      fun syncRegistration() {
         input.setActive(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
      }

      val observer = LifecycleEventObserver { _, _ -> syncRegistration() }
      lifecycle.addObserver(observer)
      syncRegistration()
      onDispose { lifecycle.removeObserver(observer); input.setActive(false) }
   }

   Column(modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp))
   {
      Text(parameters.widgetBlock.values["title"]?.toString() ?: "Scan a barcode")
      Text(parameters.widgetBlock.values["instructions"]?.toString() ?: "Press the handheld scan trigger. Wait for server confirmation before scanning again.")
      if (parameters.values["scanSaved"] == true && notice.isEmpty()) {
         Text("${scannerMetaData.acknowledgmentLabel} ${parameters.values["scanId"] ?: ""}")
      }
      if (notice.isNotEmpty()) Text(notice)
      if (scannerStatus.isNotEmpty()) Text(scannerStatus)
      if (canReconnect) Button(
         enabled = ready,
         onClick = { input.reconnect() }) { Text("Reconnect scanner") }
      if (scannerMetaData.manualEntryEnabled) {
         OutlinedTextField(
            manual, { manual = it }, label = { Text(scannerMetaData.manualLabel) }, enabled = ready, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); keyboard?.hide() })
         )
         Button(enabled = ready && manual.isNotEmpty(), onClick = {
            focusManager.clearFocus(); keyboard?.hide()
            if (submit(manual, "MANUAL", "manual")) manual = ""
         }) { Text(scannerMetaData.submitLabel) }
      }
   }
}
