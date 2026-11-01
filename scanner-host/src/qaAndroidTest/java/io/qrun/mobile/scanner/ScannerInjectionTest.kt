/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PowerManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real production scanner factory + separate ordinary UID. Only the disposable .qa host is permitted. */
@RunWith(AndroidJUnit4::class)
class ScannerInjectionTest
{
   @get:Rule val ui = createAndroidComposeRule<ScannerActivity>()
   private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
   private lateinit var origin: String
   private lateinit var adminCookie: String
   private val probePackage = "io.qrun.mobile.scanner.probe"

   @Test fun unrelatedUidCannotSubmitAProductionScan()
   {
      val instrumentation = InstrumentationRegistry.getInstrumentation()
      val context = instrumentation.targetContext
      check(context.packageName == "io.qrun.mobile.scanner.qa" && BuildConfig.BUILD_TYPE == "qa")
      assertEquals("QA must compile the production factory", "zebra-emdk", HARDWARE_SCAN_SOURCE)
      check(android.os.Build.VERSION.SDK_INT >= 28)
      val packages = context.packageManager
      val probe = packages.getPackageInfo(probePackage, PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_PERMISSIONS)
      val target = packages.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
      val probeUid = requireNotNull(probe.applicationInfo).uid
      assertTrue("Probe must have an ordinary unrelated UID", probeUid >= 10000 && probeUid != context.applicationInfo.uid)
      assertTrue("Probe may not hold target or network permissions", probe.requestedPermissions.isNullOrEmpty())
      fun signers(info: android.content.pm.PackageInfo) = requireNotNull(info.signingInfo).apkContentsSigners
         .map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).toList() }.toSet()
      assertTrue("Root must re-sign the probe with a distinct throwaway test key", signers(probe).intersect(signers(target)).isEmpty())

      val configFile = File(context.filesDir, "qa-injection.json")
      val config = JSONObject(configFile.readText())
      configFile.delete()
      val expectedBarcode = if(config.has("expectedBarcode"))
      {
         val value = config.get("expectedBarcode")
         check(value is String && value.length in 1..4096 && value.all { it.code in 32..126 })
         value
      }
      else null
      val server = URI(config.getString("serverUrl"))
      check(server.scheme == "http" && server.host == "127.0.0.1" && server.port > 0 &&
         server.userInfo == null && server.query == null && server.fragment == null && server.path == "/")
      origin = server.toString().trimEnd('/')
      waitFor("QQQ server URL")
      ui.onNodeWithText("QQQ server URL").performTextReplacement("$origin/")
      ui.onNodeWithText("Connect", substring = false).performClick()
      waitFor("Username")
      ui.onNodeWithText("Username").performTextReplacement("scanner")
      ui.onNodeWithText("Password").performTextReplacement(config.getString("scannerPassword"))
      ui.onNodeWithText("Password").performImeAction()
      ui.onNode(hasText("Sign in") and hasClickAction()).performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
      waitFor("An administrator must approve this device before you can run mobile apps.")
      val device = DeviceCredentials(context, "$origin/")
      adminCookie = client.newCall(Request.Builder().url("$origin/qqq/v1/manageSession")
         .header("Authorization", Credentials.basic("admin", config.getString("adminPassword")))
         .post("{}".toRequestBody("application/json".toMediaType())).build()).execute().use {
         assertEquals(200, it.code)
         it.headers("Set-Cookie").first { cookie -> cookie.startsWith("sessionUUID=") }.substringBefore(';')
      }
      val launch = process("mobileDeviceApprove", "/init", JSONObject())
      val id = launch.getString("processUUID")
      val applied = process("mobileDeviceApprove", "/$id/step/input", JSONObject().put("deviceId", device.deviceId))
      if(applied.optString("nextStep") == "result") process("mobileDeviceApprove", "/$id/step/result", JSONObject())
      ui.onNodeWithText("Check approval").performScrollTo().performClick()
      waitForTag("appHome.rowForApp:mobileScanApp")
      ui.onNodeWithTag("appHome.rowForApp:mobileScanApp").performClick()
      waitForTag("appHome.rowForProcess:mobileScanCapture")
      ui.onNodeWithTag("appHome.rowForProcess:mobileScanCapture").performClick()
      waitFor("Scanner ready. Press the handheld trigger.")
      val control = "manual-control-${UUID.randomUUID()}"
      ui.onNodeWithText("Manual barcode").performScrollTo().performTextReplacement(control)
      ui.onNodeWithText("Manual barcode").performImeAction()
      ui.onNodeWithText("Submit barcode").performScrollTo().performClick()
      waitFor("Saved scan ", substring = true)
      waitFor("Scanner ready. Press the handheld trigger.")
      val before = scans()
      assertEquals(1, before.count { it.optString("barcode") == control && it.optString("scanSource") == "manual" })
      foreground(context)

      val marker = "probe-${UUID.randomUUID()}"
      val routed = java.util.concurrent.CopyOnWriteArrayList<Intent>()
      val delivered = CountDownLatch(4)
      // Route control in instrumentation only; it observes payloads and NEVER invokes a process/driver.
      val observer = object : BroadcastReceiver()
      {
         override fun onReceive(receiverContext: Context?, intent: Intent?)
         {
            if(intent?.getStringExtra("com.symbol.datawedge.data_string")?.startsWith("$marker-route-") == true)
            {
               routed.add(Intent(intent))
               delivered.countDown()
            }
         }
      }
      instrumentation.sendStatus(0, Bundle().apply { putString("qqqStage", "injection-baseline") })
      ContextCompat.registerReceiver(context, observer, IntentFilter("io.qrun.mobile.scanner.SCAN").apply {
         addCategory(Intent.CATEGORY_DEFAULT)
      }, ContextCompat.RECEIVER_EXPORTED)
      try
      {
         emitProbe(context, "$marker-route", probeUid)
         assertTrue("Actual SCAN payload must reach the route observer", delivered.await(10, TimeUnit.SECONDS))
         assertEquals((0 until 4).map { "$marker-route-$it" }.toSet(), routed.map { it.getStringExtra("com.symbol.datawedge.data_string") }.toSet())
         assertTrue(routed.all { it.action == "io.qrun.mobile.scanner.SCAN" &&
            it.getStringExtra("com.symbol.datawedge.label_type") == "LABEL-TYPE-CODE128" &&
            it.getStringExtra("com.symbol.datawedge.decode_mode") == "single" })
         foreground(context)
      }
      finally { context.unregisterReceiver(observer) }
      // The route observer is absent during the actual negative attempt.
      emitProbe(context, "$marker-negative", probeUid)
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
      while(System.nanoTime() < deadline) { foreground(context); Thread.sleep(100) }
      val after = scans()
      assertEquals("Unrelated-UID broadcasts must not create any scan", before.size, after.size)
      assertFalse(after.any { it.optString("barcode").startsWith(marker) })
      waitFor("Scanner ready. Press the handheld trigger.")
      foreground(context)
      instrumentation.sendStatus(0, Bundle().apply {
         putString("qqqStage", "injection-complete")
         putInt("qqqProbeUid", probeUid)
         putInt("qqqTargetUid", context.applicationInfo.uid)
         putInt("qqqForgedScanCount", 8)
      })
      if(expectedBarcode != null)
      {
         val events = mutableSetOf<String>()
         physicalScan(context, device.deviceId, expectedBarcode, "initial", after.size, events)
         ui.activityRule.scenario.recreate()
         physicalScan(context, device.deviceId, expectedBarcode, "recreated", after.size + 1, events)
         // Exercise only this disposable Activity's real stop/resume lifecycle, without touching other apps/settings.
         ui.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
         Thread.sleep(500)
         ui.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
         physicalScan(context, device.deviceId, expectedBarcode, "resumed", after.size + 2, events)
         assertEquals(3, events.size)
         instrumentation.sendStatus(0, Bundle().apply {
            putString("qqqStage", "hardware-complete")
            putInt("qqqHardwareScans", 3)
            putString("qqqHardwareEventIds", events.joinToString(","))
         })
      }
   }

   private fun physicalScan(context: Context, deviceId: String, expectedBarcode: String, phase: String,
      expectedBefore: Int, events: MutableSet<String>)
   {
      waitFor("Scanner ready. Press the handheld trigger.")
      foreground(context)
      val before = scans()
      assertEquals("No extra scan may occur between operator phases", expectedBefore, before.size)
      val priorIds = before.map { it.getString("id") }.toSet()
      val priorEvents = before.map { it.getString("eventId") }.toSet()
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120)
      fun remainingMillis() = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()).coerceAtLeast(1)
      fun pollScans(stage: String): List<JSONObject>
      {
         val remaining = remainingMillis()
         // Do not start a near-deadline 1ms HTTP call that hides the actual operator wait failure.
         if(remaining < 1000)
         {
            Thread.sleep(remaining)
            throw AssertionError("Physical trigger deadline reached during $stage; no additional polling budget remains")
         }
         return try { scans(remaining.coerceAtMost(20_000)) }
         catch(error: java.io.InterruptedIOException)
         {
            if(System.nanoTime() >= deadline)
               throw AssertionError("Physical trigger deadline reached during $stage; the final status poll did not complete", error)
            throw error
         }
      }
      InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("qqqStage", "hardware-$phase-ready") })
      var saved: JSONObject? = null
      while(saved == null)
      {
         assertTrue("Timed out waiting for the operator's physical trigger", System.nanoTime() < deadline)
         foreground(context, remainingMillis().coerceAtMost(5000))
         val current = pollScans("waiting for the operator's acknowledged scan")
         val added = current.filter { it.getString("id") !in priorIds }
         assertTrue("One trigger must not create multiple records", added.size <= 1 && current.size <= before.size + 1)
         if(added.isNotEmpty()) saved = added.single() else Thread.sleep(200)
      }
      val row = requireNotNull(saved)
      assertTrue("Hardware barcode must exactly match the configured synthetic value", row.getString("barcode") == expectedBarcode)
      assertEquals("zebra-emdk", row.getString("scanSource"))
      assertTrue("Hardware scan must belong to the approved QA device", row.getString("deviceId") == deviceId)
      assertEquals("scanner", row.getString("userId"))
      val event = row.getString("eventId")
      UUID.fromString(event)
      assertTrue("Each physical trigger needs its own event identity", event !in priorEvents && events.add(event))
      waitFor("Saved scan ${row.getString("id")}", timeoutMillis = remainingMillis())
      waitFor("Scanner ready. Press the handheld trigger.", timeoutMillis = remainingMillis())
      assertEquals(before.size + 1, pollScans("final saved-scan verification").size)
      foreground(context, remainingMillis().coerceAtMost(5000))
      InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("qqqStage", "hardware-$phase-saved") })
   }

   private fun emitProbe(context: Context, marker: String, probeUid: Int)
   {
      val completed = CountDownLatch(1)
      val result = AtomicReference<Bundle?>()
      context.sendOrderedBroadcast(Intent("io.qrun.mobile.scanner.probe.EMIT")
         .setComponent(ComponentName(probePackage, "$probePackage.ScanProbeReceiver"))
         .putExtra("targetPackage", context.packageName).putExtra("marker", marker), null,
         object : BroadcastReceiver()
         {
            override fun onReceive(receiverContext: Context?, intent: Intent?)
            {
               result.set(getResultExtras(false)?.apply { putInt("resultCode", resultCode) })
               completed.countDown()
            }
         }, null, 0, null, null)
      assertTrue("Separate probe must finish", completed.await(10, TimeUnit.SECONDS))
      assertEquals(1, result.get()?.getInt("resultCode"))
      assertEquals(probeUid, result.get()?.getInt("senderUid"))
      assertEquals(4, result.get()?.getInt("sent"))
      assertEquals(marker, result.get()?.getString("marker"))
   }

   private fun foreground(context: Context, timeoutMillis: Long = 5000)
   {
      val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
      var ready = false
      var flags = "unavailable"
      do
      {
         ui.activityRule.scenario.onActivity {
            val resumed = it.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            val focused = it.window.decorView.hasWindowFocus()
            val interactive = context.getSystemService(PowerManager::class.java).isInteractive
            val locked = context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
            flags = "resumed=$resumed, focused=$focused, interactive=$interactive, locked=$locked"
            ready = resumed && focused && interactive && !locked
         }
         if(ready) return
         val remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
         if(remaining > 0) Thread.sleep(remaining.coerceAtMost(50))
      }
      while(System.nanoTime() < deadline)
      assertTrue("Target must regain resumed, unlocked foreground focus: $flags", ready)
   }

   private fun scans(timeoutMillis: Long = 20_000): List<JSONObject>
   {
      val rows = json(Request.Builder().url("$origin/qqq/v1/table/mobileScan/query").header("Cookie", adminCookie)
         .post("{}".toRequestBody("application/json".toMediaType())).build(), timeoutMillis).getJSONArray("records")
      return (0 until rows.length()).map { rows.getJSONObject(it).getJSONObject("values") }
   }

   private fun process(name: String, suffix: String, values: JSONObject): JSONObject
   {
      var result = json(Request.Builder().url("$origin/qqq/v1/processes/$name$suffix").header("Cookie", adminCookie)
         .post(MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("values", values.toString())
            .addFormDataPart("stepTimeoutMillis", "10000").build()).build())
      val processId = result.optString("processUUID")
      val jobId = result.optString("jobUUID")
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
      while(result.optString("type") in listOf("JOB_STARTED", "RUNNING"))
      {
         assertTrue(System.nanoTime() < deadline)
         Thread.sleep(50)
         result = json(Request.Builder().url("$origin/qqq/v1/processes/$name/$processId/status/$jobId").header("Cookie", adminCookie).build())
      }
      assertFalse(result.has("error"))
      return result
   }

   private fun json(request: Request, timeoutMillis: Long = 20_000): JSONObject
   {
      val call = client.newCall(request)
      call.timeout().timeout(timeoutMillis, TimeUnit.MILLISECONDS)
      return call.execute().use {
         assertEquals("Semantic fixture request must succeed", 200, it.code)
         JSONObject(requireNotNull(it.body).string())
      }
   }
   private fun waitFor(text: String, substring: Boolean = false, timeoutMillis: Long = 60_000)
   { ui.waitUntil(timeoutMillis) { ui.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() } }
   private fun waitForTag(tag: String)
   { ui.waitUntil(60_000) { runCatching { ui.onNodeWithTag(tag).fetchSemanticsNode() }.isSuccess } }
}
