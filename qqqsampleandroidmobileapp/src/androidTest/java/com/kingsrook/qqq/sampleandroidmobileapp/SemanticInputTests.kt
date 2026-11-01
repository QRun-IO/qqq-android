/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.sampleandroidmobileapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import com.kingsrook.qqq.frontend.android.core.controllers.QQQRepository
import com.kingsrook.qqq.frontend.android.core.model.metadata.QComponentType
import com.kingsrook.qqq.frontend.android.core.model.metadata.QFieldMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.QFieldType
import com.kingsrook.qqq.frontend.android.core.model.metadata.QFrontendComponent
import com.kingsrook.qqq.frontend.android.core.model.metadata.QFrontendStepMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.QInstance
import com.kingsrook.qqq.frontend.android.core.model.metadata.QProcessMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.ManageSessionResponse
import com.kingsrook.qqq.frontend.android.core.model.processes.QJobComplete
import com.kingsrook.qqq.frontend.android.core.model.widgets.WidgetBlock
import com.kingsrook.qqq.frontend.android.mobileapp.data.MockPreferencesDataStore
import com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication.QPasswordLogin
import com.kingsrook.qqq.frontend.android.mobileapp.ui.processes.ProcessFrontendStepView
import com.kingsrook.qqq.frontend.android.mobileapp.ui.widgets.blocks.InputFieldWidgetBlock
import com.kingsrook.qqq.frontend.android.mobileapp.ui.widgets.blocks.WidgetBlockParameters
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.ProcessViewModel
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/*******************************************************************************
 ** Exercise real native controls in a constrained viewport, including the actions below their content.
 *******************************************************************************/
class SemanticInputTests : BaseTest() {
   @get:Rule
   val ui = createComposeRule()

   /*******************************************************************************
    ** A short screen must still let an operator reach Sign in and actually call authentication.
    *******************************************************************************/
   @Test
   fun loginIsReachableInAShortViewport() {
      val loggedIn = AtomicReference<String?>()
      val repository = object : QQQRepository by SampleAppMockQQQRepository() {
         /*******************************************************************************
          **
          *******************************************************************************/
         override suspend fun loginWithPassword(username: String, password: String): ManageSessionResponse {
            loggedIn.set(username)
            return ManageSessionResponse("local-test-session")
         }
      }
      val model = QViewModel(repository, MockPreferencesDataStore())
      ui.setContent()
      {
         MaterialTheme()
         {
            Box(Modifier.width(320.dp).height(180.dp)) { QPasswordLogin(model, Modifier.fillMaxSize()) }
         }
      }
      ui.onNodeWithText("Username").performScrollTo().performTextReplacement("operator")
      ui.onNodeWithText("Password").performScrollTo().performTextReplacement("synthetic-test-input")
      ui.onNodeWithText("Password").performImeAction()
      ui.onNode(hasText("Sign in") and hasClickAction()).performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
      ui.waitUntil(10000) { loggedIn.get() == "operator" }
   }

   /*******************************************************************************
    ** The last field scrolls into view while the standard action remains outside the scrolling content.
    *******************************************************************************/
   @Test
   fun longFormCanEditItsLastFieldAndSubmit() {
      val saved = AtomicReference<Map<String, Any?>?>()
      val repository = object : QQQRepository by SampleAppMockQQQRepository() {
         /*******************************************************************************
          **
          *******************************************************************************/
         override suspend fun getProcessMetaData(processName: String) = QProcessMetaData(
            "long", "Long form", frontendSteps = listOf(
               QFrontendStepMetaData(
                  "input", "Input", components = listOf(QFrontendComponent(QComponentType.EDIT_FORM)),
                  formFields = (1..12).map { QFieldMetaData("field$it", "Field $it", type = QFieldType.STRING) })
            )
         )

         /*******************************************************************************
          **
          *******************************************************************************/
         override suspend fun processInit(processName: String, values: Map<String, Any?>, recordsParam: String?, recordIds: String?, filterJSON: String?, stepTimeoutMillis: Int?) = QJobComplete("p", nextStep = "input")

         /*******************************************************************************
          **
          *******************************************************************************/
         override suspend fun processStep(processName: String, processUUID: String, stepName: String, values: Map<String, Any?>, stepTimeoutMillis: Int?): QJobComplete {
            saved.set(values)
            return QJobComplete("p")
         }
      }
      val model = ProcessViewModel().apply { qqqRepository = repository; processName = "long"; qInstance = QInstance() }
      ui.setContent()
      {
         LaunchedEffect(Unit) { model.doInit() }
         MaterialTheme()
         {
            Box(Modifier.width(320.dp).height(280.dp)) { ProcessFrontendStepView(model, QInstance()) }
         }
      }
      ui.waitUntil(10000) { model.activeStepName == "input" }
      ui.onNodeWithText("Field 12").performScrollTo().assertIsDisplayed().performTextReplacement("last-value")
      ui.onNodeWithText("Field 12").performImeAction()
      ui.waitUntil(5000) { runCatching { ui.onNodeWithText("Next").assertIsDisplayed(); true }.getOrDefault(false) }
      ui.onNodeWithText("Next").assertIsDisplayed().assertIsEnabled().performClick()
      ui.waitUntil(10000) { saved.get() != null }
      assertEquals("last-value", saved.get()!!["field12"])
      ui.onNodeWithText("Close").assertIsDisplayed()
   }

   /*******************************************************************************
    ** Autofocus is presentation metadata: arrow-prefixed data must not become a business action.
    *******************************************************************************/
   @Test
   fun inputWidgetSubmitsArrowPrefixLiterally() {
      val submitted = AtomicReference<Map<String, Any>?>()
      val block = WidgetBlock(
         "input", "INPUT_FIELD", values = mutableMapOf(
            "autoFocus" to true, "submitOnEnter" to true,
            "fieldMetaData" to mapOf("name" to "code", "label" to "Code", "type" to "STRING")
         )
      )
      ui.setContent { MaterialTheme { InputFieldWidgetBlock(WidgetBlockParameters(block, { submitted.set(it) })) } }
      ui.onNodeWithText("Code").performTextReplacement("->literal")
      ui.onNodeWithText("Code").performImeAction()
      ui.waitUntil(10000) { submitted.get() != null }
      assertEquals(mapOf("code" to "->literal"), submitted.get())
   }

   /*******************************************************************************
    ** Field presentation flags apply to widgets just as they do to semantic forms.
    *******************************************************************************/
   @Test
   fun widgetPasswordReadonlyAndHiddenMetadataAreHonored() {
      /*******************************************************************************
       **
       *******************************************************************************/
      fun block(name: String, type: String, properties: Map<String, Any> = emptyMap()) = WidgetBlock(
         name, "INPUT_FIELD", values = mutableMapOf(
            "fieldMetaData" to (mapOf("name" to name, "label" to name, "type" to type) + properties)
         )
      )
      ui.setContent {
         MaterialTheme {
            Column {
               InputFieldWidgetBlock(WidgetBlockParameters(block("Secret", "PASSWORD")))
               InputFieldWidgetBlock(WidgetBlockParameters(block("Readonly", "STRING", mapOf("isEditable" to false))))
               InputFieldWidgetBlock(WidgetBlockParameters(block("Hidden", "STRING", mapOf("isHidden" to true))))
            }
         }
      }
      ui.onNodeWithText("Secret").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
      ui.onNodeWithText("Readonly").assertIsNotEnabled()
      ui.onNodeWithText("Hidden").assertDoesNotExist()
   }
}
