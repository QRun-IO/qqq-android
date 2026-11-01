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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
    ** Real host/native activity-result boundaries and real upload APIs, using synthetic images only.
    *******************************************************************************/
   @Test
   fun cameraAutoUploadsAndPendingGallerySurvivesRotationThenCleansOnRemoteLogout() {
      val instrumentation = InstrumentationRegistry.getInstrumentation()
      val context = instrumentation.targetContext
      check(context.packageName == "io.qrun.mobile.scanner.e2e")
      val configFile = File(context.filesDir, "e2e.json")
      val config = JSONObject(configFile.readText())
      configFile.delete()
      origin = config.getString("serverUrl").trimEnd('/')
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
         waitForTag("appHome.rowForApp:mobileScanApp")
         ui.onNodeWithTag("appHome.rowForApp:mobileScanApp").performClick()
         waitForTag("appHome.rowForProcess:mobileRecordPhotos")
         ui.onNodeWithTag("appHome.rowForProcess:mobileRecordPhotos").performClick()
         waitFor("Take photo")
         ui.onNodeWithText("Take photo").performScrollTo().performClick()
         waitFor("Saved photo. Awaiting server review.")
         assertEquals("Camera result must automatically upload without a second tap", 1, cameraCalls.get())
         assertTrue(directory.listFiles().orEmpty().none { it.name.startsWith("photo-") })
         val savedRows = jsonRequest("/qqq/v1/table/record_files_file/query", JSONObject()).getJSONArray("records")
         val savedFile = (0 until savedRows.length()).map { savedRows.getJSONObject(it).getJSONObject("values") }
            .single { it.optString("deviceId") == device.deviceId && it.optString("sha256") == RecordFilesApi.sha256(bytes) }
         assertEquals("document", savedFile.getString("tableName"))
         assertEquals("1", savedFile.getString("recordId"))
         assertEquals("CAPTURE", savedFile.getString("origin"))
         assertTrue(savedFile.getString("originDetail").endsWith("source CAMERA"))
         assertEquals("QUARANTINED", savedFile.getString("lifecycle"))
         assertEquals(bytes.size.toLong(), savedFile.getLong("sizeBytes"))
         assertTrue(savedFile.getLong("attachmentId") > 0)
         val associations = jsonRequest("/qqq/v1/table/record_files_collection_item/query", JSONObject()).getJSONArray("records")
         // Collection storage is one-based; the upload manifest/receipt positions are zero-based.
         assertTrue((0 until associations.length()).map { associations.getJSONObject(it).getJSONObject("values") }
            .any { it.optString("fileId") == savedFile.getString("id") && it.optString("tableName") == "document" && it.optString("recordId") == "1" && it.optInt("position") == 1 })
         stage("native-photo-camera-saved")

         ui.onNodeWithText("Choose photo").performScrollTo().performClick()
         waitFor("Photo ready to upload.")
         assertEquals(1, galleryCalls.get())
         val pending = directory.listFiles().orEmpty().single { it.name.startsWith("photo-") }
         assertTrue(bytes.contentEquals(pending.readBytes()))
         ui.activityRule.scenario.onActivity { OrientationPolicy.LANDSCAPE.select(it) }
         waitFor("Photo ready to upload.")
         ui.activityRule.scenario.recreate()
         waitFor("Photo ready to upload.")
         assertTrue("Rotation retains the same private original", pending.exists())
         ui.onNodeWithTag("topBar.menuIcon").performClick()
         ui.onNodeWithTag("drawer.home").performClick()
         ui.onNodeWithText("Leave process", substring = false).assertIsNotEnabled()
         ui.onNodeWithText("Stay").performClick()
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
      ui.onNodeWithText("Stop viewing").performClick()
      waitFor("Screen viewing paused")
      waitForServer { screenWidget().toString().contains("OPERATOR_PAUSED") && findImage(screenWidget()) == null }
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
      runAdmin("mobileDeviceRevoke", device.deviceId)
      // The active heartbeat revokes the open UI as well as protected process submissions.
      waitFor("Log back in")
      val afterRevoke = jsonRequest("/qqq/v1/table/mobileScan/query", JSONObject()).getJSONArray("records")
      assertEquals("Revoked devices cannot save through an already-open process", records.length(), afterRevoke.length())
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
