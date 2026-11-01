/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import android.content.Context
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.lifecycle.viewModelScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.rememberNavController
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.kingsrook.qqq.frontend.android.core.controllers.QQQRepository
import com.kingsrook.qqq.frontend.android.core.model.Environment
import com.kingsrook.qqq.frontend.android.core.model.metadata.QAppMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.QAppNodeType
import com.kingsrook.qqq.frontend.android.core.model.metadata.QAppSection
import com.kingsrook.qqq.frontend.android.core.model.metadata.QAppTreeNode
import com.kingsrook.qqq.frontend.android.core.model.metadata.QComponentType
import com.kingsrook.qqq.frontend.android.core.model.metadata.QFrontendComponent
import com.kingsrook.qqq.frontend.android.core.model.metadata.QFrontendStepMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.QInstance
import com.kingsrook.qqq.frontend.android.core.model.metadata.QProcessMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.BaseAuthenticationMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.FullyAnonymousAuthenticationMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.ManageSessionRequest
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.ManageSessionResponse
import com.kingsrook.qqq.frontend.android.core.model.processes.ProcessStepResult
import com.kingsrook.qqq.frontend.android.core.model.processes.QJobComplete
import com.kingsrook.qqq.frontend.android.mobileapp.QMobileAppMain
import com.kingsrook.qqq.frontend.android.mobileapp.data.MockPreferencesDataStore
import com.kingsrook.qqq.frontend.android.mobileapp.container.QAppContainer
import com.kingsrook.qqq.frontend.android.mobileapp.ui.LocalCustomWidgetRenderer
import com.kingsrook.qqq.frontend.android.mobileapp.ui.LocalProcessInputAllowed
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.cancel
import java.util.concurrent.atomic.AtomicReference

/*******************************************************************************
 ** Production widget and navigation surfaces; only the private hardware driver is synthetic.
 *******************************************************************************/
class ScannerOverlayTest {
   @get:Rule
   val ui = createAndroidComposeRule<ComponentActivity>()
   private var previousEnvironments: List<Environment>? = null
   private var fixtureModel: QViewModel? = null

   /*******************************************************************************
    **
    *******************************************************************************/
   @Before
   fun configureSyntheticEnvironment() {
      previousEnvironments = runCatching { QAppContainer.availableEnvironments }.getOrNull()
      QAppContainer.availableEnvironments = listOf(Environment("QA overlay", "https://overlay.invalid/"))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @After
   fun disposeSyntheticEnvironment() {
      ui.activityRule.scenario.onActivity {
         fixtureModel?.activeProcessViewModel?.reset()
         fixtureModel?.viewModelScope?.cancel()
         fixtureModel = null
         QAppContainer.availableEnvironments = previousEnvironments ?: listOf(Environment("Unconfigured", "https://unused.invalid/"))
      }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   private class Device : ScannerDriver {
      lateinit var listener: ScannerDriver.Listener
      var pending = false
      var closed = false
      var reads = 0
      var cancels = 0

      /*******************************************************************************
       **
       *******************************************************************************/
      override fun open(listener: ScannerDriver.Listener) {
         this.listener = listener; listener.idle()
      }

      /*******************************************************************************
       **
       *******************************************************************************/
      override fun isReadPending() = pending

      /*******************************************************************************
       **
       *******************************************************************************/
      override fun read() {
         check(!closed); reads++; pending = true
      }

      /*******************************************************************************
       **
       *******************************************************************************/
      override fun cancelRead() {
         cancels++; pending = false; listener.idle()
      }

      /*******************************************************************************
       **
       *******************************************************************************/
      override fun close() {
         closed = true; pending = false
      }

      /*******************************************************************************
       **
       *******************************************************************************/
      fun emit(value: String) {
         pending = false; listener.scanned(value, "QRCODE"); listener.idle()
      }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun overlaysAndLostWindowFocusBlockScansWithoutLosingDraftOrTrappingResume() {
      check(InstrumentationRegistry.getInstrumentation().targetContext.packageName == "io.qrun.mobile.scanner.qa")
      val repository = Repository()
      val model = QViewModel(repository, MockPreferencesDataStore()).apply { supportedCustomWidgetTypes = setOf("SCANNER") }
      fixtureModel = model
      val devices = mutableListOf<Device>()
      val driverFactory: (Context) -> ScannerDriver? = { Device().also { devices.add(it) } }
      val externalDialog = mutableStateOf(false)
      val parentAllowed = mutableStateOf(true)
      val observedInputAllowed = AtomicReference<(() -> Boolean)?>(null)
      ui.setContent {
         model.setNavController(rememberNavController())
         CompositionLocalProvider(
            LocalProcessInputAllowed provides { parentAllowed.value },
            LocalCustomWidgetRenderer provides { parameters, modifier, disabled ->
               if (parameters.widgetBlock.blockType == "SCANNER") {
                  val availability = LocalProcessInputAllowed.current
                  SideEffect { observedInputAllowed.set(availability) }
                  ScannerWidget(parameters, modifier, disabled, driverFactory)
                  true
               } else false
            }) { QMobileAppMain(model, "Scanner overlay test", showNetworkDialog = false) }
         // Deliberately outside semantic availability: proves actual window focus is independently checked.
         if (externalDialog.value) AlertDialog(
            onDismissRequest = { externalDialog.value = false },
            title = { Text("External window") }, confirmButton = {
               TextButton(onClick = { externalDialog.value = false }) { Text("Close external") }
            })
      }
      waitFor("Log In")
      ui.onNodeWithText("Log In").performClick()
      ui.waitUntil(5000) { model.isAuthenticated }
      ui.onNodeWithTag("appHome.rowForApp:app").performClick()
      ui.onNodeWithTag("appHome.rowForProcess:scan").performClick()
      /*******************************************************************************
       **
       *******************************************************************************/
      fun imeHidden() = ViewCompat.getRootWindowInsets(ui.activity.window.decorView)
         ?.isVisible(WindowInsetsCompat.Type.ime()) == false

      /*******************************************************************************
       **
       *******************************************************************************/
      fun ready(phase: String) {
         try {
            ui.waitUntil(5000) { devices.lastOrNull()?.pending == true && ui.activity.hasWindowFocus() }
         } catch (error: ComposeTimeoutException) {
            val drawerVisible = runCatching { ui.onNodeWithTag("drawer.settings").assertIsDisplayed(); true }.getOrDefault(false)
            val closeVisible = runCatching { ui.onNodeWithText("Close").assertIsDisplayed(); true }.getOrDefault(false)
            var flags = "unavailable"
            ui.activityRule.scenario.onActivity { activity ->
               val device = devices.lastOrNull()
               flags = "focus=${activity.hasWindowFocus()}, lifecycle=${activity.lifecycle.currentState}, " +
                  "imeHidden=${imeHidden()}, inputAllowed=${observedInputAllowed.get()?.invoke()}, " +
                  "driverPending=${device?.pending}, driverClosed=${device?.closed}, reads=${device?.reads}, cancels=${device?.cancels}, " +
                  "drivers=${devices.size}, drawerVisible=$drawerVisible, closeVisible=$closeVisible, " +
                  "waiting=${model.activeProcessViewModel?.waitingOnBackend}, pendingEvent=${model.activeProcessViewModel?.pendingStepValues != null}, " +
                  "leaveOpen=${model.qNavigator.leaveRequest != null}, parentAllowed=${parentAllowed.value}"
            }
            throw AssertionError("Scanner did not resume at $phase: $flags", error)
         }
      }

      /*******************************************************************************
       **
       *******************************************************************************/
      fun reject() {
         val count = repository.events.size
         ui.runOnIdle { devices.last().emit("blocked") }
         ui.waitForIdle()
         assertEquals("Overlay must not submit a step", count, repository.events.size)
      }

      /*******************************************************************************
       **
       *******************************************************************************/
      fun resumeAndScan(phase: String) {
         ready(phase)
         ui.onNodeWithText("Manual barcode").assertTextContains("draft")
         val count = repository.events.size
         ui.runOnIdle { devices.last().emit("00123") }
         ui.waitUntil(5000) { repository.events.size == count + 1 && model.activeProcessViewModel?.pendingStepValues == null }
         ready("$phase-acknowledged")
      }

      /*******************************************************************************
       **
       *******************************************************************************/
      fun seedDraft(phase: String) {
         // Each successful scan submits/remounts the step; preservation applies within that step.
         ready("$phase-before-overlay")
         ui.onNodeWithText("Manual barcode").performTextReplacement("draft")
         ui.onNodeWithText("Manual barcode").performImeAction()
         ui.waitUntil(5000) { imeHidden() }
         ui.onNodeWithText("Manual barcode").assertTextContains("draft")
      }
      ready("initial")
      val initialProcess = model.activeProcessViewModel
      seedDraft("drawer-back")
      ui.onNodeWithTag("topBar.menuIcon").performClick()
      ui.onNodeWithTag("drawer.settings").assertIsDisplayed()
      reject()
      ui.waitForIdle()
      ui.waitUntil(5000) { imeHidden() && ui.activity.hasWindowFocus() }
      InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
      resumeAndScan("drawer-back")
      ui.onNodeWithTag("drawer.settings").assertIsNotDisplayed()
      ui.runOnIdle {
         assertNull("Drawer Back must not open Leave behind the drawer", model.qNavigator.leaveRequest)
         assertSame(initialProcess, model.activeProcessViewModel)
         assertEquals(0, repository.cancellations)
      }

      for (destination in listOf("settings", "diagnostics")) {
         seedDraft(destination)
         ui.onNodeWithTag("topBar.menuIcon").performClick()
         ui.onNodeWithTag("drawer.$destination").performClick()
         waitFor("Close")
         reject()
         ui.onNodeWithText("Close").performClick()
         resumeAndScan(destination)
      }
      seedDraft("account")
      ui.onNodeWithTag("topBar.accountIcon").performClick()
      ui.onNodeWithTag("accountDialog.title").assertIsDisplayed()
      reject()
      ui.onNodeWithText("Close").performClick()
      resumeAndScan("account")

      seedDraft("leave-stay")
      ui.onNodeWithContentDescription("Go Back").performClick()
      waitFor("Stay")
      reject()
      ui.onNodeWithText("Stay").performClick()
      resumeAndScan("leave-stay")
      seedDraft("signout-stay")
      ui.onNodeWithTag("topBar.accountIcon").performClick()
      ui.onNodeWithText("Sign out").performClick()
      waitFor("Stay")
      reject()
      ui.onNodeWithText("Stay").performClick()
      resumeAndScan("signout-stay")

      seedDraft("external-window")
      ui.runOnIdle { externalDialog.value = true }
      waitFor("Close external")
      ui.waitUntil(5000) { !ui.activity.hasWindowFocus() }
      reject()
      ui.onNodeWithText("Close external").performClick()
      resumeAndScan("external-window")

      // A host-owned secondary surface blocks input even when it does not create another window.
      seedDraft("host-surface")
      ui.runOnIdle { parentAllowed.value = false; devices.last().emit("blocked-before-recomposition") }
      ui.waitForIdle()
      assertEquals(7, repository.events.size)
      ui.runOnIdle { parentAllowed.value = true }
      resumeAndScan("host-surface")
      assertEquals(8, repository.events.size)
      assertEquals(8, repository.events.map { it["eventId"] }.toSet().size)
      assertTrue(repository.events.all { it["barcode"] == "00123" })
      assertEquals(0, repository.cancellations)
      assertTrue(model.isAuthenticated)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   private fun waitFor(text: String) {
      ui.waitUntil(5000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   private class Repository : QQQRepository {
      val events = mutableListOf<Map<String, Any?>>()
      var cancellations = 0

      // Match decoded server JSON containers: WidgetBlock owns mutable values/styles.
      private val scannerComponent = QFrontendComponent(
         QComponentType.WIDGET,
         mutableMapOf(
            "isAdHocWidget" to true, "blocks" to listOf(
               mutableMapOf(
                  "blockId" to "scan", "blockType" to "SCANNER",
                  "styles" to mutableMapOf<String, Any?>(), "values" to mutableMapOf<String, Any?>()
               )
            )
         )
      )
      private val metadata = QProcessMetaData(
         "scan", "Scan", frontendSteps = listOf(
            QFrontendStepMetaData("input", "Input", components = listOf(scannerComponent))
         )
      )

      /*******************************************************************************
       **
       *******************************************************************************/
      override fun setEnvironment(environment: Environment) {}

      /*******************************************************************************
       **
       *******************************************************************************/
      override fun setSessionUUID(sessionUUID: String) {}

      /*******************************************************************************
       **
       *******************************************************************************/
      override fun getURI(path: String) = "https://unused.invalid/$path"

      /*******************************************************************************
       **
       *******************************************************************************/
      override suspend fun getAuthenticationMetaData(): BaseAuthenticationMetaData = FullyAnonymousAuthenticationMetaData("FULLY_ANONYMOUS")

      /*******************************************************************************
       **
       *******************************************************************************/
      override suspend fun manageSession(body: ManageSessionRequest) = ManageSessionResponse("synthetic-overlay-session")

      /*******************************************************************************
       **
       *******************************************************************************/
      override suspend fun getMetaData(frontendName: String, frontendVersion: String, applicationName: String, applicationVersion: String) = QInstance(
         appTree = listOf(QAppTreeNode("app", "App", QAppNodeType.APP)),
         apps = mapOf("app" to QAppMetaData("app", "App", sections = listOf(QAppSection("section", "Section", processes = listOf("scan"))))),
         processes = mapOf("scan" to metadata)
      )

      /*******************************************************************************
       **
       *******************************************************************************/
      override suspend fun getProcessMetaData(processName: String) = metadata

      /*******************************************************************************
       **
       *******************************************************************************/
      override suspend fun processInit(processName: String, values: Map<String, Any?>, recordsParam: String?, recordIds: String?, filterJSON: String?, stepTimeoutMillis: Int?) = QJobComplete("synthetic-process", nextStep = "input")

      /*******************************************************************************
       **
       *******************************************************************************/
      override suspend fun processStep(processName: String, processUUID: String, stepName: String, values: Map<String, Any?>, stepTimeoutMillis: Int?): ProcessStepResult {
         events.add(values.toMap())
         return QJobComplete(processUUID, mapOf("acknowledgedEventId" to values["eventId"], "scanSaved" to true, "scanId" to events.size.toString()), "input")
      }

      /*******************************************************************************
       **
       *******************************************************************************/
      override suspend fun processCancel(processName: String, processUUID: String) {
         cancellations++
      }

      /*******************************************************************************
       **
       *******************************************************************************/
      override suspend fun processJobStatus(processName: String, processUUID: String, jobUUID: String): ProcessStepResult = error("No jobs in this fixture")
   }
}
