/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.mobileapp.viewmodel

import com.kingsrook.qqq.frontend.android.core.controllers.QQQRepository
import com.kingsrook.qqq.frontend.android.core.model.Environment
import com.kingsrook.qqq.frontend.android.core.model.metadata.*
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.*
import com.kingsrook.qqq.frontend.android.core.model.processes.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProcessSubmissionTest
{
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
      private val input = QFrontendStepMetaData("input", "Input", components = listOf(QFrontendComponent(QComponentType.EDIT_FORM)),
         formFields = listOf(QFieldMetaData("eventId", "Event", type = QFieldType.STRING, isRequired = true, isHidden = hiddenEventId)))
      private val result = QFrontendStepMetaData("result", "Result", components = emptyList())
      override suspend fun getProcessMetaData(processName: String) = QProcessMetaData("edit", "Edit", frontendSteps = listOf(input, result))
      override suspend fun processInit(processName: String, values: Map<String, Any?>, recordsParam: String?, recordIds: String?, filterJSON: String?, stepTimeoutMillis: Int?) = QJobComplete("process", nextStep = "input")
      override suspend fun processStep(processName: String, processUUID: String, stepName: String, values: Map<String, Any?>, stepTimeoutMillis: Int?): ProcessStepResult
      {
         submissions.add(values.toMap())
         return QJobComplete("process", responseValues, nextStep)
      }
      override suspend fun processCancel(processName: String, processUUID: String) { cancellations++ }
      override suspend fun processJobStatus(processName: String, processUUID: String, jobUUID: String): ProcessStepResult = error("Not used")
      override suspend fun getAuthenticationMetaData(): BaseAuthenticationMetaData = error("Not used")
      override suspend fun manageSession(body: ManageSessionRequest): ManageSessionResponse = error("Not used")
      override suspend fun getMetaData(frontendName: String, frontendVersion: String, applicationName: String, applicationVersion: String): QInstance = error("Not used")
      override fun setEnvironment(environment: Environment) { }
      override fun setSessionUUID(sessionUUID: String) { }
      override fun getURI(path: String): String = error("Not used")
   }
}
