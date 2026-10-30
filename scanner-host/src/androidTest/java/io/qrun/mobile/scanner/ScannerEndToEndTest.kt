/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import android.os.Bundle

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real host UI and real QQQ server. No repository mocks or production app data. */
@RunWith(AndroidJUnit4::class)
class ScannerEndToEndTest
{
   @get:Rule
   val ui = createAndroidComposeRule<ScannerActivity>()
   private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
   private lateinit var origin: String
   private lateinit var adminCookie: String

   @Test
   fun adminFormsApproveRevokeAndCancel()
   {
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
      try { waitFor("Username") }
      catch(error: androidx.compose.ui.test.ComposeTimeoutException)
      {
         // This is before any credentials are entered; retain the actual startup error for diagnosis.
         throw AssertionError("Admin login screen did not load: ${ui.onRoot().printToString()}", error)
      }
      stage("admin-login")
      ui.onNodeWithText("Username").performTextReplacement("admin")
      ui.onNodeWithText("Password").performTextReplacement(config.getString("adminPassword"))
      ui.onNode(hasText("Sign in") and hasClickAction()).performClick()
      waitFor("An administrator must approve this device before you can run mobile apps.")
      stage("admin-enrolled")
      adminCookie = loginAdmin(config.getString("adminPassword"))
      runAdmin("mobileDeviceApprove", DeviceCredentials(context, "$origin/").deviceId)
      val target = jsonRequest("/qqq/v1/qbit/mobile-scanners/devices/enroll", JSONObject()
         .put("installationId", UUID.randomUUID().toString()).put("displayName", "Native form fixture")
         .put("manufacturer", "Test").put("model", "Emulator").put("osVersion", "test").put("appVersion", "test"), expectedStatus = 201)
         .getString("deviceId")
      ui.onNodeWithText("Check approval").performClick()
      waitForTag("appHome.rowForApp:mobileScannerAdmin")
      ui.onNodeWithTag("appHome.rowForApp:mobileScannerAdmin").performClick()
      stage("admin-app-open")
      for ((process, status, result) in listOf(
         Triple("mobileDeviceApprove", "APPROVED", "1 device approved."),
         Triple("mobileDeviceRevoke", "REVOKED", "1 device revoked.")))
      {
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
      waitForTag("appHome.rowForProcess:mobileDeviceApprove")
      ui.onNodeWithTag("appHome.rowForProcess:mobileDeviceApprove").performClick()
      waitFor("Device IDs", substring = true)
      ui.onNodeWithText("Device IDs", substring = true).performTextReplacement(target)
      ui.onNodeWithText("Cancel", substring = false).performClick()
      stage("admin-cancel-submitted")
      waitForTag("appHome.rowForProcess:mobileDeviceApprove")
      assertEquals("Cancel must not execute a populated form", "REVOKED", deviceStatus(target))
      stage("admin-finished")
   }

   private fun stage(name: String)
   {
      InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply { putString("qqqStage", name) })
   }

   private fun deviceStatus(id: String): String
   {
      val rows = jsonRequest("/qqq/v1/table/mobileDevice/query", JSONObject()).getJSONArray("records")
      return (0 until rows.length()).map { rows.getJSONObject(it).getJSONObject("values") }
         .single { it.getString("id") == id }.getString("status")
   }

   @Test
   fun incompatibleServerStopsBeforeLogin()
   {
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

   @Test
   fun loginEnrollApproveCaptureAndRevoke()
   {
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
      ui.onNode(hasText("Sign in") and hasClickAction()).performClick()
      waitFor("An administrator must approve this device before you can run mobile apps.")
      val device = DeviceCredentials(context, "$origin/")
      assertTrue("Enrollment must persist a device identity", device.deviceId.isNotEmpty())
      adminCookie = loginAdmin(config.getString("adminPassword"))
      runAdmin("mobileDeviceApprove", device.deviceId)
      ui.onNodeWithText("Check approval").performClick()
      waitForTag("appHome.rowForApp:mobileScanApp")
      ui.onNodeWithTag("appHome.rowForApp:mobileScanApp").performClick()
      waitForTag("appHome.rowForProcess:mobileScanCapture")
      ui.onNodeWithTag("appHome.rowForProcess:mobileScanCapture").performClick()
      waitFor("Manual barcode")
      val barcode = "000-E2E-${UUID.randomUUID()}"
      ui.onNodeWithText("Manual barcode").performTextReplacement(barcode)
      ui.onNodeWithText("Submit barcode").performClick()
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
      ui.onNodeWithText("Manual barcode").performTextReplacement("REVOKED-E2E-${UUID.randomUUID()}")
      ui.onNodeWithText("Submit barcode").performClick()
      waitFor("Retry", substring = true)
      val afterRevoke = jsonRequest("/qqq/v1/table/mobileScan/query", JSONObject()).getJSONArray("records")
      assertEquals("Revoked devices cannot save through an already-open process", records.length(), afterRevoke.length())
   }

   private fun waitFor(text: String, substring: Boolean = false)
   {
      ui.waitUntil(timeoutMillis = 60_000) { ui.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
   }

   private fun waitForTag(tag: String)
   {
      ui.waitUntil(timeoutMillis = 60_000) { runCatching { ui.onNodeWithTag(tag).fetchSemanticsNode() }.isSuccess }
   }

   private fun loginAdmin(password: String): String
   {
      val request = Request.Builder().url("$origin/qqq/v1/manageSession")
         .header("Authorization", Credentials.basic("admin", password))
         .post("{}".toRequestBody("application/json".toMediaType())).build()
      return client.newCall(request).execute().use { response ->
         assertEquals("Admin setup login must succeed", 200, response.code)
         response.headers("Set-Cookie").first { it.startsWith("sessionUUID=") }.substringBefore(';')
      }
   }

   private fun runAdmin(process: String, deviceId: String)
   {
      val launch = processCall(process, "/init", JSONObject())
      val processId = launch.getString("processUUID")
      val applied = processCall(process, "/$processId/step/input", JSONObject().put("deviceId", deviceId))
      if(applied.optString("nextStep") == "result") processCall(process, "/$processId/step/result", JSONObject())
   }

   private fun processCall(process: String, suffix: String, values: JSONObject): JSONObject
   {
      val request = Request.Builder().url("$origin/qqq/v1/processes/$process$suffix").header("Cookie", adminCookie)
         .post(MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("values", values.toString())
            .addFormDataPart("stepTimeoutMillis", "10000").build()).build()
      var result = executeJson(request)
      val processId = result.optString("processUUID")
      val jobId = result.optString("jobUUID")
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
      while(result.optString("type") in listOf("JOB_STARTED", "RUNNING"))
      {
         assertTrue("Process must finish before the deadline", System.nanoTime() < deadline)
         Thread.sleep(50)
         result = executeJson(Request.Builder().url("$origin/qqq/v1/processes/$process/$processId/status/$jobId").header("Cookie", adminCookie).build())
      }
      assertTrue("Administrative process must not fail", !result.has("error"))
      return result
   }

   private fun jsonRequest(path: String, body: JSONObject, expectedStatus: Int = 200): JSONObject = executeJson(Request.Builder().url("$origin$path")
      .header("Cookie", adminCookie).post(body.toString().toRequestBody("application/json".toMediaType())).build(), expectedStatus)

   private fun executeJson(request: Request, expectedStatus: Int = 200): JSONObject = client.newCall(request).execute().use { response ->
      assertEquals("Test server request must succeed", expectedStatus, response.code)
      JSONObject(response.body!!.string())
   }
}
