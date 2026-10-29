/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2004-2024.  Kingsrook, LLC
 * 651 N Broad St Ste 205 # 6917 | Middletown DE 19709 | United States
 * contact@kingsrook.com
 * https://github.com/Kingsrook/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.kingsrook.qqq.frontend.android.mobileapp.viewmodel

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kingsrook.qqq.frontend.android.core.controllers.QQQRepository
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

/***************************************************************************
 **
 ***************************************************************************/
open class ProcessViewModel : ViewModel()
{
   lateinit var qqqRepository: QQQRepository
   lateinit var qInstance: QInstance
   lateinit var processName: String
   lateinit var qViewModel: QViewModel

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////
   // function that this view-model can call, if user chose, within the process, to leave the process.         //
   // so process can do its cleanup work, and then let the navigation event occur to go to a different screen. //
   // we are probably missing the reverse of this - where a navigation event (e.g., back arrow) isn't letting  //
   // this view-model know that it is ending, so, that's a potential todo                                      //
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


   /***************************************************************************
    **
    ***************************************************************************/
   fun doInit()
   {
      viewModelScope.launch()
      {
         loadProcessMetaData().join()
         if(processMetaData != null)
         {
            initProcess()
         }
      }
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun reset()
   {
      viewModelScope.coroutineContext.cancelChildren()
      pendingStepValues = null
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

   /***************************************************************************
    **
    ***************************************************************************/
   private fun loadProcessMetaData(): Job
   {
      val processViewModel = this

      return viewModelScope.launch()
      {
         processMetaDataLoadState = try
         {
            val processMetaData = qqqRepository.getProcessMetaData(processName)
            frontendSteps = processMetaData.frontendSteps ?: emptyList()

            processViewModel.processMetaData = processMetaData

            LoadState.Success(processMetaData)
         }
         catch(e: kotlinx.coroutines.CancellationException) { throw e }
         catch(e: Exception)
         {
            Log.w(TAG, "Error loading ProcessMetaData", e)
            LoadState.Error(e, "Error loading Process MetaData")
         }
      }
   }

   /***************************************************************************
    **
    ***************************************************************************/
   private fun initProcess(): Job
   {
      return viewModelScope.launch()
      {
         try
         {
            waitingOnBackend = true
            initLoadState = LoadState.Loading(Unit)

            val value = qqqRepository.processInit(processName, emptyMap())
            evaluateProcessResult(value)
         }
         catch(e: kotlinx.coroutines.CancellationException) { throw e }
         catch(e: Exception)
         {
            Log.w(TAG, "Error initing process", e)
            initLoadState = LoadState.Error(e, "Error launching process")
         }
      }
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun setTopLevelError(message: String)
   {
      topLevelErrorState = LoadState.Error(message)
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun runStep(eventValues: Map<String, Any?> = emptyMap())
   {
      if(waitingOnBackend) return
      val localProcessUUID = processUUID
      val localActiveStepName = activeStepName

      if(localProcessUUID == null)
      {
         setTopLevelError("Missing process UUID.  Unable to continue running process.")
         return
      }

      if(localActiveStepName == "")
      {
         setTopLevelError("Missing current step name.  Unable to continue running process.")
         return
      }

      val valuesToSubmit: MutableMap<String, Any?> = mutableMapOf()

      // todo all the work with the form and form values, like validation, and formatting?
      for(name in formFieldNames)
      {
         valuesToSubmit[name] = processValues[name]
      }

      for(entry in eventValues.entries)
      {
         valuesToSubmit[entry.key] = entry.value
      }

      pendingStepValues = if(valuesToSubmit["eventId"] is String) valuesToSubmit.toMap() else null
      topLevelErrorState = LoadState.Success(Unit)

      /////////////////////////////////////////
      // set state to show we're loading now //
      /////////////////////////////////////////
      waitingOnBackend = true
      jobRunningLastUpdated = LocalDateTime.now()
      jobRunningMessage = "Working..."

      viewModelScope.launch()
      {
         try
         {
            onSubmitCallback?.invoke()

            val value = qqqRepository.processStep(processName, localProcessUUID, localActiveStepName, valuesToSubmit, stepTimeoutMillis = 1000)
            evaluateProcessResult(value)
         }
         catch(e: kotlinx.coroutines.CancellationException) { throw e }
         catch(e: Exception)
         {
            waitingOnBackend = false
            setTopLevelError(if(e is retrofit2.HttpException && e.code() in listOf(401, 403))
               "Access denied. Your session may have expired or device approval may have been revoked. Sign in again or contact your administrator."
               else "The server did not confirm this request. Retry the same request when connected.")
         }
      }
   }

   /** Retain only the current request in memory so an uncertain response can be retried safely. */
   var pendingStepValues: Map<String, Any?>? = null
      private set

   /** Explicit retries preserve the scan event ID; no background queue is created. */
   fun retryPendingStep()
   {
      if(pendingStepValues == null) return
      if(activeJobUUID.isNotEmpty())
      {
         topLevelErrorState = LoadState.Success(Unit)
         waitingOnBackend = true
         checkIn()
      }
      else pendingStepValues?.let { runStep(it) }
   }

   /***************************************************************************
    **
    ***************************************************************************/
   private fun evaluateProcessResult(value: ProcessStepResult)
   {
      lastStepResult = value

      when (value)
      {
         is QJobComplete ->
         {
            waitingOnBackend = false
            processJobCompleteResult(value)

            ////////////////////////////////////////////////////////////////////////////////////////////////
            // if the initLoadState was loading, then it means we've finally completed the initial load - //
            // so now we can set a few things to indicate we've progressed past that.                     //
            ////////////////////////////////////////////////////////////////////////////////////////////////
            if(initLoadState is LoadState.Loading)
            {
               processUUID = value.processUUID
               initLoadState = LoadState.Success(value)
            }
         }

         is QJobError ->
         {
            waitingOnBackend = false
            activeJobUUID = ""
            setTopLevelError(value.userFacingError ?: value.error)
         }

         is QJobStarted ->
         {
            waitingOnBackend = true
            jobRunningLastUpdated = LocalDateTime.now()
            activeJobUUID = value.jobUUID
            processUUID = value.processUUID
         }

         is QJobRunning ->
         {
            waitingOnBackend = true
            jobRunningMessage = value.message
            jobRunningLastUpdated = LocalDateTime.now()
            if(jobRunningMessage == "")
            {
               jobRunningMessage = "Working..."
            }

            val localCurrent = value.current
            val localTotal = value.total
            currentOfTotalMessage = if(localCurrent != null && localTotal != null)
            {
               "${localCurrent} of ${localTotal}"
            }
            else
            {
               ""
            }
         }

         else ->
         {
            setTopLevelError("Unexpected response state on a process step call (of type: ${value.javaClass.simpleName})")
         }
      }
   }

   /***************************************************************************
    **
    ***************************************************************************/
   private fun processJobCompleteResult(value: QJobComplete)
   {
      val pendingEventId = pendingStepValues?.get("eventId")
      if(pendingEventId == null || pendingEventId == value.values["acknowledgedEventId"])
      {
         pendingStepValues = null
      }
      else
      {
         setTopLevelError("The server did not acknowledge this scan. Retry the same request when connected.")
      }
      updateActiveStepName(value.nextStep)
      stepInstanceCounter++

      processValues = value.values.toMutableMap()
      activeJobUUID = ""
      formFieldNames = mutableSetOf()

      ///////////////////////////////////////////////////////////////////////////
      // at some point, we might want to support the ability for a process     //
      // to make a value appear in the topBarStatusText area... but not today! //
      // whenever we do that, it might not belong here anyway                  //
      ///////////////////////////////////////////////////////////////////////////
      // qViewModel.topBarStatusText = "${System.currentTimeMillis() % 100}"

      value.processMetaDataAdjustment?.let()
      { adjustment ->
         adjustment.updatedFields // todo tododo
      }
   }

   /***************************************************************************
    **
    ***************************************************************************/
   private fun updateActiveStepName(newActiveStepName: String)
   {
      activeStepName = newActiveStepName

      for((index, qFrontendStepMetaData) in frontendSteps.withIndex())
      {
         if(qFrontendStepMetaData.name == activeStepName)
         {
            activeStepIndex = index
            activeStep = qFrontendStepMetaData
            return
         }
      }

      Log.w(TAG, "Got through findActiveStepIndex and didn't find one...")
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun checkIn()
   {
      val localProcessUUID = processUUID
      val localJobUUID = activeJobUUID

      if(localProcessUUID == null)
      {
         setTopLevelError("Missing process UUID.  Unable to continue running process.")
         return
      }

      if(localJobUUID == null)
      {
         setTopLevelError("Missing job UUID.  Unable to continue running process.")
         return
      }

      viewModelScope.launch()
      {
         try
         {
            val value = qqqRepository.processJobStatus(processName, localProcessUUID, localJobUUID)
            evaluateProcessResult(value)
         }
         catch(e: kotlinx.coroutines.CancellationException) { throw e }
         catch(e: Exception)
         {
            waitingOnBackend = false
            setTopLevelError("The server did not confirm the running request. Retry to check the same request again.")
         }
      }
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun cancel()
   {
      // todo (maybe) an 'are you sure' dialog
      // todo - call backend if there's a cancel step on the process
      closeProcessCallback?.invoke()
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun getNextButtonLabel(): String
   {
      when (val local = processMetaDataLoadState)
      {
         is LoadState.Success ->
         {
            if(local.value.stepFlow == "LINEAR" && activeStepIndex == frontendSteps.size - 2)
            {
               return ("Submit")
            }
         }

         else ->
         {
            // noop
         }
      }

      // todo - a special case for VALIDATION_REVIEW_SCREEN, based on the value of the radio, may return Submit

      return ("Next")
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun getNoMoreSteps(): Boolean
   {
      when (val local = processMetaDataLoadState)
      {
         is LoadState.Success ->
         {
            if(local.value.stepFlow == "LINEAR" && activeStepIndex == frontendSteps.size - 1)
            {
               return (true)
            }
         }

         else ->
         {
            // noop
         }
      }

      if(processValues["noMoreSteps"] != null)
      {
         return (true)
      }

      return (false)
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun registerFormFieldName(name: String)
   {
      formFieldNames.add(name)
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun actionCallback(eventValues: Map<String, Any>)
   {
      val registerControlCallbackName = eventValues["registerControlCallbackName"]
      val registerControlCallbackFunction = eventValues["registerControlCallbackFunction"]
      if(registerControlCallbackName is String && registerControlCallbackFunction != null)
      {
         controlCallbacks[registerControlCallbackName] = eventValues["registerControlCallbackFunction"] as () -> Unit
         return
      }

      var valuesForRunStep: Map<String, Any?> = eventValues
      var doRunStep = true
      var controlCode: String? = null
      var controlCallbackName: String? = null

      val widgetBlock = eventValues["widgetBlock"]
      if(widgetBlock is WidgetBlock)
      {
         if(widgetBlock.blockType == "BUTTON" && widgetBlock.values["actionCode"] != null)
         {
            // todo maybe here if (eventValues && eventValues.actionCode && !ProcessWidgetBlockUtils.isActionCodeValid(eventValues.actionCode, activeStep, processValues))
            valuesForRunStep = mapOf("actionCode" to widgetBlock.values["actionCode"])
         }
         else if(widgetBlock.blockType == "BUTTON" && widgetBlock.values["controlCode"] != null)
         {
            controlCode = widgetBlock.values["controlCode"].toString()
         }
      }

      if(eventValues["controlCode"] != null)
      {
         controlCode = eventValues["controlCode"].toString()
      }

      if(controlCode != null)
      {
         doRunStep = false

         val split = controlCode.split(Regex(":"), 2)
         if(split.size == 2)
         {
            if(split[0] == "showModal")
            {
               processValues[split[1]] = true
               controlCallbackName = split[1]
            }
            else if(split[0] == "hideModal")
            {
               processValues[split[1]] = false
               controlCallbackName = split[1]
            }
            else if(split[0] == "toggleModal")
            {
               val currentValue = processValues[split[1]]
               if(currentValue is Boolean)
               {
                  processValues[split[1]] = !currentValue
               }
               else
               {
                  processValues[split[1]] = true
               }
               controlCallbackName = split[1]
            }
            else if(split[0] == "invokeControlCallbackOnlyOnce")
            {
               controlCallbackName = split[1]
               if(lastInvokedControlCallbackStepInstanceValues[controlCallbackName] == stepInstanceCounter)
               {
                  ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
                  // if we've already invoked the named control callback on this step, then un-set the callback name, so we won't invoke it again. //
                  // was: if we've already played audio on this step, then un-set the callback name, so we won't play it again.                    //
                  ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
                  controlCallbackName = null
               }
               else
               {
                  ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
                  // else, if we haven't invoked the named control callback on this step, then set flag to indicate that we're calling it now. //
                  // was: else, if we haven't played audio on this step, then set flag to indicate that we're playing it now.                  //
                  ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
                  lastInvokedControlCallbackStepInstanceValues[controlCallbackName] = stepInstanceCounter
               }
            }
            else
            {
               Log.w(TAG, "Unexpected part[0] (before colon) in controlCode: [${controlCode}]")
            }
         }
         else
         {
            Log.w(TAG, "Expected controlCode to have 2 colon-delimited parts, but was: [${controlCode}]")
         }
      }

      if(controlCallbackName != null)
      {
         if(controlCallbacks[controlCallbackName] != null)
         {
            try
            {
               controlCallbacks[controlCallbackName]?.invoke()
            }
            catch(e: kotlinx.coroutines.CancellationException) { throw e }
            catch(e: Exception)
            {
               Log.w(TAG, "Error calling control callback for [${controlCallbackName}]", e)
            }
         }
      }

      if(doRunStep)
      {
         runStep(valuesForRunStep)
      }
   }

   /***************************************************************************
    **
    ***************************************************************************/
   companion object
   {
      val factory: ViewModelProvider.Factory = viewModelFactory()
      {
         initializer()
         {
            ProcessViewModel()
         }
      }
   }
}
