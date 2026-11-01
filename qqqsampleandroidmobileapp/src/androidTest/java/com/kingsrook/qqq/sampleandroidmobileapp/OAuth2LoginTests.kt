/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.sampleandroidmobileapp

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import com.kingsrook.qqq.frontend.android.core.controllers.QQQRepository
import com.kingsrook.qqq.frontend.android.core.controllers.UiApiCompatibility
import com.kingsrook.qqq.frontend.android.core.controllers.UiApiVersion
import com.kingsrook.qqq.frontend.android.core.model.Environment
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.ManageSessionRequest
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.ManageSessionResponse
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.OAuth2AuthenticationMetaData
import com.kingsrook.qqq.frontend.android.mobileapp.QMobileAppMain
import com.kingsrook.qqq.frontend.android.mobileapp.container.QAppContainer
import com.kingsrook.qqq.frontend.android.mobileapp.data.MockPreferencesDataStore
import com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication.LocalOAuth2Discovery
import com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication.OAuth2LoginPhase
import com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication.oauth2RedirectUri
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.AuthorizationServiceDiscovery
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/*******************************************************************************
 ** Native UI and actual AppAuth request/result handling; no live identity provider or TLS bypass.
 *******************************************************************************/
class OAuth2LoginTests : BaseTest() {
   @get:Rule
   val ui = createAndroidComposeRule<ComponentActivity>()
   private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
   private val issuer = "https://identity.example.test"

   /*******************************************************************************
    **
    *******************************************************************************/
   private class Fixture(
      val model: QViewModel, val checks: AtomicInteger, val exchanges: AtomicInteger,
      val adopted: AtomicReference<String?>, val releaseExchange: CompletableDeferred<Unit>,
      val discarded: AtomicReference<String?>, val originalNonce: AtomicReference<String?>
   )

   /*******************************************************************************
    **
    *******************************************************************************/
   private fun start(
      configured: Boolean = true, discoveryIssuer: String = issuer, holdExchange: Boolean = false,
      apiVersion: String = "1.10.0", nonceFeature: Boolean = true, noncePolicy: String = "REQUIRED"
   ): Fixture {
      QAppContainer.availableEnvironments = listOf(Environment("Organization", "https://qqq.example.test/"))
      val callback = oauth2RedirectUri(instrumentation.targetContext.packageName)
      val metadata = OAuth2AuthenticationMetaData(
         "organization", values = OAuth2AuthenticationMetaData.Values(
            clientId = "public-configuration-id", scopes = "openid profile",
            nativeSessionLogin = if (configured) OAuth2AuthenticationMetaData.NativeSessionLogin(
               OAuth2AuthenticationMetaData.SESSION_FLOW, "S256", issuer, "$issuer/discovery", listOf(callback),
               prompt = "login", nonceValidation = noncePolicy
            ) else null
         )
      )
      val checks = AtomicInteger()
      val exchanges = AtomicInteger()
      val adopted = AtomicReference<String?>()
      val discarded = AtomicReference<String?>()
      val originalNonce = AtomicReference<String?>()
      val releaseExchange = CompletableDeferred<Unit>()
      if (!holdExchange) releaseExchange.complete(Unit)
      val base = SampleAppMockQQQRepository()
      val repository = object : QQQRepository by base {
         /*******************************************************************************
          **
          *******************************************************************************/
         override suspend fun getAuthenticationMetaData() = metadata

         /*******************************************************************************
          **
          *******************************************************************************/
         override suspend fun verifyNativeSessionLogin() {
            checks.incrementAndGet()
            UiApiCompatibility.validateNativeSessionLogin(
               UiApiVersion(
                  apiVersion,
                  listOf("nativeSessionLogin") + if (nonceFeature) listOf("nativeSessionNonceValidation") else emptyList()
               )
            )
         }

         /*******************************************************************************
          **
          *******************************************************************************/
         override suspend fun exchangeNativeSession(body: ManageSessionRequest): ManageSessionResponse {
            assertEquals(OAuth2AuthenticationMetaData.SESSION_FLOW, body.authenticationFlow)
            assertEquals(callback, body.redirectUri)
            assertEquals("synthetic-authorization-code", body.code)
            assertTrue(Regex("[A-Za-z0-9_-]{16,512}").matches(requireNotNull(body.expectedNonce)))
            originalNonce.get()?.let { assertEquals(it, body.expectedNonce) }
            assertTrue(Regex("[A-Za-z0-9._~-]{43,128}").matches(requireNotNull(body.codeVerifier)))
            exchanges.incrementAndGet()
            // Model an HTTP response that arrives after cancellation has already been requested.
            withContext(NonCancellable) { releaseExchange.await() }
            return ManageSessionResponse("synthetic-native-session")
         }

         /*******************************************************************************
          **
          *******************************************************************************/
         override suspend fun discardNativeSession(server: String, session: String) {
            assertEquals("https://qqq.example.test/", server)
            discarded.set(session)
         }

         /*******************************************************************************
          **
          *******************************************************************************/
         override suspend fun manageSession(body: ManageSessionRequest): ManageSessionResponse =
            error("Native OAuth must use the deferred semantic exchange")

         /*******************************************************************************
          **
          *******************************************************************************/
         override fun setSessionUUID(sessionUUID: String) {
            adopted.set(sessionUUID)
         }
      }
      val model = QViewModel(repository, MockPreferencesDataStore())
      val discovery = JSONObject().put("issuer", discoveryIssuer)
         .put("authorization_endpoint", "$issuer/authorize").put("token_endpoint", "$issuer/token")
         .put("jwks_uri", "$issuer/jwks").put("response_types_supported", JSONArray(listOf("code")))
         .put("subject_types_supported", JSONArray(listOf("public")))
         .put("id_token_signing_alg_values_supported", JSONArray(listOf("RS256")))
      ui.setContent {
         CompositionLocalProvider(LocalOAuth2Discovery provides {
            AuthorizationServiceConfiguration(AuthorizationServiceDiscovery(discovery))
         }) {
            model.setNavController(rememberNavController())
            QMobileAppMain(model, "Organization")
         }
      }
      return Fixture(model, checks, exchanges, adopted, releaseExchange, discarded, originalNonce)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   private fun browser(
      result: (AuthorizationRequest) -> Instrumentation.ActivityResult,
      block: (AtomicInteger) -> Unit
   ) {
      val launches = AtomicInteger()
      val monitor = object : Instrumentation.ActivityMonitor() {
         /*******************************************************************************
          **
          *******************************************************************************/
         override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.component?.className != "net.openid.appauth.AuthorizationManagementActivity") return null
            launches.incrementAndGet()
            // Pinned AppAuth0.11.1 test adapter; no production code relies on this internal extra.
            return result(AuthorizationRequest.jsonDeserialize(requireNotNull(intent.getStringExtra("authRequest"))))
         }
      }
      instrumentation.addMonitor(monitor)
      try {
         block(launches)
      } finally {
         instrumentation.removeMonitor(monitor)
      }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun advertisedBrowserFlowExchangesOnceAndOpensApps() {
      val fixture = start()
      browser({ request ->
         fixture.originalNonce.set(requireNotNull(request.nonce))
         assertEquals("S256", request.codeVerifierChallengeMethod)
         assertEquals("login", request.prompt)
         assertEquals(listOf("login"), request.toUri().getQueryParameters("prompt"))
         assertEquals("public-configuration-id", request.clientId)
         Instrumentation.ActivityResult(
            Activity.RESULT_OK, AuthorizationResponse.Builder(request)
               .setState(request.state).setAuthorizationCode("synthetic-authorization-code").build().toIntent()
         )
      }) { launches ->
         ui.onNodeWithTag("oauth2.signIn").performClick()
         ui.waitUntil(10000) { fixture.model.isAuthenticated }
         ui.onNodeWithTag("topAppBar.title").assertTextEquals("Apps")
         ui.onNodeWithTag("appHome.rowForApp:app1").assertIsDisplayed()
         assertEquals(1, launches.get())
         assertEquals(1, fixture.checks.get())
         assertEquals(1, fixture.exchanges.get())
         assertEquals("synthetic-native-session", fixture.adopted.get())
      }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun browserCancellationDoesNotExchangeOrFallBackToPassword() {
      val fixture = start()
      browser({ Instrumentation.ActivityResult(Activity.RESULT_CANCELED, Intent()) }) { launches ->
         ui.onNodeWithTag("oauth2.signIn").performClick()
         ui.waitUntil(10000) { fixture.model.oauth2LoginMessage != null }
         ui.onNodeWithTag("oauth2.message").assertIsDisplayed()
         ui.onNodeWithText("Username").assertDoesNotExist()
         assertEquals(OAuth2LoginPhase.READY, fixture.model.oauth2LoginPhase)
         assertFalse(fixture.model.isAuthenticated)
         assertEquals(1, launches.get())
         assertEquals(0, fixture.exchanges.get())
      }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun mismatchedBrowserStateNeverExchangesTheCode() {
      val fixture = start()
      browser({ request ->
         Instrumentation.ActivityResult(
            Activity.RESULT_OK, AuthorizationResponse.Builder(request)
               .setState("unrelated-browser-state").setAuthorizationCode("synthetic-authorization-code").build().toIntent()
         )
      }) { launches ->
         ui.onNodeWithTag("oauth2.signIn").performClick()
         ui.waitUntil(10000) { fixture.model.oauth2LoginMessage != null }
         assertEquals(1, launches.get())
         assertEquals(0, fixture.exchanges.get())
         assertFalse(fixture.model.isAuthenticated)
      }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun cancelledAttemptCannotBeRevivedByALateSuccessfulBrowserResult() {
      val fixture = start()
      browser({ request ->
         fixture.model.cancelOAuth2Login()
         Instrumentation.ActivityResult(
            Activity.RESULT_OK, AuthorizationResponse.Builder(request)
               .setState(request.state).setAuthorizationCode("synthetic-authorization-code").build().toIntent()
         )
      }) { launches ->
         ui.onNodeWithTag("oauth2.signIn").performClick()
         ui.waitUntil(10000) { launches.get() == 1 && fixture.model.oauth2LoginPhase == OAuth2LoginPhase.READY }
         ui.waitForIdle()
         assertEquals(0, fixture.exchanges.get())
         assertFalse(fixture.model.isAuthenticated)
      }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun cancelledExchangeDiscardsLateSessionWithoutAdoptingIt() {
      val fixture = start(holdExchange = true)
      try {
         browser({ request ->
            Instrumentation.ActivityResult(
               Activity.RESULT_OK, AuthorizationResponse.Builder(request)
                  .setState(request.state).setAuthorizationCode("synthetic-authorization-code").build().toIntent()
            )
         }) {
            ui.onNodeWithTag("oauth2.signIn").performClick()
            ui.waitUntil(10000) { fixture.exchanges.get() == 1 }
            ui.runOnIdle { fixture.model.cancelOAuth2Login() }
            fixture.releaseExchange.complete(Unit)
            ui.waitUntil(10000) { fixture.discarded.get() != null }
            assertEquals("synthetic-native-session", fixture.discarded.get())
            assertNull(fixture.adopted.get())
            assertFalse(fixture.model.isAuthenticated)
         }
      } finally {
         fixture.releaseExchange.complete(Unit)
      }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun missingNativeAdvertisementDoesNotOfferPasswordOrBrowserLogin() {
      val fixture = start(configured = false)
      ui.onNodeWithText("This server has not configured compatible native browser sign-in with required nonce validation. Ask your administrator to enable it.").assertIsDisplayed()
      ui.onNodeWithTag("oauth2.signIn").assertDoesNotExist()
      ui.onNodeWithText("Username").assertDoesNotExist()
      assertFalse(fixture.model.isAuthenticated)
      assertEquals(0, fixture.checks.get())
      assertEquals(0, fixture.exchanges.get())
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun wrongDiscoveryIssuerIsRejectedBeforeBrowserLaunch() {
      val fixture = start(discoveryIssuer = "https://different.example.test")
      browser({ error("Invalid discovery must never launch a browser") }) { launches ->
         ui.onNodeWithTag("oauth2.signIn").performClick()
         ui.waitUntil(10000) { fixture.model.oauth2LoginMessage != null }
         assertEquals(0, launches.get())
         assertEquals(0, fixture.exchanges.get())
         assertFalse(fixture.model.isAuthenticated)
      }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun oldApiCannotLaunchBrowserEvenWithNewMetadata() {
      assertVersionRejected(start(apiVersion = "1.9.0"))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun missingNonceFeatureCannotLaunchBrowserEvenWithRequiredDescriptor() {
      assertVersionRejected(start(nonceFeature = false))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   private fun assertVersionRejected(fixture: Fixture) {
      browser({ error("Unsupported nonce contract must not launch the browser") }) { launches ->
         ui.onNodeWithTag("oauth2.signIn").performClick()
         ui.waitUntil(10000) { fixture.model.oauth2LoginMessage != null }
         ui.onNodeWithText("This server does not support native sign-in with required nonce validation. Ask your administrator to upgrade or enable it.").assertIsDisplayed()
         assertEquals(0, launches.get())
         assertEquals(0, fixture.exchanges.get())
         assertFalse(fixture.model.isAuthenticated)
         ui.onNodeWithText("Username").assertDoesNotExist()
      }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun missingNoncePolicyDoesNotOfferBrowserOrPasswordFallback() {
      val fixture = start(noncePolicy = "")
      ui.onNodeWithText("This server has not configured compatible native browser sign-in with required nonce validation. Ask your administrator to enable it.").assertIsDisplayed()
      ui.onNodeWithTag("oauth2.signIn").assertDoesNotExist()
      ui.onNodeWithText("Username").assertDoesNotExist()
      assertEquals(0, fixture.checks.get())
      assertEquals(0, fixture.exchanges.get())
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun callbackRequestCannotReplaceTheRetainedOriginalNonce() {
      val fixture = start()
      browser({ request ->
         val changed = AuthorizationRequest.Builder(request.configuration, request.clientId, request.responseType, request.redirectUri)
            .setState(request.state).setNonce("replacement-nonce-must-fail").setCodeVerifier(requireNotNull(request.codeVerifier))
            .setScope(request.scope).setPrompt(request.prompt).build()
         Instrumentation.ActivityResult(
            Activity.RESULT_OK, AuthorizationResponse.Builder(changed)
               .setState(request.state).setAuthorizationCode("synthetic-authorization-code").build().toIntent()
         )
      }) { launches ->
         ui.onNodeWithTag("oauth2.signIn").performClick()
         ui.waitUntil(10000) { fixture.model.oauth2LoginMessage != null }
         assertEquals(1, launches.get())
         assertEquals(0, fixture.exchanges.get())
         assertNull(fixture.adopted.get())
         assertFalse(fixture.model.isAuthenticated)
      }
   }
}
