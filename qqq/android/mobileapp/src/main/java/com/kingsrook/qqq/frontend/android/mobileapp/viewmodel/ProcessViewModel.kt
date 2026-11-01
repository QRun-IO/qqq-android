/*
 * SPDX-License-Identifier: Apache-2.0
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2004-2024.  Kingsrook, LLC
 * 651 N Broad St Ste 205 # 6917 | Middletown DE 19709 | United States
 * contact@kingsrook.com
 * https://github.com/Kingsrook/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.kingsrook.qqq.frontend.android.mobileapp.viewmodel

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kingsrook.qqq.frontend.android.core.controllers.QQQRepository
import com.kingsrook.qqq.frontend.android.core.model.QPossibleValue
import com.kingsrook.qqq.frontend.android.core.model.metadata.QFieldMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.QFrontendStepMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.QInstance
import com.kingsrook.qqq.frontend.android.core.model.metadata.QProcessMetaData
import com.kingsrook.qqq.frontend.android.core.model.processes.ProcessStepResult
import com.kingsrook.qqq.frontend.android.core.model.processes.QJobComplete
import com.kingsrook.qqq.frontend.android.core.model.processes.QJobError
import com.kingsrook.qqq.frontend.android.core.model.processes.QJobRunning
import com.kingsrook.qqq.frontend.android.core.model.processes.QJobStarted
import com.kingsrook.qqq.frontend.android.core.model.widgets.WidgetBlock
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelChildren
import java.time.LocalDateTime


private const val TAG = "ProcessViewModel"

/*******************************************************************************
 *******************************************************************************/
open class ProcessViewModel : ViewModel() {
   lateinit var qqqRepository: QQQRepository
   lateinit var qInstance: QInstance
   lateinit var processName: String
   lateinit var qViewModel: QViewModel
   var supportedCustomWidgetTypes: Set<String> = emptySet()
   var fieldErrors: Map<String, String> by mutableStateOf(emptyMap())
      private set
   var cancelling: Boolean by mutableStateOf(false)
      private set
   var cancellationFailed: Boolean by mutableStateOf(false)
      private set
   var submissionError: String? by mutableStateOf(null)
      private set
   private var processComplete: Boolean by mutableStateOf(false)
   private var initializing = false

   /** Options from server possible-value sources (`searchProcessPossibleValues`), keyed by field name. */
   val remoteOptions = mutableStateMapOf<String, List<QPossibleValue>>()
   val remoteOptionErrors = mutableStateMapOf<String, String>()

   /** Incremented when this model clears a field's value, so the rendered control can follow. */
   val fieldResets = mutableStateMapOf<String, Int>()
   private var dependencyVersion by mutableIntStateOf(0)
   private val remoteOptionJobs = mutableMapOf<String, Job>()
   private val remoteOptionGenerations = mutableMapOf<String, Int>()

   /*******************************************************************************
    ** Fields whose choices come from a named possible-value source rather than inline metadata.
    *******************************************************************************/
   private fun isRemote(field: QFieldMetaData) = field.possibleValueSourceName != null && field.inlinePossibleValueSource.isNullOrEmpty()

   /*******************************************************************************
    ** DISABLE_UNTIL_SET (the default for contingent fields) offers no choices until every declared parent has a value.
    *******************************************************************************/
   fun remoteOptionsBlockedReason(field: QFieldMetaData): String? {
      dependencyVersion // process values are not observable state; this makes dependent controls recompose
      if (!isRemote(field) || field.possibleValueSourceDependencyBehavior == "SHOW_ALL_UNTIL_SET") return null
      val missing = field.possibleValueSourceDependsOn.orEmpty().filter { processValues[it].let { value -> value == null || value.toString().isEmpty() } }
      if (missing.isEmpty()) return null
      val labels = missing.map { name -> activeStep?.formFields?.firstOrNull { it.name == name }?.label ?: name }
      return "Choose ${labels.joinToString(" and ")} first."
   }

   /*******************************************************************************
    ** Search the field's server source with the current form values. Only the newest request may publish, so a late
    ** response for a replaced parent value can never restore choices (or a selection) that no longer apply.
    *******************************************************************************/
   fun loadRemoteOptions(field: QFieldMetaData) {
      if (!isRemote(field)) return
      val generation = (remoteOptionGenerations[field.name] ?: 0) + 1
      remoteOptionGenerations[field.name] = generation
      remoteOptionJobs.remove(field.name)?.cancel()
      remoteOptionErrors.remove(field.name)
      remoteOptions.remove(field.name)
      if (remoteOptionsBlockedReason(field) != null) return

      val formNames = activeStep?.formFields.orEmpty().map { it.name }.toSet()
      val values = formNames.filter { it != field.name }.associateWith { processValues[it] }
      val scalars = processValues.filter { (key, value) -> key !in formNames && (value is String || value is Number || value is Boolean) }
      remoteOptionJobs[field.name] = viewModelScope.launch {
         try {
            val options = qqqRepository.processPossibleValues(processName, field.name, values, scalars)
            if (remoteOptionGenerations[field.name] != generation) return@launch
            remoteOptions[field.name] = options
            val chosen = processValues[field.name]
            if (chosen != null && options.none { it.id.toString() == chosen.toString() }) clearSelection(field.name)
         } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
         } catch (e: Exception) {
            if (remoteOptionGenerations[field.name] != generation) return@launch
            remoteOptionErrors[field.name] = semanticError(e, "The available choices could not be loaded. Try again.")
         }
      }
   }

   /*******************************************************************************
    ** After a field changes, dependents re-query (or disable) and drop selections that belonged to the old parent value.
    *******************************************************************************/
   fun fieldChanged(name: String) {
      dependencyVersion++
      for (dependent in activeStep?.formFields.orEmpty().filter { isRemote(it) && name in it.possibleValueSourceDependsOn.orEmpty() }) {
         if (processValues[dependent.name] != null) clearSelection(dependent.name)
         loadRemoteOptions(dependent)
      }
   }

   /*******************************************************************************
    ** Clear a selection that no longer fits, then apply the same rule to fields depending on it.
    *******************************************************************************/
   private fun clearSelection(name: String) {
      processValues[name] = null
      fieldResets[name] = (fieldResets[name] ?: 0) + 1
      fieldChanged(name)
   }

   private val clientWork = mutableStateMapOf<String, String>()
   val clientWorkBlockReason: String? get() = clientWork.values.firstOrNull()

   /*******************************************************************************
    ** Native controls retain their own operation identity while preventing process exit or advancement.
    *******************************************************************************/
   fun setClientWork(key: String, reason: String?) {
      if (reason == null) clientWork.remove(key) else clientWork[key] = reason
   }

   var unconfirmedSubmission: Boolean by mutableStateOf(false)
      private set

   /*******************************************************************************
    ** Native navigation never interrupts a submitted mutation or pending native work.
    *******************************************************************************/
   fun navigationBlockReason(): String? = when {
      clientWorkBlockReason != null -> clientWorkBlockReason
      waitingOnBackend && processUUID != null && initLoadState !is LoadState.Loading -> "Wait for the running request to finish before leaving."
      else -> null
   }

   /*******************************************************************************
    ** Leaving is an explicit choice; a missing receipt is not proof that the scan was rejected.
    *******************************************************************************/
   fun navigationWarning(): String? = if (pendingStepValues != null)
      "The last scan is not confirmed and may already be saved. Stay to retry the same scan, or leave to stop retrying. Leaving does not undo a saved scan."
   else null

   /*******************************************************************************
    ** Use guarded navigation when attached to the host; standalone process owners still cancel explicitly.
    *******************************************************************************/
   fun requestClose() {
      if (::qViewModel.isInitialized) qViewModel.qNavigator.popStack() else cancel()
   }

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////
   // function that this view-model can call, if user chose, within the process, to leave the process.         //
   // so process can do its cleanup work, and then let the navigation event occur to go to a different screen. //
   //////////////////////////////////////////////////////////////////////////////////////////////////////////////
   var closeProcessCallback: (() -> Unit)? = null

   ///////////////////////////////////////////////////////////////////////
   // load-state wrapper for the fetching of the full process meta data //
   ///////////////////////////////////////////////////////////////////////
   var processMetaDataLoadState: LoadState<QProcessMetaData> by mutableStateOf(LoadState.Loading(Unit))
      private set

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
   // optional full meta-data for the process (yes, it is also in the load-state, but this is more straightforward //
   // for code that can safely assume the process has been init'ed, and just needs the meta-data)                  //
   //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
   var processMetaData: QProcessMetaData? = null
      private set

   ////////////////////////////////////////////////////////////////////////////////
   // load-state for the process's "init" call - e.g., the first backend step(s) //
   ////////////////////////////////////////////////////////////////////////////////
   var initLoadState: LoadState<ProcessStepResult> by mutableStateOf(LoadState.Loading(Unit))
      private set

   /////////////////////////////////////////////////////////////////////////////////////////////
   // a high-level "the process crashed" load-state                                           //
   // will never be "Loading", and initializes as Success - so just upon-error becomes Error. //
   /////////////////////////////////////////////////////////////////////////////////////////////
   var topLevelErrorState: LoadState<Unit> by mutableStateOf(LoadState.Success(Unit))
      private set

   /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
   // UUID for the process - defined upon the first response from the init step (whether complete or job-started) //
   /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
   var processUUID: String? = null
      private set

   ////////////////////////////////////////////////////////////////////////////////////
   // true when, we're waiting on the backend - so e.g., form elements are disabled. //
   ////////////////////////////////////////////////////////////////////////////////////
   var waitingOnBackend: Boolean by mutableStateOf(true)
      private set

   /////////////////////////////////////////////////////////////////////////////
   // when waiting on a backend step that's gone async, this will be its UUID //
   /////////////////////////////////////////////////////////////////////////////
   private var activeJobUUID: String by mutableStateOf("")

   ////////////////////////////////////////////////////////////////////
   // message and counts from the backend when an async job is going //
   ////////////////////////////////////////////////////////////////////
   var jobRunningMessage: String by mutableStateOf("")
   var currentOfTotalMessage: String by mutableStateOf("")

   ////////////////////////////////////////////////
   // timestamp of last check-in on an async job //
   ////////////////////////////////////////////////
   var jobRunningLastUpdated: LocalDateTime by mutableStateOf(LocalDateTime.now())
      private set

   //////////////////////////////////////////////////////////////////////////////
   // result of the last backend call - be that complete/started/running/error //
   //////////////////////////////////////////////////////////////////////////////
   var lastStepResult: ProcessStepResult by mutableStateOf(QJobComplete("", nextStep = ""))
      private set

   /////////////////////////////////////////////////////////////////////////////////////////////
   // list of the process's frontend steps (which may get mutated based on backend responses) //
   /////////////////////////////////////////////////////////////////////////////////////////////
   private var frontendSteps: List<QFrontendStepMetaData> = emptyList()

   ///////////////////////////////////////////
   // which frontend step is actively shown //
   ///////////////////////////////////////////
   var activeStep: QFrontendStepMetaData? by mutableStateOf(null)
      private set

   /////////////////////////////////////////////////////////////////////////////////
   // name of the active step (e.g., the frontend step that we are to be showing) //
   // (only publicly settable for previews/tests...)                              //
   /////////////////////////////////////////////////////////////////////////////////
   var activeStepName: String by mutableStateOf("")

   ///////////////////////////////////////////////////////////////////////////////////
   // index of the active step (useful for looking at "last" and/or "next to last") //
   ///////////////////////////////////////////////////////////////////////////////////
   private var activeStepIndex: Int by mutableIntStateOf(0)

   ///////////////////////////////////////////////////////////////////////////////////////////
   // counter that will increment when user moves to a next-step -- even if that step is    //
   // the same name or index -- but serves as a signal to views that they need to recompose //
   ///////////////////////////////////////////////////////////////////////////////////////////
   var stepInstanceCounter: Int by mutableIntStateOf(0)
      private set

   /////////////////////////////////////////////////////////////////////////////////////////
   // map of callback functions, that controls can register into, so that they can get a  //
   // callback when something changes.  Originally built for modal-composite blocks, so   //
   // they can be told when their visibility should change, e.g., in response to a button //
   /////////////////////////////////////////////////////////////////////////////////////////
   private var controlCallbacks: MutableMap<String, (() -> Unit)> = mutableMapOf()

   ///////////////////////////////////////////////////////////////////////////////////////
   // a callback that runs before a submit to the backend (e.g., for a next-step) fires //
   ///////////////////////////////////////////////////////////////////////////////////////
   var onSubmitCallback: (() -> Unit)? = null

   ///////////////////////////////////////////////////////
   // the map of values for fields within this process. //
   ///////////////////////////////////////////////////////
   var processValues: MutableMap<String, Any?> = mutableMapOf()

   ////////////////////////////////////////////////////////////////////////////////////////////////
   // set of names of fields which are "form fields" - e.g., whose entry in processValues should //
   // automatically be submitted when moving to next-step.  Views, if they show fields, should   //
   // register those field's names in this map via call to: registerFormFieldName(name)          //
   ////////////////////////////////////////////////////////////////////////////////////////////////
   var formFieldNames: MutableSet<String> = mutableSetOf()

   private var lastInvokedControlCallbackStepInstanceValues: MutableMap<String, Any> = mutableMapOf()

   ////////////////////////////////////////////////////////////////////////////
   // Note:  if you add new fields - please, add them to `fun reset()` below //
   ////////////////////////////////////////////////////////////////////////////


   /*******************************************************************************
    *******************************************************************************/
   fun doInit() {
      viewModelScope.launch()
      {
         loadProcessMetaData().join()
         if (processMetaData != null) {
            initProcess()
         }
      }
   }

   /*******************************************************************************
    *******************************************************************************/
   fun reset() {
      viewModelScope.coroutineContext.cancelChildren()
      remoteOptionJobs.clear()
      remoteOptionGenerations.clear()
      fieldResets.clear()
      remoteOptions.clear()
      remoteOptionErrors.clear()
      initializing = false
      unconfirmedSubmission = false
      clientWork.clear()
      pendingStepValues = null
      pendingAcknowledgment = null
      fieldErrors = emptyMap()
      cancelling = false
      cancellationFailed = false
      submissionError = null
      processComplete = false
      processMetaDataLoadState = LoadState.Loading(Unit)
      processMetaData = null
      initLoadState = LoadState.Loading(Unit)
      topLevelErrorState = LoadState.Success(Unit)
      processUUID = null
      waitingOnBackend = true
      activeJobUUID = ""
      jobRunningMessage = ""
      currentOfTotalMessage = ""
      jobRunningLastUpdated = LocalDateTime.now()
      lastStepResult = QJobComplete("", nextStep = "")
      frontendSteps = emptyList()
      activeStep = null
      activeStepName = ""
      activeStepIndex = 0
      stepInstanceCounter = 0
      controlCallbacks = mutableMapOf()
      onSubmitCallback = null
      processValues = mutableMapOf()
      formFieldNames = mutableSetOf()
      lastInvokedControlCallbackStepInstanceValues = mutableMapOf()
   }

   /*******************************************************************************
    *******************************************************************************/
   private fun loadProcessMetaData(): Job {
      val processViewModel = this

      return viewModelScope.launch()
      {
         processMetaDataLoadState = try {
            val processMetaData = qqqRepository.getProcessMetaData(processName)
            frontendSteps = processMetaData.frontendSteps ?: emptyList()

            processViewModel.processMetaData = processMetaData

            LoadState.Success(processMetaData)
         } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
         } catch (e: Exception) {
            LoadState.Error(e, semanticError(e, "Error loading process metadata."))
         }
      }
   }

   /*******************************************************************************
    *******************************************************************************/
   private fun initProcess(): Job {
      return viewModelScope.launch()
      {
         try {
            initializing = true
            waitingOnBackend = true
            initLoadState = LoadState.Loading(Unit)

            val value = qqqRepository.processInit(processName, emptyMap())
            if (cancelling) {
               // Keep the response long enough to cancel the server process whose UUID was not yet known.
               processUUID = value.processUUID.takeIf { it.isNotBlank() }
               if (value !is QJobComplete || !SemanticProcess.terminal(value)) {
                  val uuid = processUUID ?: error("Initialization did not return a process identity")
                  qqqRepository.processCancel(processName, uuid)
               }
               closeProcessCallback?.invoke()
            } else evaluateProcessResult(value)
         } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
         } catch (e: Exception) {
            waitingOnBackend = false
            cancellationFailed = cancelling
            initLoadState = LoadState.Error(
               e, if (cancelling)
                  "The server did not confirm cancellation. The process may still be running. Retry cancellation if available, or contact your administrator."
               else semanticError(e, "Error launching process.")
            )
         } finally {
            initializing = false
            cancelling = false
         }
      }
   }

   /*******************************************************************************
    *******************************************************************************/
   fun setTopLevelError(message: String) {
      topLevelErrorState = LoadState.Error(message)
   }

   /*******************************************************************************
    *******************************************************************************/
   fun runStep(eventValues: Map<String, Any?> = emptyMap(), acknowledgment: ProcessAcknowledgment? = null) {
      if (waitingOnBackend || cancelling || getNoMoreSteps()) return
      if (clientWorkBlockReason != null) return
      if (unconfirmedSubmission && pendingStepValues == null) return
      unsupportedReason()?.let { setTopLevelError(it); return }
      submissionError = null
      val localProcessUUID = processUUID
      val localActiveStepName = activeStepName

      if (localProcessUUID == null) {
         setTopLevelError("Missing process UUID.  Unable to continue running process.")
         return
      }

      if (localActiveStepName == "") {
         setTopLevelError("Missing current step name.  Unable to continue running process.")
         return
      }

      val valuesToSubmit: MutableMap<String, Any?> = mutableMapOf()

      val declaredFields = activeStep?.components.orEmpty()
         .filter { it.type == com.kingsrook.qqq.frontend.android.core.model.metadata.QComponentType.EDIT_FORM }
         .flatMap { SemanticProcess.fields(activeStep, it) } + SemanticProcess.widgetInputFields(activeStep, processValues)
      val editableFields = declaredFields.filter { it.isEditable && !it.isHidden }.distinctBy { it.name }
      val protectedFields = declaredFields.filter { !it.isEditable || it.isHidden }.map { it.name }.toSet()
      fieldErrors = editableFields.mapNotNull { field ->
         validateSemanticField(field, if (eventValues.containsKey(field.name)) eventValues[field.name] else semanticInitialValue(field, processValues))?.let { field.name to it }
      }.toMap()
      if (fieldErrors.isNotEmpty()) return
      editableFields.forEach { formFieldNames.add(it.name) }
      for (name in formFieldNames) {
         if (name in protectedFields) continue
         val field = editableFields.firstOrNull { it.name == name }
         valuesToSubmit[name] = if (field == null) processValues[name] else semanticFieldValue(field, semanticInitialValue(field, processValues))
      }

      for (entry in eventValues.entries) {
         if (entry.key in protectedFields) continue
         val field = editableFields.firstOrNull { it.name == entry.key }
         valuesToSubmit[entry.key] = if (field == null) entry.value else semanticFieldValue(field, entry.value)
      }

      pendingAcknowledgment = acknowledgment
      pendingStepValues = if (acknowledgment != null) valuesToSubmit.toMap() else null
      topLevelErrorState = LoadState.Success(Unit)

      /////////////////////////////////////////
      // set state to show we're loading now //
      /////////////////////////////////////////
      waitingOnBackend = true
      jobRunningLastUpdated = LocalDateTime.now()
      jobRunningMessage = "Working..."

      viewModelScope.launch()
      {
         try {
            onSubmitCallback?.invoke()

            val value = qqqRepository.processStep(processName, localProcessUUID, localActiveStepName, valuesToSubmit, stepTimeoutMillis = 1000)
            evaluateProcessResult(value)
         } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
         } catch (e: Exception) {
            waitingOnBackend = false
            if (e is com.kingsrook.qqq.frontend.android.core.controllers.UiApiException) {
               fieldErrors = e.fieldErrors.associate { it.field to it.message }
               if (e.fieldErrors.isNotEmpty()) submissionError = e.message
               else setTopLevelError(e.message ?: "The server rejected this request.")
            } else {
               unconfirmedSubmission = true
               setTopLevelError(
                  if (e is retrofit2.HttpException && e.code() in listOf(401, 403))
                     "Access denied. Your session may have expired or device approval may have been revoked. Sign in again or contact your administrator."
                  else if (pendingStepValues != null) "The server did not confirm this request. Retry the same request when connected."
                  else "The server did not confirm this request. Do not submit it again. Process reference: $localProcessUUID. Contact your administrator to check its result."
               )
            }
         }
      }
   }

   /*******************************************************************************
    ** Retain only the current request in memory so an uncertain response can be retried safely.
    *******************************************************************************/
   var pendingStepValues: Map<String, Any?>? by mutableStateOf(null)
      private set
   private var pendingAcknowledgment: ProcessAcknowledgment? = null

   /*******************************************************************************
    ** Explicit retries preserve the scan event ID; no background queue is created.
    *******************************************************************************/
   fun retryPendingStep() {
      if (waitingOnBackend || cancelling) return
      if (activeJobUUID.isNotEmpty()) {
         topLevelErrorState = LoadState.Success(Unit)
         waitingOnBackend = true
         checkIn()
      } else pendingStepValues?.let { runStep(it, pendingAcknowledgment) }
   }

   /*******************************************************************************
    ** Polling a known job is safe even when its original action was not an idempotent scanner event.
    *******************************************************************************/
   fun retryLabel(): String? = when {
      activeJobUUID.isNotEmpty() -> "Check result again"
      pendingStepValues != null -> "Retry same request"
      else -> null
   }

   /*******************************************************************************
    *******************************************************************************/
   private fun evaluateProcessResult(value: ProcessStepResult) {
      lastStepResult = value

      when (value) {
         is QJobComplete -> {
            waitingOnBackend = false
            processJobCompleteResult(value)

            ////////////////////////////////////////////////////////////////////////////////////////////////
            // if the initLoadState was loading, then it means we've finally completed the initial load - //
            // so now we can set a few things to indicate we've progressed past that.                     //
            ////////////////////////////////////////////////////////////////////////////////////////////////
            if (initLoadState is LoadState.Loading) {
               processUUID = value.processUUID
               initLoadState = LoadState.Success(value)
            }
         }

         is QJobError -> {
            waitingOnBackend = false
            activeJobUUID = ""
            setTopLevelError(value.userFacingError ?: value.error)
         }

         is QJobStarted -> {
            waitingOnBackend = true
            jobRunningLastUpdated = LocalDateTime.now()
            activeJobUUID = value.jobUUID
            processUUID = value.processUUID
         }

         is QJobRunning -> {
            waitingOnBackend = true
            jobRunningMessage = value.message
            jobRunningLastUpdated = LocalDateTime.now()
            if (jobRunningMessage == "") {
               jobRunningMessage = "Working..."
            }

            val localCurrent = value.current
            val localTotal = value.total
            currentOfTotalMessage = if (localCurrent != null && localTotal != null) {
               "${localCurrent} of ${localTotal}"
            } else {
               ""
            }
         }

         else -> {
            setTopLevelError("Unexpected response state on a process step call (of type: ${value.javaClass.simpleName})")
         }
      }
   }

   /*******************************************************************************
    *******************************************************************************/
   private fun processJobCompleteResult(value: QJobComplete) {
      unconfirmedSubmission = false
      if (pendingAcknowledgment?.matches(value.values) != false) {
         pendingStepValues = null
         pendingAcknowledgment = null
      } else {
         setTopLevelError("The server did not acknowledge this scan. Retry the same request when connected.")
         return
      }
      frontendSteps = SemanticProcess.adjust(frontendSteps, value.processMetaDataAdjustment)
      processValues = value.values.toMutableMap()
      processComplete = SemanticProcess.terminal(value)
      fieldErrors = emptyMap()
      submissionError = null
      updateActiveStepName(value.nextStep)
      stepInstanceCounter++
      activeJobUUID = ""
      formFieldNames = mutableSetOf()

      ///////////////////////////////////////////////////////////////////////////
      // at some point, we might want to support the ability for a process     //
      // to make a value appear in the topBarStatusText area... but not today! //
      // whenever we do that, it might not belong here anyway                  //
      ///////////////////////////////////////////////////////////////////////////
      // qViewModel.topBarStatusText = "${System.currentTimeMillis() % 100}"

   }

   /*******************************************************************************
    *******************************************************************************/
   private fun updateActiveStepName(newActiveStepName: String) {
      activeStepName = newActiveStepName
      activeStep = null

      for ((index, qFrontendStepMetaData) in frontendSteps.withIndex()) {
         if (qFrontendStepMetaData.name == activeStepName) {
            activeStepIndex = index
            activeStep = qFrontendStepMetaData
            return
         }
      }

      if (!getNoMoreSteps()) setTopLevelError("The server returned an unknown process screen. Update the client or contact your administrator.")
   }

   /*******************************************************************************
    *******************************************************************************/
   fun checkIn() {
      val localProcessUUID = processUUID
      val localJobUUID = activeJobUUID

      if (localProcessUUID == null) {
         setTopLevelError("Missing process UUID.  Unable to continue running process.")
         return
      }

      if (localJobUUID.isEmpty() || cancelling) {
         setTopLevelError("Missing job UUID.  Unable to continue running process.")
         return
      }

      viewModelScope.launch()
      {
         try {
            val value = qqqRepository.processJobStatus(processName, localProcessUUID, localJobUUID)
            evaluateProcessResult(value)
         } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
         } catch (e: Exception) {
            waitingOnBackend = false
            setTopLevelError(semanticError(e, "The server did not confirm the running request. Retry to check the same request again."))
         }
      }
   }

   /*******************************************************************************
    *******************************************************************************/
   fun cancel() {
      if (cancelling) return
      cancellationFailed = false
      val uuid = processUUID
      if (uuid == null && initializing) {
         // Cancelling transport here could discard the only response containing the server UUID.
         cancelling = true
         return
      }
      if (uuid == null || getNoMoreSteps()) {
         viewModelScope.coroutineContext.cancelChildren()
         closeProcessCallback?.invoke()
         return
      }
      cancelling = true
      viewModelScope.coroutineContext.cancelChildren()
      viewModelScope.launch {
         try {
            qqqRepository.processCancel(processName, uuid)
            pendingStepValues = null
            pendingAcknowledgment = null
            closeProcessCallback?.invoke()
         } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
         } catch (e: Exception) {
            cancellationFailed = true
            waitingOnBackend = false
            setTopLevelError(if (e is com.kingsrook.qqq.frontend.android.core.controllers.UiApiException) e.message ?: "Cancellation failed." else "The server did not confirm cancellation. Try again when connected.")
         } finally {
            cancelling = false
         }
      }
   }

   /*******************************************************************************
    *******************************************************************************/
   fun getNextButtonLabel(): String = "Next"

   /*******************************************************************************
    ** Expose the server-derived completion decision instead of inferring it from the last displayed step.
    *******************************************************************************/
   fun getNoMoreSteps(): Boolean = processComplete

   /*******************************************************************************
    ** Validate the current server screen against native controls before allowing submission.
    *******************************************************************************/
   fun unsupportedReason(): String? = SemanticProcess.unsupported(
      activeStep, processValues,
      if (::qInstance.isInitialized) qInstance else null, supportedCustomWidgetTypes
   )

   /*******************************************************************************
    ** Expose semantic server messages only through their explicit exception type and otherwise use a safe fallback.
    *******************************************************************************/
   private fun semanticError(error: Exception, fallback: String): String =
      if (error is com.kingsrook.qqq.frontend.android.core.controllers.UiApiException) error.message ?: fallback else fallback

   /*******************************************************************************
    ** Track editable semantic fields so the submission boundary validates the values rendered by this step.
    *******************************************************************************/
   fun registerFormFieldName(name: String) {
      formFieldNames.add(name)
   }

   /*******************************************************************************
    *******************************************************************************/
   fun actionCallback(eventValues: Map<String, Any>) {
      val registerControlCallbackName = eventValues["registerControlCallbackName"]
      val registerControlCallbackFunction = eventValues["registerControlCallbackFunction"]
      if (registerControlCallbackName is String && registerControlCallbackFunction != null) {
         require(registerControlCallbackFunction is Function0<*>) { "Control callback must take no arguments" }
         controlCallbacks[registerControlCallbackName] = { registerControlCallbackFunction.invoke(); Unit }
         return
      }

      var valuesForRunStep: Map<String, Any?> = eventValues
      var doRunStep = true
      var controlCode: String? = null
      var controlCallbackName: String? = null

      val widgetBlock = eventValues["widgetBlock"]
      if (widgetBlock is WidgetBlock) {
         if (widgetBlock.blockType == "BUTTON" && widgetBlock.values["actionCode"] != null) {
            valuesForRunStep = mapOf("actionCode" to widgetBlock.values["actionCode"])
         } else if (widgetBlock.blockType == "BUTTON" && widgetBlock.values["controlCode"] != null) {
            controlCode = widgetBlock.values["controlCode"].toString()
         }
      }

      if (eventValues["controlCode"] != null) {
         controlCode = eventValues["controlCode"].toString()
      }

      if (controlCode != null) {
         doRunStep = false

         val split = controlCode.split(Regex(":"), 2)
         if (split.size == 2) {
            if (split[0] == "showModal") {
               processValues[split[1]] = true
               controlCallbackName = split[1]
            } else if (split[0] == "hideModal") {
               processValues[split[1]] = false
               controlCallbackName = split[1]
            } else if (split[0] == "toggleModal") {
               val currentValue = processValues[split[1]]
               if (currentValue is Boolean) {
                  processValues[split[1]] = !currentValue
               } else {
                  processValues[split[1]] = true
               }
               controlCallbackName = split[1]
            } else if (split[0] == "invokeControlCallbackOnlyOnce") {
               controlCallbackName = split[1]
               if (lastInvokedControlCallbackStepInstanceValues[controlCallbackName] == stepInstanceCounter) {
                  ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
                  // if we've already invoked the named control callback on this step, then un-set the callback name, so we won't invoke it again. //
                  // was: if we've already played audio on this step, then un-set the callback name, so we won't play it again.                    //
                  ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
                  controlCallbackName = null
               } else {
                  ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
                  // else, if we haven't invoked the named control callback on this step, then set flag to indicate that we're calling it now. //
                  // was: else, if we haven't played audio on this step, then set flag to indicate that we're playing it now.                  //
                  ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
                  lastInvokedControlCallbackStepInstanceValues[controlCallbackName] = stepInstanceCounter
               }
            } else {
               Log.w(TAG, "Unexpected part[0] (before colon) in controlCode: [${controlCode}]")
            }
         } else {
            Log.w(TAG, "Expected controlCode to have 2 colon-delimited parts, but was: [${controlCode}]")
         }
      }

      if (controlCallbackName != null) {
         if (controlCallbacks[controlCallbackName] != null) {
            try {
               controlCallbacks[controlCallbackName]?.invoke()
            } catch (e: kotlinx.coroutines.CancellationException) {
               throw e
            } catch (e: Exception) {
               Log.w(TAG, "Error calling control callback for [${controlCallbackName}]", e)
            }
         }
      }

      if (doRunStep) {
         runStep(valuesForRunStep)
      }
   }

   /*******************************************************************************
    *******************************************************************************/
   companion object {
      val factory: ViewModelProvider.Factory = viewModelFactory()
      {
         initializer()
         {
            ProcessViewModel()
         }
      }
   }
}
