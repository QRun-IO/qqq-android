/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import android.os.Bundle
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*******************************************************************************
 ** Opt-in acceptance against the real fixture Authentik and real QQQ (docs/REAL-AUTHENTIK-ACCEPTANCE.md).
 ** Skipped unless the instrumentation argument realAuthentik=true is passed, so ordinary runs never reach it.
 ** Nothing here injects a callback, ActivityResult, token or discovery document, and nothing clears browser
 ** state: the external browser, the exported callback activity and the QQQ code exchange run as in production.
 ** The person holding the device types each operator's provider credentials into the browser themselves.
 ** Progress is reported only as fixed stage names; no URL, query, token, cookie or credential is emitted.
 *******************************************************************************/
@RunWith(AndroidJUnit4::class)
class RealAuthentikAcceptanceTest {
   @get:Rule
   val ui = createAndroidComposeRule<ScannerActivity>()
   private val instrumentation = InstrumentationRegistry.getInstrumentation()
   private val context get() = instrumentation.targetContext
   private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
   private lateinit var admin: SessionAcceptanceClient
   private lateinit var deviceId: String
   private var actorField: String = ""

   /*******************************************************************************
    ** A signs in through the browser, company sign-out runs, then B must authenticate afresh.
    *******************************************************************************/
   @Test
   fun browserCallbackThenCompanyLogoutThenFreshSecondOperator() {
      assumeTrue(InstrumentationRegistry.getArguments().getString("realAuthentik") == "true")
      check(context.packageName == "io.qrun.mobile.scanner.e2e") { "Requires the disposable E2E package" }
      val file = File(context.filesDir, "real-auth.json")
      val config = try { JSONObject(file.readText()) } catch (exception: Exception) { throw AssertionError("Fixture configuration is invalid") }
      finally { file.delete() }
      check(config.optString("fixtureType") == "real-authentik-android")
      val server = URI(config.getString("qqqOrigin"))
      check(server.scheme == "https" && server.host == "localhost" && server.port == 18443 && server.userInfo == null &&
         server.query == null && server.fragment == null && server.path in setOf("", "/"))
      val origin = "https://localhost:18443"
      val operators = config.getJSONObject("operators")
      val a = operators.getJSONObject("A")
      val b = operators.getJSONObject("B")
      check(a.getString("display") != b.getString("display")) { "Operators must have distinct display names" }
      actorField = config.optString("sessionActorField")
      admin = SessionAcceptanceClient(origin, config.getString("adminCookie"), client)

      var lastSession: String? = null
      try {
         waitFor("QQQ server URL")
         ui.onNodeWithText("QQQ server URL").performTextReplacement("$origin/")
         click("Connect")
         // OAuth2 is advertised: the browser button exists and no local password form is offered.
         await("browser-login-offered") { ui.onAllNodesWithTag("oauth2.signIn").fetchSemanticsNodes().isNotEmpty() }
         assertTrue("No password fallback may be offered", ui.onAllNodesWithText("Password").fetchSemanticsNodes().isEmpty())
         stage("login-screen-verified")

         // Operator A: real browser, real provider redirect, exported callback, real QQQ exchange.
         browserLogin("A")
         deviceId = DeviceCredentials(context, "$origin/").deviceId
         assertTrue("A new device must wait for administrator approval", pendingShown())
         stage("device-pending-A")
         approveAndOpenCatalog()
         assertPrincipal(a.getString("display"))
         val sessionA = activeSession(a.optString("actor"))
         stage("operator-A-verified")

         // Optional company sign-out; no browser state is cleared by this harness.
         companySignOut()
         await("operator-A-session-ended") { sessionStatus(sessionA) != "ACTIVE" }
         stage("operator-A-qqq-session-ended")
         click("Log back in")

         // Operator B must authenticate again; a silent reuse of A would show A as the principal.
         await("browser-login-offered-B") { ui.onAllNodesWithTag("oauth2.signIn").fetchSemanticsNodes().isNotEmpty() }
         browserLogin("B")
         if (pendingShown()) approveAndOpenCatalog()
         else await("catalog-B", 60) { catalogShown() }
         assertPrincipal(b.getString("display"))
         lastSession = activeSession(b.optString("actor"))
         assertNotEquals("B must hold a different QQQ session than A", sessionA, lastSession)
         stage("operator-B-verified")
      } finally {
         // End only the disposable device session; the supplied admin session belongs to root and is left alone.
         lastSession?.let { runCatching { admin.apply("mobileSessionEnd", JSONObject().put("sessionId", it)) } }
      }
   }

   /*******************************************************************************
    ** Start AppAuth from the native button, require a real external browser, then wait for the operator.
    *******************************************************************************/
   private fun browserLogin(label: String) {
      click("oauth2.signIn", tag = true)
      stage("browser-$label-requested")
      var browser: String? = null
      await("external-browser-foreground-$label", 45) {
         browser = foreignPackage()
         browser != null || ui.onAllNodesWithTag("oauth2.message").fetchSemanticsNodes().isNotEmpty()
      }
      assertTrue(
         "Browser sign-in never reached an external browser. Check temporary CA trust, reverse mappings and server advertisement.",
         browser != null
      )
      stage("browser-$label-operator-enter-credentials")
      // The provider's callback re-enters through the exported redirect activity; only then is the app foreground again.
      await("callback-returned-$label", 600) {
         activePackage() == context.packageName && (
            pendingShown() || catalogShown() || ui.onAllNodesWithTag("oauth2.message").fetchSemanticsNodes().isNotEmpty())
      }
      assertTrue("Native sign-in did not complete after the browser returned", ui.onAllNodesWithTag("oauth2.message").fetchSemanticsNodes().isEmpty())
      stage("callback-exchange-$label-complete")
   }

   /*******************************************************************************
    ** Approve this device through the real administrator process, then re-check approval in the app.
    *******************************************************************************/
   private fun approveAndOpenCatalog() {
      admin.apply("mobileDeviceApprove", JSONObject().put("deviceId", deviceId))
      click("Check approval")
      await("approved-catalog", 60) { catalogShown() }
   }

   /*******************************************************************************
    ** The principal shown by QQQ comes from the provider claims QQQ validated, not from this harness.
    *******************************************************************************/
   private fun assertPrincipal(display: String) {
      click("topBar.accountIcon", tag = true)
      await("principal-visible") { ui.onAllNodesWithText("Current User:  $display").fetchSemanticsNodes().isNotEmpty() }
      click("Close")
   }

   /*******************************************************************************
    ** Run optional RP-initiated logout through the browser and require explicit completion.
    *******************************************************************************/
   private fun companySignOut() {
      click("topBar.accountIcon", tag = true)
      await("company-signout-offered", 30) { ui.onAllNodesWithText("Sign out of company").fetchSemanticsNodes().isNotEmpty() }
      click("Sign out of company")
      // The confirmation dialog explains the effect; its button carries the same label as the Account item.
      await("company-signout-confirm") {
         ui.onAllNodesWithText("Sign out of QQQ and continue in your browser", substring = true).fetchSemanticsNodes().isNotEmpty() &&
            ui.onAllNodes(hasText("Sign out of company") and hasClickAction() and isEnabled()).fetchSemanticsNodes().size == 1
      }
      click("Sign out of company")
      stage("company-signout-requested")
      await("company-signout-browser", 45) { foreignPackage() != null }
      stage("company-signout-operator-confirm-if-asked")
      await("company-signout-returned", 300) {
         activePackage() == context.packageName && ui.onAllNodesWithTag("oauth2.logoutMessage").fetchSemanticsNodes().isNotEmpty()
      }
      await("company-signout-result", 30) { logoutMessage().contains("Company sign-out") }
      val message = logoutMessage()
      assertTrue("Company sign-out must be verified complete", message.contains("Company sign-out completed.") && !message.contains("did not complete"))
      stage("company-signout-verified")
   }

   /*******************************************************************************
    ** The visible company sign-out progress/result text.
    *******************************************************************************/
   private fun logoutMessage(): String = ui.onNodeWithTag("oauth2.logoutMessage").fetchSemanticsNode().config
      .getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

   /*******************************************************************************
    ** The single ACTIVE session for this device, optionally checked for its server-derived actor.
    *******************************************************************************/
   private fun activeSession(expectedActor: String): String {
      var found: JSONObject? = null
      await("active-session", 30) {
         found = admin.sessions().singleOrNull { it.optString("deviceId") == deviceId && it.optString("status") == "ACTIVE" }
         found != null
      }
      val row = requireNotNull(found)
      if (actorField.isNotBlank() && expectedActor.isNotBlank()) assertEquals("Server actor must match the provider user", expectedActor, row.optString(actorField))
      return row.getString("id")
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   private fun sessionStatus(id: String) = admin.sessions().single { it.getString("id") == id }.optString("status")

   /*******************************************************************************
    **
    *******************************************************************************/
   private fun pendingShown() = ui.onAllNodesWithText("An administrator must approve this device before you can run mobile apps.").fetchSemanticsNodes().isNotEmpty()

   /*******************************************************************************
    **
    *******************************************************************************/
   private fun catalogShown() = ui.onAllNodes(SemanticsMatcher("test tag starts with appHome.rowForApp:") {
      it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("appHome.rowForApp:") == true
   }).fetchSemanticsNodes().isNotEmpty()

   /*******************************************************************************
    ** The active window's package when it is neither this app nor system chrome (that is, the browser).
    *******************************************************************************/
   private fun foreignPackage(): String? = activePackage()?.takeIf { it != context.packageName && it != "com.android.systemui" && it != "android" }

   /*******************************************************************************
    **
    *******************************************************************************/
   private fun activePackage(): String? {
      val root = instrumentation.uiAutomation.rootInActiveWindow ?: return null
      return try {
         root.packageName?.toString()
      } finally {
         @Suppress("DEPRECATION") root.recycle()
      }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   private fun click(label: String, tag: Boolean = false) {
      val matcher = (if (tag) hasTestTag(label) else hasText(label, substring = false)) and hasClickAction()
      await("enabled-app-control") { ui.onAllNodes(matcher and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
      ui.onNode(matcher).assertIsEnabled().performClick()
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   private fun waitFor(text: String) = await("app-control") { ui.onAllNodesWithText(text, substring = false).fetchSemanticsNodes().isNotEmpty() }

   /*******************************************************************************
    ** Fixed stage identifiers only; the launcher maps them to operator instructions.
    *******************************************************************************/
   private fun stage(name: String) = instrumentation.sendStatus(0, Bundle().apply { putString("qqqStage", name) })

   /*******************************************************************************
    ** Bounded wait that keeps Compose's clock moving while the app is in the background.
    *******************************************************************************/
   private fun await(name: String, seconds: Long = 40, predicate: () -> Boolean) {
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
      var previous = System.nanoTime()
      do {
         val now = System.nanoTime()
         ui.mainClock.advanceTimeBy(TimeUnit.NANOSECONDS.toMillis(now - previous).coerceAtLeast(16))
         previous = now
         if (predicate()) return
         Thread.sleep(250)
      } while (System.nanoTime() < deadline)
      throw AssertionError("Real-provider stage did not complete within its bound: $name")
   }
}
