/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import android.Manifest
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Base64
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.sqrt
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Manual-only acceptance. Android permission UI is exclusively operated by the person holding the TC22. */
@RunWith(AndroidJUnit4::class)
class DeviceScreenAcceptanceTest
{
   @get:Rule val ui = createAndroidComposeRule<ScannerActivity>()
   private val instrumentation = InstrumentationRegistry.getInstrumentation()
   private val context get() = instrumentation.targetContext
   private val client = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()
   private lateinit var admin: SessionAcceptanceClient
   private lateinit var sessionId: String

   @Test fun operatorConsentCapturesHomeAndStopsAtEverySessionBoundary()
   {
      check(context.packageName == "io.qrun.mobile.scanner.qa" && BuildConfig.BUILD_TYPE == "qa")
      check(InstrumentationRegistry.getArguments().getString("operatorConsent") == "true")
      check(Build.MANUFACTURER.startsWith("Zebra", true) && Build.MODEL.startsWith("TC22", true))
      check(context.getSystemService(PowerManager::class.java).isInteractive &&
         !context.getSystemService(KeyguardManager::class.java).isDeviceLocked)
      val file = File(context.filesDir, "qa-screen.json")
      val config = JSONObject(file.readText())
      check(file.delete())
      check(config.getString("purpose") == "device-screen-qa")
      val server = URI(config.getString("serverUrl"))
      check(server.scheme == "http" && server.host == "127.0.0.1" && server.port > 0 &&
         server.userInfo == null && server.query == null && server.fragment == null && server.path == "/")
      val origin = server.toString().trimEnd('/')
      comparatorControls()
      waitFor("QQQ server URL")
      ui.onNodeWithText("QQQ server URL").performTextReplacement("$origin/")
      click("Connect")
      signIn(config.getString("scannerPassword"))
      waitFor("An administrator must approve this device before you can run mobile apps.")
      val device = DeviceCredentials(context, "$origin/")
      admin = SessionAcceptanceClient(origin, SessionAcceptanceClient.login(origin, config.getString("adminPassword"), client), client)
      try
      {
         admin.apply("mobileDeviceApprove", JSONObject().put("deviceId", device.deviceId))
         click("Check approval")
         await("approved-catalog") { ui.onAllNodesWithTag("appHome.rowForApp:mobileScanApp").fetchSemanticsNodes().isNotEmpty() }
         await("initial-session") {
            val current = admin.sessions().singleOrNull { it.optString("deviceId") == device.deviceId && it.optString("status") == "ACTIVE" }
            if(current != null) sessionId = current.getString("id")
            current != null && current.optString("captureMode") == "APP_CONTENT"
         }

         consent("initial")
         assertDeviceFrame()
         stage("screen-app-frame")
         val launcher = instrumentation.context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
         assertTrue("An unambiguous Android Home launcher must be available", !launcher.isNullOrBlank() && launcher != context.packageName)
         stage("screen-home-ready")
         await("operator-Home-and-matching-frame", 120) {
            val widget = admin.widget(sessionId) // This is the actual administrator's renewable viewing demand.
            val atHome = activePackage() == launcher
            atHome && !row().optBoolean("foreground") && row().optString("captureMode") == "DEVICE_SCREEN" &&
               matchesDisplay(SessionAcceptanceClient.image(widget))
         }
         stage("screen-home-verified")

         stage("screen-stop-ready")
         await("operator-system-Stop", 120) {
            // Reading the widget keeps demand alive: only the operator's Stop may end this round.
            val image = SessionAcceptanceClient.image(admin.widget(sessionId))
            !row().optBoolean("screenAvailable") && image == null && !projectionNotification()
         }
         stage("screen-stop-verified")
         stage("screen-return-app-ready")
         await("operator-return-via-Recents", 120) { activityFocused() }
         waitFor("Screen viewing paused")

         consent("expiry")
         assertDeviceFrame()
         // Deliberately query ONLY the session table here. A widget read would renew the lease under test.
         await("viewer-lease-expiry", 60) { !row().optBoolean("screenAvailable") && !projectionNotification() }
         assertTrue("Expired viewing demand must purge its frame", SessionAcceptanceClient.image(admin.widget(sessionId)) == null)
         waitFor("Screen viewing paused")
         stage("screen-expiry-verified")

         consent("logout")
         assertDeviceFrame()
         admin.apply("mobileSessionEnd", JSONObject().put("sessionId", sessionId))
         await("administrator-session-end") {
            row().optString("status") == "ENDED" && !projectionNotification() &&
               SessionAcceptanceClient.image(admin.widget(sessionId)) == null
         }
         waitFor("Log back in")
         stage("screen-logout-verified")
         click("Log back in")
         signIn(config.getString("scannerPassword"))
         await("fresh-session-without-projection") {
            val fresh = admin.sessions().singleOrNull { it.optString("deviceId") == device.deviceId && it.optString("status") == "ACTIVE" }
            fresh != null && fresh.getString("id") != sessionId && fresh.optString("captureMode") == "APP_CONTENT" && !projectionNotification()
         }
         stage("screen-fresh-login-verified")
      }
      finally { admin.logout() }
   }

   private fun consent(round: String)
   {
      assertFalse("Each round must begin without a running projection", projectionNotification())
      ui.onNodeWithTag("topBar.menuIcon").performClick()
      ui.onNodeWithTag("drawer.settings").performClick()
      if(Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
      {
         waitFor("Enable sharing notification")
         stage("screen-notification-permission-ready")
         click("Enable sharing notification") // Own app requests permission; the operator grants/denies Android's dialog.
         await("operator-notification-permission", 120) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED && activePackage() == context.packageName
         }
      }
      assertTrue("Sharing requires enabled notifications", context.getSystemService(NotificationManager::class.java).areNotificationsEnabled())
      waitFor("Share device screen…")
      click("Share device screen…")
      waitFor("Share this device screen?")
      stage("screen-consent-$round-ready")
      click("Continue to Android permission") // Never inspect or click the system permission window.
      await("operator-consent-$round", 120) {
         admin.widget(sessionId)
         row().optString("captureMode") == "DEVICE_SCREEN" && row().optBoolean("screenAvailable") && projectionNotification() && activePackage() == context.packageName
      }
      stage("screen-consent-$round-granted")
      click("Close")
   }

   private fun assertDeviceFrame()
   {
      await("actual-device-frame") {
         val image = SessionAcceptanceClient.image(admin.widget(sessionId))
         val value = row()
         image != null && value.optString("captureMode") == "DEVICE_SCREEN" && value.optBoolean("screenAvailable") && decoded(image) { bitmap ->
            bitmap.width in 1..640 && bitmap.height in 1..1280 && nonblank(blocks(bitmap))
         }
      }
   }

   private fun row() = admin.sessions().single { it.getString("id") == sessionId }
   private fun projectionNotification() = context.getSystemService(NotificationManager::class.java).activeNotifications.any {
      it.notification.channelId == "qqq-device-screen"
   }
   private fun activityFocused(): Boolean
   {
      var focused = false
      instrumentation.runOnMainSync { focused = ui.activity.hasWindowFocus() }
      return focused
   }
   private fun activePackage(): String?
   {
      val root = instrumentation.uiAutomation.rootInActiveWindow ?: return null
      return try { root.packageName?.toString() } finally { @Suppress("DEPRECATION") root.recycle() }
   }
   private fun signIn(password: String)
   {
      waitFor("Username")
      ui.onNodeWithText("Username").performTextReplacement("scanner")
      ui.onNodeWithText("Password").performTextReplacement(password)
      ui.onNodeWithText("Password").performImeAction()
      click("Sign in", scroll = true)
   }
   private fun click(text: String, scroll: Boolean = false)
   {
      if(text == "Log back in") await("completed-logout", 15) {
         ui.onAllNodesWithText("Signed out.").fetchSemanticsNodes().isNotEmpty() ||
            ui.onAllNodesWithText("Signed out on this device.", substring = true).fetchSemanticsNodes().isNotEmpty()
      }
      await("enabled-app-control", 15) {
         ui.onAllNodes(hasText(text, substring = false) and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty()
      }
      val node = ui.onNode(hasText(text, substring = false) and hasClickAction())
      if(scroll) node.performScrollTo()
      node.assertIsEnabled().performClick()
   }
   private fun waitFor(text: String) = await("app-control") { ui.onAllNodesWithText(text, substring = false).fetchSemanticsNodes().isNotEmpty() }
   private fun stage(name: String) = instrumentation.sendStatus(0, Bundle().apply { putString("qqqStage", name) })
   private fun await(name: String, seconds: Long = 40, predicate: () -> Boolean)
   {
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
      var previous = System.nanoTime()
      do
      {
         val now = System.nanoTime()
         ui.mainClock.advanceTimeBy(TimeUnit.NANOSECONDS.toMillis(now - previous).coerceAtLeast(16))
         previous = now
         if(predicate()) return
         Thread.sleep(250)
      } while(System.nanoTime() < deadline)
      throw AssertionError("Device-screen stage did not complete within its bound: $name")
   }

   private fun decoded(image: String, predicate: (Bitmap) -> Boolean): Boolean
   {
      val bytes = Base64.decode(image.substringAfter(','), Base64.DEFAULT)
      val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return false
      return try { predicate(bitmap) } finally { bitmap.recycle() }
   }

   /** Display pixels are an independent in-memory oracle, never uploaded or written as an artifact. */
   private fun matchesDisplay(image: String?): Boolean
   {
      if(image == null) return false
      val before = instrumentation.uiAutomation.takeScreenshot() ?: return false
      try
      {
         val first = blocks(before)
         if(!nonblank(first)) return false
         val agrees = decoded(image) { frame ->
            frame.width in 1..640 && frame.height in 1..1280 &&
               abs(frame.width.toDouble() / frame.height - before.width.toDouble() / before.height) < 0.01 &&
               compatible(first, blocks(frame))
         }
         val after = instrumentation.uiAutomation.takeScreenshot() ?: return false
         return try { agrees && error(first, blocks(after)).maxOrNull()!! <= 3.0 } finally { after.recycle() }
      }
      finally { before.recycle() }
   }

   // 24x40 spatial block means preserve launcher structure while averaging JPEG ringing and resize interpolation.
   // Exclude outer10% (status/navigation/consent indicators); require mean RGB error<=12/255 and95%<=24/255.
   // This deliberately fails changed layouts/blank screens; it is not a perceptual hash or exact-pixel claim.
   private fun blocks(bitmap: Bitmap): DoubleArray
   {
      val result = DoubleArray(24 * 40 * 3)
      for(y in 0 until 40) for(x in 0 until 24)
      {
         val left = (bitmap.width * (0.1 + 0.8 * x / 24)).toInt()
         val right = (bitmap.width * (0.1 + 0.8 * (x + 1) / 24)).toInt().coerceAtLeast(left + 1)
         val top = (bitmap.height * (0.1 + 0.8 * y / 40)).toInt()
         val bottom = (bitmap.height * (0.1 + 0.8 * (y + 1) / 40)).toInt().coerceAtLeast(top + 1)
         val i = (y * 24 + x) * 3
         for(py in top until bottom) for(px in left until right)
         {
            val pixel = bitmap.getPixel(px, py)
            result[i] = result[i] + Color.red(pixel).toDouble()
            result[i + 1] = result[i + 1] + Color.green(pixel).toDouble()
            result[i + 2] = result[i + 2] + Color.blue(pixel).toDouble()
         }
         val count = (right - left) * (bottom - top)
         for(channel in 0..2) result[i + channel] = result[i + channel] / count.toDouble()
      }
      return result
   }
   private fun nonblank(values: DoubleArray): Boolean
   {
      // Spatial variation, not variation between RGB channels: a solid red frame must also fail.
      return (0..2).any { channel ->
         val samples = values.indices.filter { it % 3 == channel }.map { values[it] }
         val average = samples.average()
         sqrt(samples.map { (it - average) * (it - average) }.average()) >= 18.0 &&
            samples.maxOrNull()!! - samples.minOrNull()!! >= 48.0
      }
   }
   private fun error(first: DoubleArray, second: DoubleArray) = first.indices.map { abs(first[it] - second[it]) }.sorted()
   private fun compatible(first: DoubleArray, second: DoubleArray): Boolean
   {
      val errors = error(first, second)
      return nonblank(first) && nonblank(second) && errors.average() <= 12.0 && errors[(errors.size * 0.95).toInt()] <= 24.0
   }
   private fun comparatorControls()
   {
      val pattern = Bitmap.createBitmap(240, 400, Bitmap.Config.ARGB_8888)
      val blank = Bitmap.createBitmap(240, 400, Bitmap.Config.ARGB_8888)
      try
      {
         for(y in 0 until pattern.height) for(x in 0 until pattern.width)
            pattern.setPixel(x, y, if((x / 24 + y / 40) % 2 == 0) Color.WHITE else Color.BLACK)
         val reference = blocks(pattern)
         assertTrue("Comparator accepts identical nonblank control", compatible(reference, reference))
         assertFalse("Comparator rejects blank frame", compatible(reference, blocks(blank)))
         assertFalse("Comparator rejects a wrong spatial layout", compatible(reference, reference.map { 255.0 - it }.toDoubleArray()))
      }
      finally { pattern.recycle(); blank.recycle() }
   }
}
