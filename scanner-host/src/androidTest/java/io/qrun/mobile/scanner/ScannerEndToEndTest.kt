/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import android.os.Bundle
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Color
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasSetTextAction
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.content.FileProvider
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*******************************************************************************
 ** Real host UI and real QQQ server. No repository mocks or production app data.
 *******************************************************************************/
@RunWith(AndroidJUnit4::class)
class ScannerEndToEndTest {
   @get:Rule
   val ui = createAndroidComposeRule<ScannerActivity>()
   private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
   private lateinit var origin: String
   private lateinit var adminCookie: String
   /*******************************************************************************
    ** Explicitly prepare a disposable QA app for an operator's manual record/photo test.
    ** Uses real sign-in and approval; does not alter the ordinary scanner application.
    *******************************************************************************/
   @Test
   fun prepareManualRecordPhotoPreview() {
      val context = InstrumentationRegistry.getInstrumentation().targetContext
      check(context.packageName == "io.qrun.mobile.scanner.qa")
      val configFile = File(context.filesDir, "manual-photo-preview.json")
      val config = try { JSONObject(configFile.readText()) } finally { configFile.delete() }
      check(config.optBoolean("manualPhotoPreview"))
      origin = config.getString("serverUrl").trimEnd('/')
      val endpoint = URI(origin)
      check(endpoint.scheme == "http" && endpoint.host == "127.0.0.1" && endpoint.port in 1024..65535 &&
         endpoint.port !in setOf(8080, 57832, 51631, 18081, 18086) && endpoint.path in setOf("", "/") &&
         endpoint.userInfo == null && endpoint.query == null && endpoint.fragment == null)
      waitFor("QQQ server URL")
      ui.onNodeWithText("QQQ server URL").performTextReplacement("$origin/")
      ui.onNodeWithText("Connect", substring = false).performClick()
      waitFor("Username")
      ui.onNodeWithText("Username").performTextReplacement("scanner")
      ui.onNodeWithText("Password").performTextReplacement(config.getString("scannerPassword"))
      ui.onNodeWithText("Password").performImeAction()
      ui.onNode(hasText("Sign in") and hasClickAction()).performScrollTo().performClick()
      waitFor("An administrator must approve this device before you can run mobile apps.")
      val device = DeviceCredentials(context, "$origin/")
      adminCookie = loginAdmin(config.getString("adminPassword"))
      runAdmin("mobileDeviceApprove", device.deviceId)
      ui.onNodeWithText("Check approval").performScrollTo().performClick()
      waitForTag("appHome.rowForApp:mobileScanApp")
      ui.onNodeWithTag("appHome.rowForApp:mobileScanApp").performClick()
      waitForTag("appHome.rowForTable:document")
      ui.onNodeWithTag("appHome.rowForTable:document").performClick()
      waitForTag("records.row:1")
      ui.onNodeWithTag("records.row:1").performClick()
      waitFor("Take photo")
      stage("manual-photo-preview-ready")
   }

   /*******************************************************************************
    ** Exercise real Echo process/PVS persistence with explicitly simulated print acceptance.
    ** No printer, production scanner ingress, screenshots or private identifiers become evidence.
    *******************************************************************************/
   @Test
   fun echoSelectsOnceQueuesTwoScansAndClosesOnAppsLeave() {
      val context = InstrumentationRegistry.getInstrumentation().targetContext
      check(context.packageName == "io.qrun.mobile.scanner.e2e") { "Echo requires the disposable E2E package" }
      val configFile = File(context.filesDir, "e2e.json")
      val config = try { JSONObject(configFile.readText()) }
      catch(exception: Exception) { throw AssertionError("Echo fixture configuration is invalid") }
      finally { configFile.delete() }
      check(config.optString("fixtureType") == "echo-ui-e2e" && config.optBoolean("simulatedPrinting"))
      origin = config.getString("serverUrl").trimEnd('/')
      val endpoint = URI(origin)
      check(endpoint.scheme == "http" && endpoint.host == "127.0.0.1" && endpoint.port in 1..65535 &&
         endpoint.port !in setOf(57832, 51631, 18081, 18086) && endpoint.path in setOf("", "/") &&
         endpoint.userInfo == null && endpoint.query == null && endpoint.fragment == null)

      waitFor("QQQ server URL")
      ui.onNodeWithText("QQQ server URL").performTextReplacement("$origin/")
      ui.onNodeWithText("Connect", substring = false).performClick()
      waitFor("Username")
      ui.onNodeWithText("Username").performTextReplacement("scanner")
      ui.onNodeWithText("Password").performTextReplacement(config.getString("scannerPassword"))
      ui.onNodeWithText("Password").performImeAction()
      ui.onNode(hasText("Sign in") and hasClickAction()).performScrollTo().performClick()
      waitFor("An administrator must approve this device before you can run mobile apps.")
      val device = DeviceCredentials(context, "$origin/")
      adminCookie = loginAdmin(config.getString("adminPassword"))
      runAdmin("mobileDeviceApprove", device.deviceId)
      ui.onNodeWithText("Check approval").performScrollTo().performClick()
      waitForTag("appHome.rowForApp:echo")
      val scannerCookie = loginAdmin(config.getString("scannerPassword"), "scanner")

      /*******************************************************************************
       ** Verify persisted rows through ordinary QQQ reads under the operator's real actor/device locks.
       *******************************************************************************/
      fun rows(table: String): List<JSONObject> {
         check(table in setOf("echoScan", "echoFixtureRun"))
         val request = Request.Builder().url("$origin/qqq/v1/table/$table/query").header("Cookie", scannerCookie)
            .post("{}".toRequestBody("application/json".toMediaType()))
         device.headers().forEach { (name, value) -> request.header(name, value) }
         val records = executeJson(request.build()).getJSONArray("records")
         return (0 until records.length()).map { records.getJSONObject(it).getJSONObject("values") }
      }

      /*******************************************************************************
       ** Discover choices through the same versioned `searchProcessPossibleValues` POST the renderer uses
       ** ({values, processValues, useCase:form}), so no document id or label is assumed by this harness.
       *******************************************************************************/
      fun choices(field: String, values: JSONObject): List<Pair<Any, String>> {
         val request = Request.Builder().url("$origin/qqq/v1/processes/echoPrint/possibleValues/$field").header("Cookie", scannerCookie)
            .post(JSONObject().put("values", values).put("processValues", JSONObject()).put("useCase", "form").toString().toRequestBody("application/json".toMediaType()))
         device.headers().forEach { (name, value) -> request.header(name, value) }
         val options = executeJson(request.build()).optJSONArray("options") ?: return emptyList()
         return (0 until options.length()).map { options.getJSONObject(it) }.map { it.get("id") to it.getString("label") }
      }
      val printerChoice = choices("printerId", JSONObject().put("mediaId", JSONObject.NULL).put("templateId", JSONObject.NULL)).single { it.second == "Simulated printer" }
      val mediaChoice = choices("mediaId", JSONObject().put("printerId", printerChoice.first).put("templateId", JSONObject.NULL)).single { it.second == "Simulated 4 x 6 in — 300 dpi" }
      val documents = choices("templateId", JSONObject().put("printerId", printerChoice.first).put("mediaId", mediaChoice.first))
      assertTrue("Document choices must be offered for the selected printer/media with unique labels and true JSON integer ids",
         documents.isNotEmpty() && documents.map { it.second }.toSet().size == documents.size && documents.all { it.first is Int || it.first is Long })
      // Choose a different document after the stale-selection check when more than one is offered, so restoring the old one is detectable.
      val firstDocument = documents.first()
      val finalDocument = documents.last()
      // Persisted setup column holding the chosen document id; confirm against the server fixture schema.
      val documentColumn = "templateId"
      // Persisted setup column and process field for the optional Copies per scan integer. The Echo owner must expose
      // both; the Printing service's own limit is 1..100, and this test does not hard-code any other bound.
      val copiesColumn = "copies"
      val selectedCopies = 3
      fun assertCopiesPinned(row: JSONObject) {
         val pinnedCopies = row.opt(copiesColumn)
         assertTrue("Setup must pin Copies per scan as the true JSON integer $selectedCopies the operator chose (found: $pinnedCopies); " +
            "the Echo fixture run table must expose an integer '$copiesColumn' column",
            pinnedCopies is Number && pinnedCopies.toLong() == selectedCopies.toLong())
      }
      fun assertSetupPinned(row: JSONObject) {
         assertTrue("Setup must pin the selected printer and media once", row.getString("state") == "OPEN" &&
            row.getString("printerRecordId") == "fixture-printer" && row.getString("mediaId") == "fixture-4x6")
         val persisted = row.opt(documentColumn)
         assertTrue("Setup must persist the chosen document as the same integer id the choices returned",
            persisted is Number && persisted.toLong() == (finalDocument.first as Number).toLong())
         assertCopiesPinned(row)
      }

      /*******************************************************************************
       ** The metadata-driven integer control: a text field labelled by the server's field label, found by its set-text action.
       *******************************************************************************/
      val copiesField = hasText("Copies per scan") and hasSetTextAction()
      fun copiesValue(): String? = ui.onNode(copiesField).performScrollTo().fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text
      fun enterCopies(value: String) {
         ui.onNode(copiesField).performScrollTo().performTextReplacement(value)
         // Dismiss the keyboard as a person would, so later read-only scan-display assertions measure the app, not this input.
         ui.activityRule.scenario.onActivity { WindowCompat.getInsetsController(it.window, it.window.decorView).hide(WindowInsetsCompat.Type.ime()) }
         ui.waitForIdle()
      }

      /*******************************************************************************
       ** The only enabled, unselected choice control (earlier fields already hold labels).
       *******************************************************************************/
      fun openOnlyRemainingChoice() {
         ui.waitUntil(timeoutMillis = 60_000) {
            ui.onAllNodesWithText("Choose a value").fetchSemanticsNodes().size == 1 &&
               ui.onAllNodes(hasText("Choose a value") and isEnabled()).fetchSemanticsNodes().size == 1
         }
         ui.onNodeWithText("Choose a value").performScrollTo().performClick()
      }

      val priorRuns = rows("echoFixtureRun").map { it.getString("id") }.toSet()
      ui.onNodeWithTag("appHome.rowForApp:echo").performClick()
      waitForTag("appHome.rowForProcess:echoPrint")
      ui.onNodeWithTag("appHome.rowForProcess:echoPrint").performClick()
      waitFor("Printer")
      waitFor("Paper or label size")
      waitFor("Document")
      waitFor("Copies per scan")
      assertEquals("Copies per scan must default to 1 from the server's field metadata", "1", copiesValue())
      stage("echo-copies-defaulted")
      ui.onAllNodesWithText("Choose a value").onFirst().performScrollTo().performClick()
      waitFor("Simulated printer")
      ui.onNodeWithText("Simulated printer").performClick()
      // Media is a contingent field: it stays disabled until the server returns choices for the selected printer.
      ui.waitUntil(timeoutMillis = 60_000) { ui.onAllNodes(hasText("Choose a value") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
      ui.onAllNodesWithText("Choose a value").onFirst().performScrollTo().performClick()
      waitFor("Simulated 4 x 6 in — 300 dpi")
      ui.onNodeWithText("Simulated 4 x 6 in — 300 dpi").performClick()
      // Document depends on both printer and media; it is the only unselected control left once media holds its label.
      openOnlyRemainingChoice()
      waitFor(firstDocument.second)
      ui.onNodeWithText(firstDocument.second).performClick()
      waitFor(firstDocument.second)
      stage("echo-document-selected")

      // Choosing a controlling field again drops every dependent selection and blocks Next until they are reselected.
      ui.onNodeWithText("Simulated printer").performScrollTo().performClick()
      ui.onNode(hasText("Simulated printer") and hasAnyAncestor(isPopup())).performClick()
      ui.waitUntil(timeoutMillis = 60_000) { ui.onAllNodesWithText("Choose a value").fetchSemanticsNodes().size == 2 }
      assertTrue("The stale document must not stay selected", ui.onAllNodesWithText(firstDocument.second).fetchSemanticsNodes().isEmpty())
      waitFor("Choose Paper or label size first.")
      ui.onNodeWithText("Next", substring = false).performClick()
      waitFor("Paper or label size is required.")
      waitFor("Document is required.")
      assertTrue("Next must not start setup without a reselected document", ui.onAllNodesWithText("Scanner ready. Press the handheld trigger.").fetchSemanticsNodes().isEmpty() &&
         rows("echoFixtureRun").none { it.getString("id") !in priorRuns && it.optString("printerRecordId").isNotBlank() })
      ui.onAllNodesWithText("Choose a value").onFirst().performScrollTo().performClick()
      waitFor("Simulated 4 x 6 in — 300 dpi")
      ui.onNodeWithText("Simulated 4 x 6 in — 300 dpi").performClick()
      openOnlyRemainingChoice()
      waitFor(finalDocument.second)
      ui.onNodeWithText(finalDocument.second).performClick()
      waitFor(finalDocument.second)
      assertTrue("Only the reselected document may remain chosen", firstDocument == finalDocument || ui.onAllNodesWithText(firstDocument.second).fetchSemanticsNodes().isEmpty())
      stage("echo-stale-document-cleared")
      assertEquals("Reselecting a controlling choice must not disturb the independent Copies per scan value", "1", copiesValue())
      // The field's advertised bounds (Printing's 1..100) are enforced by the renderer before any run is created.
      for ((invalid, message) in listOf("0" to "Copies per scan is below the allowed minimum.", "101" to "Copies per scan exceeds the allowed maximum.")) {
         enterCopies(invalid)
         ui.onNodeWithText("Next", substring = false).performClick()
         waitFor(message)
         assertTrue("Invalid Copies per scan must not start setup", ui.onAllNodesWithText("Scanner ready. Press the handheld trigger.").fetchSemanticsNodes().isEmpty() &&
            rows("echoFixtureRun").none { it.getString("id") !in priorRuns && it.optString("printerRecordId").isNotBlank() })
      }
      enterCopies(selectedCopies.toString())
      assertEquals("The chosen quantity must be held by the metadata-driven integer field", selectedCopies.toString(), copiesValue())
      stage("echo-copies-selected")
      ui.onNodeWithText("Next", substring = false).performClick()
      waitFor("Scanner ready. Press the handheld trigger.")
      assertTrue("Echo must not offer manual barcode input", ui.onAllNodesWithText("Manual barcode").fetchSemanticsNodes().isEmpty())
      assertTrue("Echo must not offer manual submission", ui.onAllNodesWithText("Submit barcode").fetchSemanticsNodes().isEmpty())
      // The scan display is read-only and present although Echo disables manual entry; nothing opens the keyboard.
      fun assertScanDisplay(barcode: String?, symbology: String?, status: String) {
         ui.onNodeWithTag("scanner-barcode").performScrollTo().assertIsDisplayed()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetText))
         if (barcode != null) ui.onNodeWithTag("scanner-barcode").assert(hasText(barcode))
         ui.onNodeWithTag("scanner-symbology").assertTextEquals("Symbology: ${symbology ?: "—"}")
         ui.onNodeWithTag("scanner-status").assertTextEquals(status)
         assertTrue("Read-only scan display must not open the keyboard",
            ViewCompat.getRootWindowInsets(ui.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) != true)
      }
      assertScanDisplay(null, null, "Waiting for scan")
      val run = rows("echoFixtureRun").single { it.getString("id") !in priorRuns }
      val runId = run.getString("id")
      assertSetupPinned(run)
      stage("echo-setup-selected")

      var receipt = ""
      val gs1Payload = "01095011015300031120101421TEST000123"
      for ((index, sample) in listOf("001234567890" to "LABEL-TYPE-CODE128", "000-ECHO-QR" to "QRCODE", gs1Payload to "EAN128").withIndex()) {
         waitFor("Scanner ready. Press the handheld trigger.")
         context.sendBroadcast(Intent("io.qrun.mobile.scanner.SCAN").setPackage(context.packageName)
            .addCategory(Intent.CATEGORY_DEFAULT).putExtra("com.symbol.datawedge.data_string", sample.first)
            .putExtra("com.symbol.datawedge.label_type", sample.second).apply {
               if (sample.second == "EAN128") {
                  putExtra("io.qrun.mobile.scanner.e2e.raw_data", sample.first.toByteArray(Charsets.US_ASCII))
                  putExtra("io.qrun.mobile.scanner.e2e.aim_identifier", "]C1")
               }
            })
         var accepted: JSONObject? = null
         waitForServer {
            accepted = rows("echoScan").singleOrNull { it.optString("runId") == runId && it.optString("barcode") == sample.first && it.optString("state") == "QUEUED" }
            accepted != null
         }
         val row = requireNotNull(accepted)
         receipt = row.getString("mobileScanId")
         waitFor("Print queued $receipt", substring = true)
         assertScanDisplay(sample.first, sample.second, "Confirmed by server")
         assertTrue("Accepted Echo scans must preserve exact type and approved-device attribution",
            row.getString("symbology") == sample.second && row.getString("rendererSymbology") == listOf("code128", "qr", "gs1-128")[index] &&
               row.getString("scanSource") == "e2e-fixture" && row.getString("deviceId") == device.deviceId && row.getString("actorId") == "scanner")
         assertTrue("Only a synthetic accepted job may acknowledge this fixture scan",
            row.getString("id") == receipt && row.getString("printJobId") == "fixture-job-$receipt" && row.getString("eventId").isNotBlank())
         assertTrue("Each event must create exactly one accepted Echo scan", rows("echoScan").count { it.optString("runId") == runId } == index + 1)
         assertSetupPinned(rows("echoFixtureRun").single { it.getString("id") == runId })
         if (index == 2) {
            assertEquals("]C1", row.getString("aimIdentifier"))
            assertEquals("[01]09501101530003[11]201014[21]TEST000123", row.getString("rendererValue"))
            assertEquals("(01)09501101530003(11)201014(21)TEST000123", row.getString("humanReadable"))
         }
         stage(listOf("echo-first-queued", "echo-second-queued", "echo-gs1-queued")[index])
      }

      ui.activityRule.scenario.onActivity { OrientationPolicy.LANDSCAPE.select(it) }
      waitFor("Print queued $receipt", substring = true)
      ui.activityRule.scenario.recreate()
      waitFor("Print queued $receipt", substring = true)
      assertScanDisplay(gs1Payload, "EAN128", "Confirmed by server")
      assertTrue("Recreation must retain scan phase without showing printer selection", ui.onAllNodesWithText("Choose a value").fetchSemanticsNodes().isEmpty())
      val acceptedRows = rows("echoScan").filter { it.optString("runId") == runId }
      assertTrue("Rotation/recreation must not accept another event", acceptedRows.size == 3 && acceptedRows.map { it.getString("eventId") }.toSet().size == 3 &&
         acceptedRows.map { it.getString("printJobId") }.toSet().size == 3)
      assertSetupPinned(rows("echoFixtureRun").single { it.getString("id") == runId })
      stage("echo-copies-persisted")
      stage("echo-recreation-stable")
      ui.onNodeWithTag("topBar.menuIcon").performClick()
      ui.onNodeWithTag("drawer.home").performClick()
      waitFor("Leave process")
      ui.onNodeWithText("Leave process", substring = false).performClick()
      waitForTag("appHome.rowForApp:echo")
      waitForServer { rows("echoFixtureRun").single { it.getString("id") == runId }.getString("state") == "CLOSED" }
      assertTrue("Leaving must close this run without accepting extra print work", rows("echoScan").count { it.optString("runId") == runId } == 3)
      assertCopiesPinned(rows("echoFixtureRun").single { it.getString("id") == runId })
      stage("echo-run-closed")
      ui.onNodeWithTag("appHome.rowForApp:echo").performClick()
      waitForTag("appHome.rowForTable:echoScan")
      ui.onNodeWithTag("appHome.rowForTable:echoScan").performClick()
      waitForTag("records.row:$receipt")
      ui.onNodeWithTag("records.row:$receipt").performClick()
      waitForTag("record.details")
      ui.onNodeWithText("(01)09501101530003(11)201014(21)TEST000123").performScrollTo().assertIsDisplayed()
      stage("echo-record-browsed")
   }

   /*******************************************************************************
    ** Real host/native activity-result boundaries and real upload APIs, using synthetic images only.
    *******************************************************************************/
   @Test
   fun cameraAutoUploadsAndPendingGallerySurvivesRotationThenCleansOnRemoteLogout() {
      exercisePhotoCapture(false)
   }

   /*******************************************************************************
    ** Record browsing selects the parent, and the same upload/lifecycle contract still applies.
    *******************************************************************************/
   @Test
   fun recordDetailsCameraAndGalleryAttachToSelectedRecord() {
      exercisePhotoCapture(true)
   }

   @Test
   fun richRecordsPageGroupNavigateAndAttachPhotos() {
      exercisePhotoCapture(fromRecord = true, rich = true)
   }

   private fun exercisePhotoCapture(fromRecord: Boolean, rich: Boolean = false) {
      val parentTable = if (rich) "testOrder" else "document"
      val parentId = if (rich) "26" else "1"
      val instrumentation = InstrumentationRegistry.getInstrumentation()
      val context = instrumentation.targetContext
      check(context.packageName == "io.qrun.mobile.scanner.e2e")
      val configFile = File(context.filesDir, "e2e.json")
      val config = JSONObject(configFile.readText())
      configFile.delete()
      origin = config.getString("serverUrl").trimEnd('/')
      // Explicit fixture expectations, not production host classification: the rich-records scenario (rich=true) runs
      // against the configured EchoDemo host, which admits an approved device's captures immediately
      // (Record Files quarantineCapturedFiles=false) and so expects AVAILABLE. The generic document/process photo
      // fixtures (rich=false) expect QUARANTINED.
      val echoHost = rich
      val savedMessage = if (echoHost) "Saved photo." else "Saved photo. Awaiting server review."
      val expectedLifecycle = if (echoHost) "AVAILABLE" else "QUARANTINED"
      val expectedBadge = if (echoHost) "Available" else "Awaiting review"
      val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
      val bytes = ByteArrayOutputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output); output.toByteArray() }
      bitmap.recycle()
      val directory = File(context.cacheDir, "record-photos").apply { mkdirs() }
      val galleryFile = File(directory, "gallery-fixture-${UUID.randomUUID()}.jpg").apply { writeBytes(bytes) }
      val galleryUri = FileProvider.getUriForFile(context, context.packageName + ".photos", galleryFile)
      val cameraCalls = AtomicInteger()
      val galleryCalls = AtomicInteger()
      val monitor = object : Instrumentation.ActivityMonitor() {
         /*******************************************************************************
          **
          *******************************************************************************/
         override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.action == MediaStore.ACTION_IMAGE_CAPTURE) {
               cameraCalls.incrementAndGet()
               // API26-compatible parcelable access; this URI is created by the tested host's FileProvider.
               @Suppress("DEPRECATION") val output = intent.getParcelableExtra<Uri>(MediaStore.EXTRA_OUTPUT)!!
               context.contentResolver.openOutputStream(output)!!.use { it.write(bytes) }
               return Instrumentation.ActivityResult(Activity.RESULT_OK, null)
            }
            if (intent.action in setOf(MediaStore.ACTION_PICK_IMAGES, Intent.ACTION_OPEN_DOCUMENT, Intent.ACTION_GET_CONTENT)) {
               galleryCalls.incrementAndGet()
               return Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(galleryUri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
            return null
         }
      }
      instrumentation.addMonitor(monitor)
      try {
         waitFor("QQQ server URL")
         ui.onNodeWithText("QQQ server URL").performTextReplacement("$origin/")
         ui.onNodeWithText("Connect", substring = false).performClick()
         waitFor("Username")
         ui.onNodeWithText("Username").performTextReplacement("scanner")
         ui.onNodeWithText("Password").performTextReplacement(config.getString("scannerPassword"))
         ui.onNodeWithText("Password").performImeAction()
         ui.onNode(hasText("Sign in") and hasClickAction()).performScrollTo().performClick()
         waitFor("An administrator must approve this device before you can run mobile apps.")
         val device = DeviceCredentials(context, "$origin/")
         adminCookie = loginAdmin(config.getString("adminPassword"))
         runAdmin("mobileDeviceApprove", device.deviceId)
         ui.onNodeWithText("Check approval").performScrollTo().performClick()
         val app = if (rich) "mobileTestData" else "mobileScanApp"
         waitForTag("appHome.rowForApp:$app")
         // Accessible activation avoids a system floating control covering a tile on physical devices.
         ui.onNodeWithTag("appHome.rowForApp:$app").performScrollTo().assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
         if (fromRecord) {
            waitForTag("appHome.rowForTable:$parentTable")
            ui.onNodeWithTag("appHome.rowForTable:$parentTable").performScrollTo().assertIsDisplayed()
               .performSemanticsAction(SemanticsActions.OnClick) { it() }
            waitForTag("records.row:1")
            if (rich) {
               ui.onNodeWithText("Next").performClick()
               waitForTag("records.row:26")
               ui.onNodeWithTag("records.row:1").assertDoesNotExist()
            }
            ui.onNodeWithTag("records.row:$parentId").performClick()
            waitForTag("record.details")
            if (rich) {
               ui.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
               waitForTag("records.row:26")
               ui.onNodeWithText("26–50 of 1000").assertIsDisplayed()
               ui.onNodeWithTag("records.row:1").assertDoesNotExist()
               ui.onNodeWithTag("records.search").performTextReplacement("TEST-000026")
               ui.waitForIdle()
               waitFor("1–1 of 1")
               ui.onNodeWithTag("records.row:26").performClick()
               waitForTag("record.details")
               ui.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
               waitFor("1–1 of 1")
               ui.onNodeWithTag("records.search").assertTextContains("TEST-000026")
               ui.onNodeWithText("Clear", substring = false).performClick()
               waitFor("1–25 of 1000")
               ui.onNodeWithText("Filter", substring = false).performClick()
               waitFor("Filter records")
               ui.onNodeWithTag("records.filter.value").performTextReplacement("TEST-000026")
               ui.onNodeWithText("Apply filter").performClick()
               waitFor("1–1 of 1")
               ui.onNodeWithTag("records.row:26").performClick()
               waitForTag("record.details")
               listOf("Order overview", "Customer", "Shipping", "Amounts", "Warehouse", "Notes and audit").forEach {
                  ui.onNodeWithText("Sections (6)").performClick()
                  ui.onNodeWithText(it, substring = false).performClick()
                  ui.onNodeWithTag("record.section:$it").performScrollTo().assertIsDisplayed()
               }
               ui.onNodeWithText("Items", substring = false).performClick()
               waitForTag("record.related.row:testOrderItem:301")
               ui.onNodeWithText("1–10 of 12").assertIsDisplayed()
               ui.onNodeWithTag("record.related.row:testOrderItem:1").assertDoesNotExist()
               ui.onNodeWithText("Next", substring = false).performClick()
               waitForTag("record.related.row:testOrderItem:311")
               ui.onNodeWithTag("record.related.row:testOrderItem:311").performClick()
               waitForTag("record.section:Pricing and handling")
               ui.onNodeWithText("Back to parent record").performClick()
               waitForTag("record.related.row:testOrderItem:311")
               ui.onNodeWithText("History", substring = false).performClick()
               waitForTag("record.related.row:testOrderEvent:76")
               ui.onNodeWithText("1–3 of 3").assertIsDisplayed()
               stage("rich-records-grouped-related-paged")
               stage("rich-records-search-filter-back-preserved")
            }
            ui.onNodeWithText("Take photo").assertDoesNotExist()
            ui.onNodeWithText("Actions", substring = false).performClick()
            waitFor("Add photo")
            ui.onNodeWithText("Add photo", substring = false).performClick()
         } else {
            waitForTag("appHome.rowForProcess:mobileRecordPhotos")
            ui.onNodeWithTag("appHome.rowForProcess:mobileRecordPhotos").performClick()
         }
         waitFor("Take photo")
         ui.onNodeWithText("Take photo").performScrollTo().performClick()
         waitFor(savedMessage)
         if (echoHost) ui.onNodeWithText("Saved photo. Awaiting server review.").assertDoesNotExist()
         assertEquals("Camera result must automatically upload without a second tap", 1, cameraCalls.get())
         assertTrue(directory.listFiles().orEmpty().none { it.name.startsWith("photo-") })
         val savedRows = jsonRequest("/qqq/v1/table/record_files_file/query", JSONObject()).getJSONArray("records")
         val savedFile = (0 until savedRows.length()).map { savedRows.getJSONObject(it).getJSONObject("values") }
            .single { it.optString("deviceId") == device.deviceId && it.optString("sha256") == RecordFilesApi.sha256(bytes) }
         assertEquals(parentTable, savedFile.getString("tableName"))
         assertEquals(parentId, savedFile.getString("recordId"))
         assertEquals("CAPTURE", savedFile.getString("origin"))
         assertTrue(savedFile.getString("originDetail").endsWith("source CAMERA"))
         assertEquals(expectedLifecycle, savedFile.getString("lifecycle"))
         assertEquals(bytes.size.toLong(), savedFile.getLong("sizeBytes"))
         assertTrue(savedFile.getLong("attachmentId") > 0)
         val associations = jsonRequest("/qqq/v1/table/record_files_collection_item/query", JSONObject()).getJSONArray("records")
         // Collection storage is one-based; the upload manifest/receipt positions are zero-based.
         assertTrue((0 until associations.length()).map { associations.getJSONObject(it).getJSONObject("values") }
            .any { it.optString("fileId") == savedFile.getString("id") && it.optString("tableName") == parentTable && it.optString("recordId") == parentId && it.optInt("position") == 1 })
         stage("native-photo-camera-saved")

         ui.onNodeWithText("Choose photo").performScrollTo().performClick()
         waitFor("Photo ready to upload.")
         assertEquals(1, galleryCalls.get())
         ui.onNodeWithText("Upload photo").performScrollTo().performClick()
         waitFor(savedMessage)
         val galleryRows = jsonRequest("/qqq/v1/table/record_files_file/query", JSONObject()).getJSONArray("records")
         val gallerySaved = (0 until galleryRows.length()).map { galleryRows.getJSONObject(it).getJSONObject("values") }
            .single { it.optString("deviceId") == device.deviceId && it.optString("originDetail").endsWith("source GALLERY") }
         assertEquals(parentTable, gallerySaved.getString("tableName"))
         assertEquals(parentId, gallerySaved.getString("recordId"))
         assertEquals(RecordFilesApi.sha256(bytes), gallerySaved.getString("sha256"))
         assertEquals(expectedLifecycle, gallerySaved.getString("lifecycle"))
         assertTrue(gallerySaved.getLong("attachmentId") > 0)
         stage("native-photo-gallery-saved")
         if (fromRecord) {
            ui.onNodeWithText("Done", substring = false).performClick()
            ui.onNodeWithText("Photos", substring = false).performClick()
            waitFor(if (rich) "3 photos" else "2 photos")
            if (rich) {
               ui.onNodeWithText("Preview example 27.jpg").assertDoesNotExist()
               // Seeded, camera and gallery photos are all AVAILABLE, so scope thumbnail and Preview to the newly captured camera row.
               val cameraRow = "record.photo:${savedFile.getString("id")}"
               ui.onNodeWithTag("record.photos.list").performScrollToNode(hasTestTag(cameraRow))
               val cameraThumbnail = hasTestTag("photo.image:256") and hasAnyAncestor(hasTestTag(cameraRow))
               ui.waitUntil(timeoutMillis = 60_000) { runCatching { ui.onNode(cameraThumbnail).fetchSemanticsNode() }.isSuccess }
               ui.onNode(hasText("Preview photo") and hasAnyAncestor(hasTestTag(cameraRow))).performClick()
               waitFor("Photo preview")
               waitForTag("photo.image:1600")
               ui.onNodeWithText("Close preview").performClick()
               stage("native-photo-approved-preview")
            } else ui.onNodeWithText("Preview photo").assertDoesNotExist()
            listOf(savedFile, gallerySaved).forEach { file ->
               val row = "record.photo:${file.getString("id")}"
               ui.onNodeWithTag("record.photos.list").performScrollToNode(hasTestTag(row))
               ui.onNode(hasText(expectedBadge) and hasAnyAncestor(hasTestTag(row))).assertIsDisplayed()
            }
            stage("native-photo-gallery-parent-scoped")
            ui.onNodeWithText("Actions", substring = false).performClick()
            ui.onNodeWithText("Add photo", substring = false).performClick()
         }
         ui.onNodeWithText("Choose photo").performScrollTo().performClick()
         waitFor("Photo ready to upload.")
         assertEquals(2, galleryCalls.get())
         val pending = directory.listFiles().orEmpty().single { it.name.startsWith("photo-") }
         assertTrue(bytes.contentEquals(pending.readBytes()))
         ui.activityRule.scenario.onActivity { OrientationPolicy.LANDSCAPE.select(it) }
         waitFor("Photo ready to upload.")
         ui.activityRule.scenario.recreate()
         waitFor("Photo ready to upload.")
         assertTrue("Rotation retains the same private original", pending.exists())
         if (fromRecord) {
            ui.onNodeWithText("Done", substring = false).assertIsNotEnabled()
            ui.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
             waitFor("Photo in progress")
             ui.onNodeWithText("Return to photo").performClick()
            waitFor("Photo ready to upload.")
         } else {
            ui.onNodeWithTag("topBar.menuIcon").performClick()
            ui.onNodeWithTag("drawer.home").performClick()
            ui.onNodeWithText("Leave process", substring = false).assertIsNotEnabled()
            ui.onNodeWithText("Stay").performClick()
         }
         stage("native-photo-rotation-preserved")

         var sessionId = ""
         waitForServer {
            sessionId = sessionRows().firstOrNull { it.optString("deviceId") == device.deviceId && it.optString("status") == "ACTIVE" }?.optString("id").orEmpty()
            sessionId.isNotBlank()
         }
         val launch = processCall("mobileSessionEnd", "/init", JSONObject())
         val ended = processCall("mobileSessionEnd", "/${launch.getString("processUUID")}/step/input", JSONObject().put("sessionId", sessionId))
         if (ended.optString("nextStep") == "result") processCall("mobileSessionEnd", "/${launch.getString("processUUID")}/step/result", JSONObject())
         waitFor("Log back in")
         waitForServer { !pending.exists() }
         assertTrue("The selected gallery original is never deleted", galleryFile.exists())
         assertEquals("No late callback created a second camera action", 1, cameraCalls.get())
         stage("native-photo-forced-logout-cleaned")
      } finally {
         instrumentation.removeMonitor(monitor)
         galleryFile.delete()
      }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun sessionActivityMirrorsActualScreenAndAdminEndForcesFreshLogin() {
      val context = InstrumentationRegistry.getInstrumentation().targetContext
      check(context.packageName in setOf("io.qrun.mobile.scanner.e2e", "io.qrun.mobile.scanner.qa"))
      val configFile = File(context.filesDir, "e2e.json")
      val config = JSONObject(configFile.readText())
      configFile.delete()
      origin = config.getString("serverUrl").trimEnd('/')
      waitFor("QQQ server URL")
      ui.onNodeWithText("QQQ server URL").performTextReplacement("$origin/")
      ui.onNodeWithText("Connect", substring = false).performClick()
      /*******************************************************************************
       **
       *******************************************************************************/
      fun signIn() {
         waitFor("Username")
         ui.onNodeWithText("Username").performTextReplacement("scanner")
         ui.onNodeWithText("Password").performTextReplacement(config.getString("scannerPassword"))
         ui.onNodeWithText("Password").performImeAction()
         ui.onNode(hasText("Sign in") and hasClickAction()).performScrollTo().performClick()
      }
      signIn()
      waitFor("An administrator must approve this device before you can run mobile apps.")
      val device = DeviceCredentials(context, "$origin/")
      adminCookie = loginAdmin(config.getString("adminPassword"))
      runAdmin("mobileDeviceApprove", device.deviceId)
      ui.onNodeWithText("Check approval").performScrollTo().assertIsDisplayed().performClick()
      try {
         waitForTag("appHome.rowForApp:mobileScanApp")
      } catch (error: androidx.compose.ui.test.ComposeTimeoutException) {
         val safeTree = ui.onRoot().printToString().replace(config.getString("scannerPassword"), "<REDACTED>")
            .replace(config.getString("adminPassword"), "<REDACTED>")
         throw AssertionError("Approved app catalog did not load: $safeTree", error)
      }
      ui.onNodeWithTag("appHome.rowForApp:mobileScanApp").performClick()
      waitForTag("appHome.rowForProcess:mobileScanCapture")
      ui.onNodeWithTag("appHome.rowForProcess:mobileScanCapture").performClick()
      waitFor("Manual barcode")
      ui.onNodeWithText("Manual barcode").performTextReplacement("orientation-draft")
      ui.onNodeWithText("Manual barcode").assertTextContains("orientation-draft")
      ui.activityRule.scenario.onActivity { OrientationPolicy.LANDSCAPE.select(it) }
      ui.waitUntil(timeoutMillis = 15000)
      {
         var landscape = false
         ui.activityRule.scenario.onActivity {
            landscape = it.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE &&
               it.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
         }
         landscape
      }
      waitFor("Manual barcode")
      ui.onNodeWithText("Manual barcode").assertTextContains("orientation-draft")
      ui.activityRule.scenario.recreate()
      waitFor("Manual barcode")
      ui.onNodeWithText("Manual barcode").assertTextContains("orientation-draft")
      stage("session-process-open")
      var row = JSONObject()
      waitForServer {
         row = sessionRows().firstOrNull { it.optString("deviceId") == device.deviceId && it.optString("status") == "ACTIVE" } ?: JSONObject()
         row.optString("processName") == "mobileScanCapture" && row.optString("stepName").isNotBlank()
      }
      val sessionId = row.getString("id")
      assertEquals("mobileScanApp", row.getString("appName"))
      assertTrue(row.getBoolean("foreground"))
      val deviceRows = jsonRequest("/qqq/v1/table/mobileDevice/query", JSONObject()).getJSONArray("records")
      val deviceRow = (0 until deviceRows.length()).map { deviceRows.getJSONObject(it).getJSONObject("values") }.single { it.getString("id") == device.deviceId }
      assertEquals("PERMISSION_DENIED", deviceRow.getString("locationStatus"))
      /*******************************************************************************
       **
       *******************************************************************************/
      fun screenWidget() = jsonRequest("/qqq/v1/widget/mobileSessionScreen", JSONObject().put("id", sessionId))
      try {
         waitForServer {
            row = sessionRows().single { it.getString("id") == sessionId }
            row.optBoolean("screenAvailable")
         }
      } catch (error: AssertionError) {
         var windowState = "unavailable"
         ui.activityRule.scenario.onActivity { activity ->
            val power = activity.getSystemService(android.os.PowerManager::class.java)
            val keyguard = activity.getSystemService(android.app.KeyguardManager::class.java)
            windowState = "interactive=${power.isInteractive}, keyguard=${keyguard.isKeyguardLocked}, deviceLocked=${keyguard.isDeviceLocked}, activityFocus=${activity.hasWindowFocus()}, decorFocus=${activity.window.decorView.hasWindowFocus()}, lifecycle=${activity.lifecycle.currentState}"
         }
         throw AssertionError("Capture eligibility did not converge: foreground=${row.optBoolean("foreground")}, reason=${row.optString("captureBlockedReason")}, $windowState", error)
      }
      // Match the ordinary admin widget's polling lease, including transient screen changes.
      var widgetStatus = "unobserved"
      try {
         waitForServer {
            val widget = screenWidget()
            val blocks = widget.optJSONArray("blocks") ?: widget.optJSONArray("subBlocks")
            widgetStatus = blocks?.optJSONObject(0)?.optJSONObject("values")?.optString("text").orEmpty()
            ui.onAllNodesWithText("Screen being viewed by an administrator").fetchSemanticsNodes().isNotEmpty()
         }
      } catch (error: AssertionError) {
         val latest = sessionRows().single { it.getString("id") == sessionId }
         throw AssertionError("Viewing demand did not converge: available=${latest.optBoolean("screenAvailable")}, reason=${latest.optString("captureBlockedReason")}, lastSeenAt=${latest.optString("lastSeenAt")}, deviceTime=${java.time.Instant.now()}, widgetStatus=$widgetStatus", error)
      }
      var frame = ""
      waitForServer {
         frame = findImage(screenWidget()).orEmpty()
         frame.startsWith("data:image/jpeg;base64,")
      }
      val bytes = Base64.decode(frame.substringAfter(','), Base64.DEFAULT)
      val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
      assertTrue("Admin receives actual decodable app pixels", bitmap != null && bitmap.width <= 640 && bitmap.height <= 1280)
      bitmap?.recycle()
      stage("session-mirror-frame")
      ui.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
      waitForServer { screenWidget().toString().contains("APP_BACKGROUND") && findImage(screenWidget()) == null }
      ui.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
      waitForServer {
         screenWidget()
         ui.onAllNodesWithText("Screen being viewed by an administrator").fetchSemanticsNodes().isNotEmpty()
      }
      // Scanner users have no in-app control to pause or disable administrator viewing.
      assertTrue("No in-app Stop viewing control", ui.onAllNodesWithText("Stop viewing").fetchSemanticsNodes().isEmpty())
      assertTrue("No in-app Allow viewing control", ui.onAllNodesWithText("Allow viewing").fetchSemanticsNodes().isEmpty())
      ui.onNodeWithText("Screen being viewed by an administrator").assertHasNoClickAction()
      waitForServer { findImage(screenWidget()).orEmpty().startsWith("data:image/jpeg;base64,") }
      val launch = processCall("mobileSessionEnd", "/init", JSONObject())
      val applied = processCall("mobileSessionEnd", "/${launch.getString("processUUID")}/step/input", JSONObject().put("sessionId", sessionId))
      if (applied.optString("nextStep") == "result") processCall("mobileSessionEnd", "/${launch.getString("processUUID")}/step/result", JSONObject())
      waitFor("Log back in")
      assertEquals("ENDED", sessionRows().single { it.getString("id") == sessionId }.getString("status"))
      // The label is rendered during asynchronous logout while its button is disabled.
      // Clicking that disabled control is a no-op, not a request for a fresh login.
      ui.waitUntil(timeoutMillis = 15_000)
      {
         val signedOut = ui.onAllNodesWithText("Signed out.").fetchSemanticsNodes().isNotEmpty() ||
            ui.onAllNodesWithText("Signed out on this device.", substring = true).fetchSemanticsNodes().isNotEmpty()
         signedOut && ui.onAllNodes(hasText("Log back in") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
      }
      stage("session-login-action-ready")
      ui.onNodeWithText("Log back in").assertIsEnabled().performClick()
      signIn()
      waitForTag("appHome.rowForApp:mobileScanApp")
      waitForServer { sessionRows().any { it.optString("deviceId") == device.deviceId && it.optString("status") == "ACTIVE" && it.optString("id") != sessionId } }
      stage("session-fresh-login")
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   private fun sessionRows(): List<JSONObject> {
      return SessionAcceptanceClient(origin, adminCookie, client).sessions()
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   private fun waitForServer(condition: () -> Boolean) {
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(40)
      var lastClockAdvance = System.nanoTime()
      while (true) {
         // LaunchedEffect timers use this clock; keep it aligned with real network/polling time.
         val now = System.nanoTime()
         ui.mainClock.advanceTimeBy(TimeUnit.NANOSECONDS.toMillis(now - lastClockAdvance).coerceAtLeast(16))
         lastClockAdvance = now
         if (condition()) return
         assertTrue("Session server state must converge", System.nanoTime() < deadline)
         Thread.sleep(250)
      }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   private fun findImage(value: Any?): String? = SessionAcceptanceClient.image(value)

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun adminFormsApproveRevokeAndCancel() {
      stage("admin-start")
      val context = InstrumentationRegistry.getInstrumentation().targetContext
      check(context.packageName == "io.qrun.mobile.scanner.e2e")
      val configFile = File(context.filesDir, "e2e.json")
      val config = JSONObject(configFile.readText())
      configFile.delete()
      origin = config.getString("serverUrl").trimEnd('/')
      waitFor("QQQ server URL")
      ui.onNodeWithText("QQQ server URL").performTextReplacement("$origin/")
      ui.onNodeWithText("Connect", substring = false).performClick()
      try {
         waitFor("Username")
      } catch (error: androidx.compose.ui.test.ComposeTimeoutException) {
         // This is before any credentials are entered; retain the actual startup error for diagnosis.
         throw AssertionError("Admin login screen did not load: ${ui.onRoot().printToString()}", error)
      }
      stage("admin-login")
      ui.onNodeWithText("Username").performTextReplacement("admin")
      ui.onNodeWithText("Password").performTextReplacement(config.getString("adminPassword"))
      ui.onNodeWithText("Password").performImeAction()
      ui.onNode(hasText("Sign in") and hasClickAction()).performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
      waitFor("An administrator must approve this device before you can run mobile apps.")
      stage("admin-enrolled")
      adminCookie = loginAdmin(config.getString("adminPassword"))
      runAdmin("mobileDeviceApprove", DeviceCredentials(context, "$origin/").deviceId)
      val targetDevice = jsonRequest(
         "/qqq/v1/qbit/mobile-scanners/devices/enroll", JSONObject()
            .put("installationId", UUID.randomUUID().toString()).put("displayName", "Native form fixture")
            .put("manufacturer", "Test").put("model", "Emulator").put("osVersion", "test").put("appVersion", "test"), expectedStatus = 201
      )
      val target = targetDevice.getString("deviceId")
      ui.onNodeWithText("Check approval").performScrollTo().assertIsDisplayed().performClick()
      waitForTag("appHome.rowForApp:mobileScannerAdmin")
      ui.onNodeWithTag("appHome.rowForApp:mobileScannerAdmin").performClick()
      stage("admin-app-open")
      for ((process, status, result) in listOf(
         Triple("mobileDeviceApprove", "APPROVED", "1 device approved."),
         Triple("mobileDeviceRevoke", "REVOKED", "1 device revoked.")
      )) {
         stage("admin-open-$process")
         waitForTag("appHome.rowForProcess:$process")
         ui.onNodeWithTag("appHome.rowForProcess:$process").performClick()
         waitFor("Device IDs", substring = true)
         ui.onNodeWithText("Device IDs", substring = true).performTextReplacement(target)
         ui.onNodeWithText("Next", substring = false).performClick()
         stage("admin-submitted-$process")
         waitFor(result, substring = true)
         assertEquals("Native form must persist its submitted target", status, deviceStatus(target))
         ui.onNodeWithText("Next", substring = false).performClick()
         stage("admin-finish-submitted-$process")
         waitFor("Close")
         stage("admin-complete-$process")
         ui.onNodeWithText("Close").performClick()
         stage("admin-closed-$process")
      }
      // A separate core login represents another handheld; end it through the native metadata form.
      runAdmin("mobileDeviceApprove", target)
      val fixtureCookie = loginAdmin(config.getString("scannerPassword"), "scanner")
      val heartbeat = executeJson(
         Request.Builder().url("$origin/qqq/v1/qbit/mobile-scanners/sessions/heartbeat")
            .header("Cookie", fixtureCookie).header("X-QQQ-Device-Id", target)
            .header("X-QQQ-Device-Token", targetDevice.getString("deviceToken"))
            .header("X-QQQ-Client", "android/qqq-mobile")
            .post(
               JSONObject().put("foreground", true).put("screenAvailable", false).put("captureBlockedReason", "SCREEN_LOADING")
                  .toString().toRequestBody("application/json".toMediaType())
            ).build()
      )
      val targetSession = heartbeat.getString("sessionId")
      waitForTag("appHome.rowForProcess:mobileSessionEnd")
      ui.onNodeWithTag("appHome.rowForProcess:mobileSessionEnd").performClick()
      waitFor("Session IDs", substring = true)
      ui.onNodeWithText("Session IDs", substring = true).performScrollTo().performTextReplacement(targetSession)
      ui.onNodeWithText("Next", substring = false).performClick()
      waitFor("1 session ended.", substring = true)
      assertEquals("ENDED", sessionRows().single { it.getString("id") == targetSession }.getString("status"))
      ui.onNodeWithText("Next", substring = false).performClick()
      waitFor("Close")
      ui.onNodeWithText("Close").performClick()
      runAdmin("mobileDeviceRevoke", target)
      stage("admin-ended-session")
      waitForTag("appHome.rowForProcess:mobileDeviceApprove")
      ui.onNodeWithTag("appHome.rowForProcess:mobileDeviceApprove").performClick()
      waitFor("Device IDs", substring = true)
      ui.onNodeWithText("Device IDs", substring = true).performTextReplacement(target)
      ui.onNodeWithText("Device IDs", substring = true).performImeAction()
      ui.onNodeWithText("Cancel", substring = false).performClick()
      ui.onNodeWithText("Leave process", substring = false).performClick()
      stage("admin-cancel-submitted")
      waitForTag("appHome.rowForProcess:mobileDeviceApprove")
      assertEquals("Cancel must not execute a populated form", "REVOKED", deviceStatus(target))
      stage("admin-finished")
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   private fun stage(name: String) {
      InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply { putString("qqqStage", name) })
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   private fun deviceStatus(id: String): String {
      val rows = jsonRequest("/qqq/v1/table/mobileDevice/query", JSONObject()).getJSONArray("records")
      return (0 until rows.length()).map { rows.getJSONObject(it).getJSONObject("values") }
         .single { it.getString("id") == id }.getString("status")
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun incompatibleServerStopsBeforeLogin() {
      val context = InstrumentationRegistry.getInstrumentation().targetContext
      check(context.packageName == "io.qrun.mobile.scanner.e2e")
      val configFile = File(context.filesDir, "e2e.json")
      val config = JSONObject(configFile.readText())
      configFile.delete()
      waitFor("QQQ server URL")
      ui.onNodeWithText("QQQ server URL").performTextReplacement(config.getString("serverUrl"))
      ui.onNodeWithText("Connect", substring = false).performClick()
      waitFor(config.getString("expectedError"), substring = true)
      assertTrue("Incompatible servers must not display a login form", ui.onAllNodesWithText("Username").fetchSemanticsNodes().isEmpty())
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun loginEnrollApproveCaptureAndRevoke() {
      val context = InstrumentationRegistry.getInstrumentation().targetContext
      check(context.packageName == "io.qrun.mobile.scanner.e2e") { "E2E must run in its isolated application" }
      val configFile = File(context.filesDir, "e2e.json")
      val config = JSONObject(configFile.readText())
      configFile.delete()
      origin = config.getString("serverUrl").trimEnd('/')
      waitFor("QQQ server URL")
      ui.onNodeWithText("QQQ server URL").performTextReplacement("$origin/")
      ui.onNodeWithText("Connect", substring = false).performClick()
      waitFor("Username")
      ui.onNodeWithText("Username").performTextReplacement("scanner")
      ui.onNodeWithText("Password").performTextReplacement(config.getString("scannerPassword"))
      ui.onNodeWithText("Password").performImeAction()
      stage("scanner-login-ready")
      ui.onNode(hasText("Sign in") and hasClickAction()).performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
      stage("scanner-login-clicked")
      waitFor("An administrator must approve this device before you can run mobile apps.")
      val device = DeviceCredentials(context, "$origin/")
      assertTrue("Enrollment must persist a device identity", device.deviceId.isNotEmpty())
      adminCookie = loginAdmin(config.getString("adminPassword"))
      runAdmin("mobileDeviceApprove", device.deviceId)
      ui.onNodeWithText("Check approval").performScrollTo().assertIsDisplayed().performClick()
      waitForTag("appHome.rowForApp:mobileScanApp")
      ui.onNodeWithTag("appHome.rowForApp:mobileScanApp").performClick()
      waitForTag("appHome.rowForProcess:mobileScanCapture")
      ui.onNodeWithTag("appHome.rowForProcess:mobileScanCapture").performClick()
      waitFor("Manual barcode")
      val barcode = "000-E2E-${UUID.randomUUID()}"
      ui.onNodeWithText("Manual barcode").performScrollTo().performTextReplacement(barcode)
      ui.onNodeWithText("Manual barcode").performImeAction()
      ui.onNodeWithText("Submit barcode").performScrollTo().assertIsDisplayed().performClick()
      waitFor("Saved scan ", substring = true)
      val records = jsonRequest("/qqq/v1/table/mobileScan/query", JSONObject()).getJSONArray("records")
      val scans = (0 until records.length()).map { records.getJSONObject(it).getJSONObject("values") }
         .filter { it.optString("barcode") == barcode }
      assertEquals("One UI submission must create one durable scan", 1, scans.size)
      assertEquals(device.deviceId, scans.single().getString("deviceId"))
      assertEquals("scanner", scans.single().getString("userId"))
      assertEquals("manual", scans.single().getString("scanSource"))
      assertTrue(scans.single().has("receivedAt"))

      // Scanner-sourced fixture event with exact raw bytes (NUL, high bytes, embedded ]Q1) and an explicit AIM
      // identifier, delivered through the disposable e2e driver. Text, type and bytes are fixed and non-sensitive.
      val scannedBarcode = "000-NATIVE-RAW-0123"
      val scannedSymbology = "LABEL-TYPE-QRCODE"
      val rawBytes = byteArrayOf(0x00, 0xFF.toByte(), 0x80.toByte(), ']'.code.toByte(), 'Q'.code.toByte(), '1'.code.toByte(), 'A'.code.toByte(), 0x00, 0xFE.toByte())
      // The same >=1.5 negotiation rule as production, read independently from public discovery of this scenario's server.
      val fidelityNegotiated = mobileScannersAtLeast15()
      fun scanRows() = jsonRequest("/qqq/v1/table/mobileScan/query", JSONObject()).getJSONArray("records")
      val beforeScan = scanRows().length()
      waitFor("Scanner ready. Press the handheld trigger.")
      context.sendBroadcast(Intent("io.qrun.mobile.scanner.SCAN").setPackage(context.packageName)
         .addCategory(Intent.CATEGORY_DEFAULT).putExtra("com.symbol.datawedge.data_string", scannedBarcode)
         .putExtra("com.symbol.datawedge.label_type", scannedSymbology)
         .putExtra("io.qrun.mobile.scanner.e2e.raw_data", rawBytes)
         .putExtra("io.qrun.mobile.scanner.e2e.aim_identifier", "]Q1"))
      var scanned: JSONObject? = null
      waitForServer {
         val rows = scanRows()
         scanned = (0 until rows.length()).map { rows.getJSONObject(it).getJSONObject("values") }
            .singleOrNull { it.optString("barcode") == scannedBarcode }
         scanned != null
      }
      val persisted = requireNotNull(scanned)
      // The acknowledgment names this event's id, so it cannot be the earlier manual acknowledgment.
      waitFor("Saved scan ${persisted.getString("id")}", substring = true)
      assertEquals("e2e-fixture", persisted.getString("scanSource"))
      assertEquals(device.deviceId, persisted.getString("deviceId"))
      assertEquals(scannedBarcode, persisted.getString("barcode"))
      assertEquals(scannedSymbology, persisted.getString("symbology"))
      if (fidelityNegotiated) {
         assertTrue("Persisted raw bytes must equal the exact bytes sent, including NUL and high bytes",
            rawBytes.contentEquals(Base64.decode(persisted.getString("rawDataBase64"), Base64.NO_WRAP)))
         assertEquals("]Q1", persisted.getString("aimIdentifier"))
      } else {
         assertTrue("A server without scan fidelity must receive and persist legacy fields only",
            persisted.isNull("rawDataBase64") && persisted.isNull("aimIdentifier"))
      }
      assertEquals("One scanner event must create exactly one durable scan", beforeScan + 1, scanRows().length())
      stage(if (fidelityNegotiated) "native-raw-scan-persisted" else "native-legacy-scan-persisted")

      val beforeRevoke = scanRows()
      runAdmin("mobileDeviceRevoke", device.deviceId)
      // The active heartbeat revokes the open UI as well as protected process submissions.
      waitFor("Log back in")
      val afterRevoke = scanRows()
      assertEquals("Revoked devices cannot save through an already-open process", beforeRevoke.length(), afterRevoke.length())
   }

   /*******************************************************************************
    ** Public, credential-free discovery: mobile-scanners at its fixed path with version >=1.5.0 and <2.
    *******************************************************************************/
   private fun mobileScannersAtLeast15(): Boolean {
      val discovery = executeJson(Request.Builder().url("$origin/qqq/v1/version").get().build())
      val extensions = discovery.optJSONArray("extensions") ?: return false
      val extension = (0 until extensions.length()).map { extensions.getJSONObject(it) }.singleOrNull { it.optString("name") == "mobile-scanners" } ?: return false
      if (extension.optString("pathPrefix") != "/qbit/mobile-scanners") return false
      val parts = Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)").matchEntire(extension.optString("version"))?.groupValues ?: return false
      return parts[1] == "1" && (parts[2].toIntOrNull() ?: return false) >= 5
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   private fun waitFor(text: String, substring: Boolean = false) {
      ui.waitUntil(timeoutMillis = 60_000) { ui.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   private fun waitForTag(tag: String) {
      ui.waitUntil(timeoutMillis = 60_000) { runCatching { ui.onNodeWithTag(tag).fetchSemanticsNode() }.isSuccess }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   private fun loginAdmin(password: String, account: String = "admin"): String {
      return SessionAcceptanceClient.login(origin, password, client, account)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   private fun runAdmin(process: String, deviceId: String) {
      val launch = processCall(process, "/init", JSONObject())
      val processId = launch.getString("processUUID")
      val applied = processCall(process, "/$processId/step/input", JSONObject().put("deviceId", deviceId))
      if (applied.optString("nextStep") == "result") processCall(process, "/$processId/step/result", JSONObject())
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   private fun processCall(process: String, suffix: String, values: JSONObject): JSONObject {
      return SessionAcceptanceClient(origin, adminCookie, client).process(process, suffix, values)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   private fun jsonRequest(path: String, body: JSONObject, expectedStatus: Int = 200): JSONObject =
      SessionAcceptanceClient(origin, adminCookie, client).json(path, body, expectedStatus)

   /*******************************************************************************
    **
    *******************************************************************************/
   private fun executeJson(request: Request, expectedStatus: Int = 200): JSONObject = client.newCall(request).execute().use { response ->
      assertEquals("Test server request must succeed", expectedStatus, response.code)
      JSONObject(response.body!!.string())
   }
}
