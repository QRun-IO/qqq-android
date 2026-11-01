/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.sampleandroidmobileapp

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.kingsrook.qqq.frontend.android.core.controllers.QQQRepository
import com.kingsrook.qqq.frontend.android.core.model.Environment
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.NativeLogoutData
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.OAuth2AuthenticationMetaData
import com.kingsrook.qqq.frontend.android.mobileapp.container.QAppContainer
import com.kingsrook.qqq.frontend.android.mobileapp.data.MockPreferencesDataStore
import com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication.LocalOAuth2Discovery
import com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication.OAuth2LogoutBinding
import com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication.OAuth2LogoutPhase
import com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication.QLogoutForm
import com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication.oauth2LogoutBinding
import com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication.oauth2RedirectUri
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.LoadState
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.AuthorizationServiceDiscovery
import net.openid.appauth.EndSessionRequest
import net.openid.appauth.EndSessionResponse
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/*******************************************************************************
 ** Tests actual SDK request/result plumbing with synthetic responses, not real-provider acceptance.
 *******************************************************************************/
class OAuth2LogoutTests : BaseTest() {
   @get:Rule
   val ui = createAndroidComposeRule<ComponentActivity>()
   private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
   private val issuer = "https://identity.example.test"
   private val endpoint = "$issuer/end-session"
   private val hint = "qqq-logout-canary-" + UUID.randomUUID()

   /*******************************************************************************
    **
    *******************************************************************************/
   private class Fixture(
      val model: QViewModel, val binding: OAuth2LogoutBinding, val calls: AtomicInteger,
      val cookie: AtomicReference<String>, val preferences: MockPreferencesDataStore
   )

   /*******************************************************************************
    **
    *******************************************************************************/
   private fun start(failLocal: Boolean = false, returnedEndpoint: String = endpoint): Fixture {
      val server = "https://qqq.example.test/"
      QAppContainer.availableEnvironments = listOf(Environment("Company", server))
      val callback = oauth2RedirectUri(instrumentation.targetContext.packageName)
      val metadata = OAuth2AuthenticationMetaData(
         "company", values = OAuth2AuthenticationMetaData.Values(
            clientId = "synthetic-client", scopes = "openid", nativeSessionLogin = OAuth2AuthenticationMetaData.NativeSessionLogin(
               OAuth2AuthenticationMetaData.SESSION_FLOW, "S256", issuer, "$issuer/discovery", listOf(callback), "login", "REQUIRED"
            ),
            nativeSessionLogout = OAuth2AuthenticationMetaData.NativeSessionLogout(OAuth2AuthenticationMetaData.LOGOUT_FLOW, listOf(callback))
         )
      )
      val calls = AtomicInteger()
      val cookie = AtomicReference("synthetic-session")
      val repository = object : QQQRepository by SampleAppMockQQQRepository() {
         /*******************************************************************************
          **
          *******************************************************************************/
         override suspend fun getAuthenticationMetaData() = metadata

         /*******************************************************************************
          **
          *******************************************************************************/
         override fun setSessionUUID(sessionUUID: String) {
            cookie.set(sessionUUID)
         }

         /*******************************************************************************
          **
          *******************************************************************************/
         override suspend fun logoutWithProvider(postLogoutRedirectUri: String): NativeLogoutData? {
            assertEquals(callback, postLogoutRedirectUri)
            calls.incrementAndGet()
            if (failLocal) error("synthetic transport failure")
            return NativeLogoutData(issuer, returnedEndpoint, callback, hint)
         }
      }
      val preferences = MockPreferencesDataStore()
      val model = QViewModel(repository, preferences)
      val discovery = JSONObject().put("issuer", issuer).put("authorization_endpoint", "$issuer/authorize")
         .put("token_endpoint", "$issuer/token").put("end_session_endpoint", endpoint).put("jwks_uri", "$issuer/jwks")
         .put("response_types_supported", JSONArray(listOf("code"))).put("subject_types_supported", JSONArray(listOf("public")))
         .put("id_token_signing_alg_values_supported", JSONArray(listOf("RS256")))
      ui.setContent {
         CompositionLocalProvider(LocalOAuth2Discovery provides {
            AuthorizationServiceConfiguration(AuthorizationServiceDiscovery(discovery))
         }) {
            if (model.isLogoutRequested) QLogoutForm(model, metadata) else Text("Ready")
         }
      }
      ui.waitUntil(10000) { model.initialLoadFromDataStoreLoadState is LoadState.Success && model.authenticationMetaDataLoadState is LoadState.Success }
      ui.runOnIdle { model.logInSuccessful() }
      return Fixture(model, requireNotNull(oauth2LogoutBinding(metadata, server, instrumentation.targetContext.packageName)), calls, cookie, preferences)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   private fun browser(result: (EndSessionRequest) -> Instrumentation.ActivityResult, block: (AtomicInteger) -> Unit) {
      val launches = AtomicInteger()
      val monitor = object : Instrumentation.ActivityMonitor() {
         /*******************************************************************************
          **
          *******************************************************************************/
         override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.component?.className != "net.openid.appauth.AuthorizationManagementActivity") return null
            launches.incrementAndGet()
            // Pinned SDK adapter. Production uses the public AppAuth result contract.
            return result(EndSessionRequest.jsonDeserialize(requireNotNull(intent.getStringExtra("authRequest"))))
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
   private fun beginAndAwait(fixture: Fixture) {
      ui.runOnIdle { fixture.model.requestCompanyLogout(fixture.binding) }
      ui.waitUntil(10000) { fixture.model.companyLogoutPhase == OAuth2LogoutPhase.FINISHED }
      assertFalse(fixture.model.isAuthenticated)
      assertEquals("", fixture.cookie.get())
      assertEquals("", fixture.model.sessionUserFullName)
      assertFalse(fixture.preferences.preferences.toString().contains(hint))
      assertEquals(1, fixture.calls.get())
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun stateOnlyReturnCompletesAfterLocalClearingAndDoesNotLogOrPersistHint() {
      val fixture = start()
      browser({ request ->
         assertFalse(fixture.model.isAuthenticated)
         assertEquals("", fixture.cookie.get())
         assertNull(fixture.model.prepareOAuth2Login(fixture.binding.login))
         assertNull(fixture.model.prepareAuthentication())
         fixture.model.requestLogin()
         assertTrue(fixture.model.isLogoutRequested)
         assertEquals(endpoint, request.configuration.endSessionEndpoint.toString())
         assertEquals(hint, request.toUri().getQueryParameter("id_token_hint"))
         assertEquals(fixture.binding.postLogoutRedirectUri, request.postLogoutRedirectUri.toString())
         Instrumentation.ActivityResult(Activity.RESULT_OK, EndSessionResponse.Builder(request).setState(request.state).build().toIntent())
      }) { launches ->
         beginAndAwait(fixture)
         assertTrue(fixture.model.logoutMessage.contains("Company sign-out completed."))
         assertEquals(1, launches.get())
      }
      val logs = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("logcat -d -t 1000 -v brief"))
         .bufferedReader().use { it.readText() }
      assertFalse("Synthetic logout hint must not enter logcat", logs.contains(hint))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun cancellationAndWrongStateNeverClaimProviderLogoutOrRestoreLocalIdentity() {
      val fixture = start()
      browser({ request ->
         Instrumentation.ActivityResult(
            Activity.RESULT_OK,
            EndSessionResponse.Builder(request).setState("wrong-state").build().toIntent()
         )
      }) { launches ->
         beginAndAwait(fixture)
         assertTrue(fixture.model.logoutMessage.contains("Company sign-out did not complete."))
         assertEquals(1, launches.get())
      }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun cancelledBrowserLeavesLocalSessionCleared() {
      val fixture = start()
      browser({ Instrumentation.ActivityResult(Activity.RESULT_CANCELED, Intent()) }) { launches ->
         beginAndAwait(fixture)
         assertTrue(fixture.model.logoutMessage.contains("Company sign-out did not complete."))
         assertEquals(1, launches.get())
      }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun localFailureDoesNotLaunchBrowserAndStillClearsPrivateState() {
      val fixture = start(failLocal = true)
      browser({ error("Browser must not launch after local failure") }) { launches ->
         beginAndAwait(fixture)
         assertTrue(fixture.model.logoutMessage.contains("Server sign-out could not be confirmed."))
         assertEquals(0, launches.get())
      }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun mismatchedDiscoveredEndpointCannotReceiveHint() {
      val fixture = start(returnedEndpoint = "https://other.example.test/logout")
      browser({ error("Untrusted endpoint must not receive the hint") }) { launches ->
         beginAndAwait(fixture)
         assertTrue(fixture.model.logoutMessage.contains("Company sign-out did not complete."))
         assertEquals(0, launches.get())
      }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun lateCallbackAfterResetCannotRestoreLogoutOrLoginState() {
      val fixture = start()
      browser({ request ->
         fixture.model.resetApp()
         Instrumentation.ActivityResult(Activity.RESULT_OK, EndSessionResponse.Builder(request).setState(request.state).build().toIntent())
      }) { launches ->
         ui.runOnIdle { fixture.model.requestCompanyLogout(fixture.binding) }
         ui.waitUntil(10000) { launches.get() == 1 && fixture.model.companyLogoutPhase == OAuth2LogoutPhase.IDLE }
         assertFalse(fixture.model.isAuthenticated)
         assertFalse(fixture.model.isLogoutRequested)
         assertFalse(fixture.model.logoutMessage.contains("Company sign-out completed."))
         assertEquals("", fixture.cookie.get())
      }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun wrongAppAuthResponseTypeCannotCompleteLogout() {
      val fixture = start()
      browser({
         Instrumentation.ActivityResult(
            Activity.RESULT_OK,
            Intent().putExtra("net.openid.appauth.AuthorizationResponse", "{}")
         )
      }) { launches ->
         beginAndAwait(fixture)
         assertTrue(fixture.model.logoutMessage.contains("Company sign-out did not complete."))
         assertEquals(1, launches.get())
      }
   }
}
