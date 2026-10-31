/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.mobileapp.viewmodel

import com.kingsrook.qqq.frontend.android.core.controllers.QQQRepository
import com.kingsrook.qqq.frontend.android.core.model.Environment
import com.kingsrook.qqq.frontend.android.core.model.metadata.*
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.*
import com.kingsrook.qqq.frontend.android.core.model.processes.*
import com.kingsrook.qqq.frontend.android.mobileapp.data.MockPreferencesDataStore
import com.kingsrook.qqq.frontend.android.mobileapp.container.QAppContainer
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProcessSubmissionTest
{
   @Test fun navigationWaitsForMutationAndNeverReplaysUnknownOrdinaryResult() = runTest()
   {
      Dispatchers.setMain(StandardTestDispatcher(testScheduler))
      try
      {
         val gate = CompletableDeferred<Unit>()
         val repository = SubmissionRepository().apply { stepGate = gate; stepFailures = 1 }
         val model = model(repository)
         model.doInit()
         advanceUntilIdle()
         model.runStep(mapOf("eventId" to "ordinary"))
         runCurrent()
         assertNotNull(model.navigationBlockReason())
         gate.complete(Unit)
         advanceUntilIdle()
         assertTrue(model.unconfirmedSubmission)
         assertEquals("process", model.processUUID)
         assertNull(model.retryLabel())
         model.retryPendingStep()
         model.runStep(mapOf("eventId" to "ordinary"))
         advanceUntilIdle()
         assertEquals(1, repository.submissions.size)
         assertTrue((model.topLevelErrorState as LoadState.Error).message.contains("Do not submit it again"))
      }
      finally { Dispatchers.resetMain() }
   }

   @Test fun settledMissingScanReceiptAllowsConfirmedLeaveWithoutDiscardingRetry() = runTest()
   {
      Dispatchers.setMain(StandardTestDispatcher(testScheduler))
      try
      {
         val repository = SubmissionRepository()
         val model = model(repository)
         model.doInit()
         advanceUntilIdle()
         assertNull(model.navigationBlockReason())
         assertNull(model.navigationWarning())
         model.runStep(mapOf("eventId" to "scan"), ProcessAcknowledgment("acknowledgedEventId", "scan"))
         advanceUntilIdle()
         assertNull(model.navigationBlockReason())
         assertEquals("scan", model.pendingStepValues?.get("eventId"))
         assertTrue(model.navigationWarning()!!.contains("may already be saved"))
         repository.responseValues = mapOf("acknowledgedEventId" to "scan")
         model.retryPendingStep()
         advanceUntilIdle()
         assertNull(model.navigationBlockReason())
         assertNull(model.pendingStepValues)
         assertNull(model.navigationWarning())
      }
      finally { Dispatchers.resetMain() }
   }

   @Test fun lostScanResponseAllowsLeaveOnlyAfterRequestSettlesAndPreservesPendingPhotos() = runTest()
   {
      Dispatchers.setMain(StandardTestDispatcher(testScheduler))
      try
      {
         val gate = CompletableDeferred<Unit>()
         val repository = SubmissionRepository().apply { stepGate = gate; stepFailures = 1 }
         val model = model(repository)
         model.doInit()
         advanceUntilIdle()
         model.runStep(mapOf("eventId" to "scan"), ProcessAcknowledgment("acknowledgedEventId", "scan"))
         runCurrent()
         assertNotNull(model.navigationBlockReason())
         gate.complete(Unit)
         advanceUntilIdle()
         assertNull(model.navigationBlockReason())
         assertNotNull(model.navigationWarning())
         assertEquals("scan", model.pendingStepValues?.get("eventId"))
         model.setClientWork("photo", "Finish the pending photo first.")
         assertEquals("Finish the pending photo first.", model.navigationBlockReason())
         model.setClientWork("photo", null)
         assertNull(model.navigationBlockReason())
         assertEquals(1, repository.submissions.size)
      }
      finally { Dispatchers.resetMain() }
   }

   @Test fun confirmedLeaveKeepsOriginalScanUntilCancellationSucceedsAndNeverResubmits() = runTest()
   {
      Dispatchers.setMain(StandardTestDispatcher(testScheduler))
      try
      {
         val gate = CompletableDeferred<Unit>()
         val repository = SubmissionRepository().apply { cancelGate = gate; cancelFailures = 1 }
         val model = model(repository)
         var closes = 0
         model.closeProcessCallback = { closes++ }
         model.doInit()
         advanceUntilIdle()
         model.runStep(mapOf("eventId" to "scan"), ProcessAcknowledgment("acknowledgedEventId", "scan"))
         advanceUntilIdle()
         model.cancel()
         runCurrent()
         model.cancel()
         assertEquals(1, repository.cancellations)
         assertEquals("scan", model.pendingStepValues?.get("eventId"))
         gate.complete(Unit)
         advanceUntilIdle()
         assertEquals(0, closes)
         assertTrue(model.cancellationFailed)
         assertEquals("scan", model.pendingStepValues?.get("eventId"))
         assertNotNull(model.navigationWarning())
         model.cancel()
         assertFalse(model.cancellationFailed)
         advanceUntilIdle()
         assertEquals(1, closes)
         assertEquals(2, repository.cancellations)
         assertEquals(1, repository.submissions.size)
         assertNull(model.pendingStepValues)
         assertNull(model.navigationWarning())
      }
      finally { Dispatchers.resetMain() }
   }

   @Test fun widgetInputsAreValidatedConvertedAndProtectedAtSubmission()
   {
      runTest {
         Dispatchers.setMain(StandardTestDispatcher(testScheduler))
         try
         {
            fun block(name: String, type: String, hidden: Boolean = false) = mapOf("blockType" to "INPUT_FIELD", "values" to mapOf(
               "fieldMetaData" to mapOf("name" to name, "label" to name, "type" to type, "isRequired" to true, "isHidden" to hidden)))
            val repository = SubmissionRepository().apply {
               inputOverride = QFrontendStepMetaData("input", "Widget", components = listOf(QFrontendComponent(QComponentType.WIDGET,
                  mapOf("isAdHocWidget" to true, "blocks" to listOf(block("quantity", "INTEGER"), block("secret", "STRING", true))))))
            }
            val model = model(repository)
            model.doInit()
            advanceUntilIdle()
            model.runStep(mapOf("quantity" to "invalid"))
            advanceUntilIdle()
            assertTrue(repository.submissions.isEmpty())
            assertTrue(model.fieldErrors.containsKey("quantity"))
            model.runStep(mapOf("quantity" to "12", "secret" to "must-not-submit"))
            advanceUntilIdle()
            assertEquals(mapOf("quantity" to 12), repository.submissions.single())
         }
         finally { Dispatchers.resetMain() }
      }
   }

   @Test fun ordinaryAsyncJobCanRetryItsStatusWithoutResubmittingTheAction() = runTest()
   {
      Dispatchers.setMain(StandardTestDispatcher(testScheduler))
      try
      {
         val repository = SubmissionRepository().apply { asynchronous = true; statusFailures = 1 }
         val model = model(repository)
         model.doInit()
         advanceUntilIdle()
         model.runStep(mapOf("eventId" to "ordinary"))
         advanceUntilIdle()
         model.checkIn()
         advanceUntilIdle()
         assertEquals("Check result again", model.retryLabel())
         assertTrue(model.topLevelErrorState is LoadState.Error)
         model.retryPendingStep()
         advanceUntilIdle()
         assertEquals(1, repository.submissions.size)
         assertEquals(2, repository.statusCalls)
         assertEquals("result", model.activeStepName)
         assertNull(model.retryLabel())
      }
      finally { Dispatchers.resetMain() }
   }

   @Test fun cancellingBeforeMetadataReturnsPreventsInitialization() = runTest()
   {
      Dispatchers.setMain(StandardTestDispatcher(testScheduler))
      try
      {
         val gate = CompletableDeferred<Unit>()
         val repository = SubmissionRepository().apply { metadataGate = gate }
         val model = model(repository)
         var closes = 0
         model.closeProcessCallback = { closes++ }
         model.doInit()
         runCurrent()
         model.cancel()
         gate.complete(Unit)
         advanceUntilIdle()
         assertEquals(1, closes)
         assertEquals(0, repository.initializations)
         assertEquals(0, repository.cancellations)
      }
      finally { Dispatchers.resetMain() }
   }

   @Test fun cancellingDuringInitializationWaitsForItsIdentityAndCancelsTheServer() = runTest()
   {
      Dispatchers.setMain(StandardTestDispatcher(testScheduler))
      try
      {
         val gate = CompletableDeferred<Unit>()
         val repository = SubmissionRepository().apply { initGate = gate }
         val model = model(repository)
         var closes = 0
         model.closeProcessCallback = { closes++ }
         model.doInit()
         runCurrent()
         assertEquals(1, repository.initializations)
         model.cancel()
         assertTrue(model.cancelling)
         assertEquals(0, closes)
         gate.complete(Unit)
         advanceUntilIdle()
         assertEquals(1, repository.cancellations)
         assertEquals(1, closes)
         assertFalse(model.cancelling)
      }
      finally { Dispatchers.resetMain() }
   }

   @Test fun completeScannerReceiptIsRequiredBeforeDiscardingTheOriginalEvent() = runTest()
   {
      Dispatchers.setMain(StandardTestDispatcher(testScheduler))
      try
      {
         val repository = SubmissionRepository().apply { responseValues = mapOf("acknowledgedEventId" to "scan-1", "scanSaved" to false) }
         val model = model(repository)
         model.doInit()
         advanceUntilIdle()
         model.runStep(mapOf("eventId" to "scan-1", "barcode" to "00123"),
            ProcessAcknowledgment("acknowledgedEventId", "scan-1", mapOf("scanSaved" to true), setOf("scanId")))
         advanceUntilIdle()
         assertEquals("scan-1", model.pendingStepValues?.get("eventId"))
         assertEquals("input", model.activeStepName)
         repository.responseValues = mapOf("acknowledgedEventId" to "scan-1", "scanSaved" to true, "scanId" to "receipt")
         model.retryPendingStep()
         advanceUntilIdle()
         assertNull(model.pendingStepValues)
         assertEquals(repository.submissions.first(), repository.submissions.last())
      }
      finally { Dispatchers.resetMain() }
   }

   @Test fun omittedNextStepCompletesTheProcessAndCloseDoesNotCancelIt() = runTest()
   {
      Dispatchers.setMain(StandardTestDispatcher(testScheduler))
      try
      {
         val repository = SubmissionRepository()
         val model = model(repository)
         var closed = false
         model.closeProcessCallback = { closed = true }
         model.doInit()
         advanceUntilIdle()
         model.processValues["eventId"] = "ordinary-business-id"
         model.runStep()
         advanceUntilIdle()
         assertEquals("result", model.activeStepName)
         assertFalse("A final display step must still be submitted", model.getNoMoreSteps())
         repository.nextStep = ""
         model.runStep()
         advanceUntilIdle()
         assertTrue("The server omitted nextStep, so there is nothing left to run", model.getNoMoreSteps())
         assertTrue(model.topLevelErrorState is LoadState.Success)
         assertNull(model.activeStep)
         model.cancel()
         advanceUntilIdle()
         assertTrue(closed)
         assertEquals(0, repository.cancellations)
      }
      finally { Dispatchers.resetMain() }
   }

   @Test fun ordinaryEventIdFieldAdvancesWithoutScannerAcknowledgment() = runTest()
   {
      Dispatchers.setMain(StandardTestDispatcher(testScheduler))
      try
      {
         val repository = SubmissionRepository()
         val model = model(repository)
         model.doInit()
         advanceUntilIdle()
         model.processValues["eventId"] = "ordinary-business-id"
         model.runStep()
         advanceUntilIdle()
         assertEquals("ordinary-business-id", repository.submissions.single()["eventId"])
         assertEquals("result", model.activeStepName)
         assertNull(model.pendingStepValues)
         assertTrue(model.topLevelErrorState is LoadState.Success)
      }
      finally { Dispatchers.resetMain() }
   }

   @Test fun explicitScanAcknowledgmentRetainsIdentityUntilMatchingRetry() = runTest()
   {
      Dispatchers.setMain(StandardTestDispatcher(testScheduler))
      try
      {
         val repository = SubmissionRepository()
         val model = model(repository)
         model.doInit()
         advanceUntilIdle()
         val values = mapOf("eventId" to "scan-1", "barcode" to "000123")
         model.runStep(values, ProcessAcknowledgment("acknowledgedEventId", "scan-1"))
         advanceUntilIdle()
         assertEquals("input", model.activeStepName)
         assertEquals("scan-1", model.pendingStepValues?.get("eventId"))
         repository.responseValues = mapOf("acknowledgedEventId" to "scan-1")
         model.retryPendingStep()
         advanceUntilIdle()
         assertEquals(2, repository.submissions.size)
         assertEquals(repository.submissions.first(), repository.submissions.last())
         assertEquals("result", model.activeStepName)
         assertNull(model.pendingStepValues)
         assertTrue(model.topLevelErrorState is LoadState.Success)
      }
      finally { Dispatchers.resetMain() }
   }

   @Test fun hiddenRequiredFieldsAreNeitherValidatedNorResubmitted() = runTest()
   {
      Dispatchers.setMain(StandardTestDispatcher(testScheduler))
      try
      {
         val repository = SubmissionRepository(hiddenEventId = true)
         val model = model(repository)
         model.doInit()
         advanceUntilIdle()
         model.runStep()
         advanceUntilIdle()
         assertTrue(model.fieldErrors.isEmpty())
         assertFalse(repository.submissions.single().containsKey("eventId"))
         assertEquals("result", model.activeStepName)
      }
      finally { Dispatchers.resetMain() }
   }

   @Test fun explicitLocalLeaveRequiresFailedCancellationAndHonorsWorkGuards() = runTest()
   {
      Dispatchers.setMain(StandardTestDispatcher(testScheduler))
      try
      {
         val repository = SubmissionRepository().apply { cancelFailures = 2 }
         val process = model(repository)
         QAppContainer.availableEnvironments = listOf(Environment("Test", "https://example.invalid"))
         val host = QViewModel(repository, MockPreferencesDataStore())
         // This policy fixture owns only navigation; skip Android-backed startup/authentication work.
         host.viewModelScope.coroutineContext.cancelChildren()
         host.activeProcessViewModel = process
         process.doInit()
         advanceUntilIdle()
         process.runStep(mapOf("eventId" to "scan"), ProcessAcknowledgment("acknowledgedEventId", "scan"))
         advanceUntilIdle()
         var destinations = 0
         host.qNavigator.requestLeave("Leave?") { destinations++ }
         host.qNavigator.leaveAnyway()
         assertEquals(0, destinations)
         host.qNavigator.confirmLeave()
         advanceUntilIdle()
         assertTrue(process.cancellationFailed)
         host.qNavigator.dismissLeave()
         assertEquals("scan", process.pendingStepValues?.get("eventId"))
         assertEquals(0, destinations)
         host.qNavigator.requestLeave("Leave?") { destinations++ }
         host.qNavigator.confirmLeave()
         assertFalse(process.cancellationFailed)
         host.qNavigator.leaveAnyway()
         assertEquals(0, destinations)
         advanceUntilIdle()
         process.setClientWork("photo", "Pending photo")
         host.qNavigator.leaveAnyway()
         assertEquals(0, destinations)
         process.setClientWork("photo", null)
         val stepGate = CompletableDeferred<Unit>()
         repository.stepGate = stepGate
         process.retryPendingStep()
         runCurrent()
         host.qNavigator.leaveAnyway()
         assertEquals(0, destinations)
         stepGate.complete(Unit)
         advanceUntilIdle()
         host.qNavigator.leaveAnyway()
         host.qNavigator.leaveAnyway()
         assertEquals(1, destinations)
         assertEquals(2, repository.cancellations)
         assertEquals(2, repository.submissions.size)
         assertEquals(repository.submissions.first(), repository.submissions.last())
         assertNull(process.pendingStepValues)
         assertNull(host.activeProcessViewModel)
         assertFalse(process.cancellationFailed)
      }
      finally { Dispatchers.resetMain() }
   }

   private fun model(repository: SubmissionRepository) = ProcessViewModel().apply()
   {
      qqqRepository = repository
      processName = "edit"
      qInstance = QInstance()
   }

   private class SubmissionRepository(hiddenEventId: Boolean = false) : QQQRepository
   {
      val submissions = mutableListOf<Map<String, Any?>>()
      var responseValues: Map<String, Any?> = emptyMap()
      var nextStep = "result"
      var cancellations = 0
      var initializations = 0
      var metadataGate: CompletableDeferred<Unit>? = null
      var initGate: CompletableDeferred<Unit>? = null
      var stepGate: CompletableDeferred<Unit>? = null
      var stepFailures = 0
      var cancelGate: CompletableDeferred<Unit>? = null
      var cancelFailures = 0
      var asynchronous = false
      var statusFailures = 0
      var statusCalls = 0
      var inputOverride: QFrontendStepMetaData? = null
      private val input = QFrontendStepMetaData("input", "Input", components = listOf(QFrontendComponent(QComponentType.EDIT_FORM)),
         formFields = listOf(QFieldMetaData("eventId", "Event", type = QFieldType.STRING, isRequired = true, isHidden = hiddenEventId)))
      private val result = QFrontendStepMetaData("result", "Result", components = emptyList())
      override suspend fun getProcessMetaData(processName: String): QProcessMetaData
      {
         metadataGate?.await()
         return QProcessMetaData("edit", "Edit", frontendSteps = listOf(inputOverride ?: input, result))
      }
      override suspend fun processInit(processName: String, values: Map<String, Any?>, recordsParam: String?, recordIds: String?, filterJSON: String?, stepTimeoutMillis: Int?): ProcessStepResult
      {
         initializations++
         initGate?.await()
         return QJobComplete("process", nextStep = "input")
      }
      override suspend fun processStep(processName: String, processUUID: String, stepName: String, values: Map<String, Any?>, stepTimeoutMillis: Int?): ProcessStepResult
      {
         submissions.add(values.toMap())
         stepGate?.await()
         if(stepFailures-- > 0) throw java.io.IOException("Response lost")
         return if(asynchronous) QJobStarted("process", "job") else QJobComplete("process", responseValues, nextStep)
      }
      override suspend fun processCancel(processName: String, processUUID: String)
      {
         cancellations++
         cancelGate?.await()
         if(cancelFailures-- > 0) throw java.io.IOException("Cancellation response lost")
      }
      override suspend fun processJobStatus(processName: String, processUUID: String, jobUUID: String): ProcessStepResult
      {
         statusCalls++
         if(statusFailures-- > 0) throw java.io.IOException("Unavailable")
         return QJobComplete("process", responseValues, nextStep)
      }
      override suspend fun getAuthenticationMetaData(): BaseAuthenticationMetaData = error("Not used")
      override suspend fun manageSession(body: ManageSessionRequest): ManageSessionResponse = error("Not used")
      override suspend fun getMetaData(frontendName: String, frontendVersion: String, applicationName: String, applicationVersion: String): QInstance = error("Not used")
      override fun setEnvironment(environment: Environment) { }
      override fun setSessionUUID(sessionUUID: String) { }
      override fun getURI(path: String): String = error("Not used")
   }
}
