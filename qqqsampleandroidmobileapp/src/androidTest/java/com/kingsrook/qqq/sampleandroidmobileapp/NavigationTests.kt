/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.sampleandroidmobileapp

import android.view.KeyEvent
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.assertTextContains
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import com.kingsrook.qqq.frontend.android.mobileapp.QMobileAppMain
import com.kingsrook.qqq.sampleandroidmobileapp.utils.TestUtils
import com.kingsrook.qqq.frontend.android.core.controllers.QQQRepository
import com.kingsrook.qqq.frontend.android.core.model.metadata.*
import com.kingsrook.qqq.frontend.android.core.model.processes.QJobComplete
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel
import com.kingsrook.qqq.frontend.android.mobileapp.data.MockPreferencesDataStore
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NavigationTests : BaseTest()
{
   @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

   private fun hardwareBack()
   {
      // Dialog dismissal is asynchronous at the Android window layer, even after Compose settles.
      ui.waitUntil(5000) { ui.activity.hasWindowFocus() }
      InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
      ui.waitForIdle()
   }

   @Test fun signedOutDrawerExposesSettingsWithoutBusinessNavigation()
   {
      TestUtils.startStandardTest(ui)
      ui.onNodeWithTag("topBar.menuIcon").performClick()
      ui.onNodeWithTag("drawer.home").assertDoesNotExist()
      ui.onNodeWithTag("drawer.app:app1").assertDoesNotExist()
      ui.onNodeWithTag("drawer.settings").assertIsDisplayed().performClick()
      ui.onNodeWithText("Close").performClick()
      ui.onNodeWithTag("topBar.menuIcon").performClick()
      hardwareBack()
      ui.onNodeWithText("Log In").assertIsDisplayed()
      ui.onNodeWithTag("topBar.apps").assertDoesNotExist()
      ui.onNodeWithTag("topBar.switchApp").assertDoesNotExist()
   }

   @Test fun leavingSwitchingSettingsAndSignOutPreserveOrCancelExactlyOnce()
   {
      val cancellations = AtomicInteger()
      val submissions = AtomicInteger()
      val base = SampleAppMockQQQRepository()
      val repository = object : QQQRepository by base
      {
         override suspend fun getMetaData(frontendName: String, frontendVersion: String, applicationName: String, applicationVersion: String): QInstance
         {
            val instance = base.getMetaData(frontendName, frontendVersion, applicationName, applicationVersion)
            return instance.copy(apps = instance.apps.orEmpty() + ("app2" to QAppMetaData("app2", "App 2")))
         }
         override suspend fun getProcessMetaData(processName: String) = QProcessMetaData("process1", "Process 1", frontendSteps = listOf(
            QFrontendStepMetaData("input", "Input", components = listOf(QFrontendComponent(QComponentType.EDIT_FORM)),
               formFields = listOf(QFieldMetaData("reference", "Reference", type = QFieldType.STRING)))))
         override suspend fun processInit(processName: String, values: Map<String, Any?>, recordsParam: String?, recordIds: String?, filterJSON: String?, stepTimeoutMillis: Int?) = QJobComplete("navigation-process", nextStep = "input")
         override suspend fun processStep(processName: String, processUUID: String, stepName: String, values: Map<String, Any?>, stepTimeoutMillis: Int?): QJobComplete
         {
            submissions.incrementAndGet()
            return QJobComplete(processUUID)
         }
         override suspend fun processCancel(processName: String, processUUID: String) { cancellations.incrementAndGet() }
      }
      val model = QViewModel(repository, MockPreferencesDataStore())
      TestUtils.startStandardTest(ui, model)
      TestUtils.login(ui)
      fun openProcess(fromCatalog: Boolean = true)
      {
         if(fromCatalog) ui.onNodeWithTag("appHome.rowForApp:app1").performClick()
         ui.onNodeWithTag("appHome.rowForProcess:process1").performClick()
         ui.waitUntil(5000) { model.activeProcessViewModel?.activeStepName == "input" }
      }
      openProcess()
      ui.onNodeWithTag("topBar.apps").assertDoesNotExist()
      ui.onNodeWithTag("topBar.switchApp").assertDoesNotExist()
      ui.onNodeWithText("Reference").performTextReplacement("preserved draft")
      ui.onNodeWithText("Reference").performImeAction()
      ui.onNodeWithContentDescription("Go Back").performClick()
      ui.onNodeWithText("Stay").performClick()
      ui.onNodeWithText("Reference").assertTextContains("preserved draft")
      assertEquals(0, cancellations.get())
      ui.onNodeWithTag("topBar.menuIcon").performClick()
      ui.onNodeWithTag("drawer.home").assertIsDisplayed()
      hardwareBack()
      ui.onNodeWithText("Reference").assertTextContains("preserved draft")
      assertEquals(0, cancellations.get())
      ui.onNodeWithTag("topBar.menuIcon").performClick()
      ui.onNodeWithTag("drawer.settings").assertIsDisplayed().performClick()
      ui.onNodeWithText("Close").performClick()
      ui.onNodeWithTag("topBar.menuIcon").performClick()
      ui.onNodeWithTag("drawer.diagnostics").assertIsDisplayed().performClick()
      ui.onNodeWithText("Close").performClick()
      ui.onNodeWithText("Reference").assertTextContains("preserved draft")
      assertEquals(0, cancellations.get())
      ui.onNodeWithTag("topBar.accountIcon").performClick()
      ui.onNodeWithText("Sign out").performClick()
      ui.onNodeWithText("Stay").performClick()
      ui.onNodeWithText("Reference").assertTextContains("preserved draft")
      assertEquals(0, submissions.get())
      hardwareBack()
      ui.onNodeWithText("Leave process").performClick()
      ui.waitUntil(5000) { model.activeProcessViewModel == null }
      assertEquals(1, cancellations.get())
      assertEquals("app1", model.qNavigator.currentAppName)

      openProcess(false)
      ui.onNodeWithTag("topBar.menuIcon").performClick()
      ui.onNodeWithTag("drawer.home").performClick()
      ui.onNodeWithText("Leave process").performClick()
      ui.waitUntil(5000) { model.qNavigator.atHome }
      assertEquals(2, cancellations.get())
      openProcess()
      ui.onNodeWithTag("topBar.menuIcon").performClick()
      ui.onNodeWithTag("drawer.app:app2").performScrollTo().performClick()
      ui.onNodeWithText("Leave process").performClick()
      ui.waitUntil(5000) { model.qNavigator.currentAppName == "app2" }
      assertEquals(3, cancellations.get())
      ui.onNodeWithContentDescription("Go Back").performClick()
      assertTrue(model.qNavigator.atHome)

      openProcess()
      ui.onNodeWithText("Next").performClick()
      ui.onNodeWithText("Close").performClick()
      assertEquals(3, cancellations.get())
      assertEquals(1, submissions.get())
      openProcess(false)
      ui.onNodeWithTag("topBar.accountIcon").performClick()
      ui.onNodeWithText("Sign out").performClick()
      ui.onNodeWithText("Sign out", substring = false).performClick()
      ui.waitUntil(5000) { !model.isAuthenticated }
      assertEquals(4, cancellations.get())
      ui.onNodeWithText("Log back in").performClick()
      TestUtils.login(ui)
      assertTrue(model.qNavigator.atHome)
      assertNull(model.activeProcessViewModel)
   }

   @Test fun systemBackAndToolbarBackUseTheActualDestination()
   {
      TestUtils.startStandardTest(ui)
      TestUtils.login(ui)
      val navigator = TestUtils.qViewModel.qNavigator
      ui.onNodeWithTag("appHome.rowForApp:app1").performClick()
      ui.onNodeWithTag("topAppBar.title").assertTextContains("App 1")
      hardwareBack()
      ui.waitForIdle()
      assertTrue(navigator.atHome)
      assertNull(navigator.currentAppName)
      ui.onNodeWithTag("topAppBar.title").assertTextContains("Apps")
      ui.onNodeWithTag("appHome.rowForApp:app1").performClick()
      ui.onNodeWithContentDescription("Go Back").performClick()
      ui.onNodeWithTag("topAppBar.title").assertTextContains("Apps")
      ui.runOnIdle { navigator.popStack() }
      assertTrue(navigator.atHome)
   }

   @Test fun replacingControllerDropsStaleAppAndProcessState()
   {
      val model = TestUtils.createQViewModel()
      TestUtils.qViewModel = model
      val generation = mutableIntStateOf(0)
      ui.setContent()
      {
         key(generation.intValue)
         {
            model.setNavController(rememberNavController())
            QMobileAppMain(model, "Test App")
         }
      }
      TestUtils.login(ui)
      ui.onNodeWithTag("appHome.rowForApp:app1").performClick()
      ui.onNodeWithTag("appHome.rowForProcess:process1").performClick()
      ui.onNodeWithTag("topAppBar.title").assertTextContains("Process 1")
      ui.runOnIdle { generation.intValue++ }
      ui.onNodeWithTag("topAppBar.title").assertTextContains("Apps")
      assertTrue(model.qNavigator.atHome)
      assertNull(model.qNavigator.currentAppName)
      assertNull(model.activeProcessViewModel)
      assertEquals(0, model.qNavigator.navDepth)
   }
}
