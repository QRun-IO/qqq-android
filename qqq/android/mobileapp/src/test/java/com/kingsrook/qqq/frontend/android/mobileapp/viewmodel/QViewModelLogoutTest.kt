/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.mobileapp.viewmodel

import com.kingsrook.qqq.frontend.android.core.controllers.QQQRepository
import com.kingsrook.qqq.frontend.android.core.model.Environment
import com.kingsrook.qqq.frontend.android.core.model.metadata.QInstance
import com.kingsrook.qqq.frontend.android.core.model.metadata.QProcessMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.BaseAuthenticationMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.ManageSessionRequest
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.ManageSessionResponse
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.NativeLogoutData
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.OAuth2AuthenticationMetaData
import com.kingsrook.qqq.frontend.android.core.model.processes.ProcessStepResult
import com.kingsrook.qqq.frontend.android.mobileapp.container.QAppContainer
import com.kingsrook.qqq.frontend.android.mobileapp.data.MockPreferencesDataStore
import com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication.OAuth2LoginBinding
import com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication.OAuth2LogoutBinding
import com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication.OAuth2LogoutPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SERVER = "https://example.invalid"
private const val ISSUER = "https://idp.example.invalid"
private const val REDIRECT = "io.qrun.test.oauth://callback/oauth2redirect"
private const val STATE = "state-0123456789abcdef"
private const val NONCE = "nonce-0123456789abcdef"
private const val VERIFIER = "verifier-0123456789abcdefghijklmnopqrstuvwxyz0123456789"

/*******************************************************************************
 ** Ordering of host session end, provider logout and local clearing; identity adoption after browser login.
 *******************************************************************************/
@OptIn(ExperimentalCoroutinesApi::class)
class QViewModelLogoutTest {
   /*******************************************************************************
    ** (a) Provider logout must read the hint before the host ends its device session (which ends the QQQ session).
    *******************************************************************************/
   @Test
   fun companyLogoutRequestsProviderMaterialBeforeHostSessionEnd() = runTest()
   {
      Dispatchers.setMain(StandardTestDispatcher(testScheduler))
      try {
         val repository = LogoutRepository()
         val model = signedInModel(repository)
         model.beforeLogout = { repository.calls += "beforeLogout" }
         val material = companyLogout(model)
         assertNotNull(material)
         assertEquals(listOf("logoutWithProvider", "beforeLogout"), repository.calls)
         assertEquals("Signed out.", model.logoutMessage)
         assertFalse(model.isAuthenticated)
         assertEquals("", model.sessionUUID)
         assertEquals("", model.sessionUserFullName)
      } finally {
         Dispatchers.resetMain()
      }
   }

   /*******************************************************************************
    ** (b) A failing host session end after the provider logout neither loses the hint nor leaves local state.
    *******************************************************************************/
   @Test
   fun failingHostSessionEndAfterProviderLogoutKeepsMaterialAndClearsLocally() = runTest()
   {
      Dispatchers.setMain(StandardTestDispatcher(testScheduler))
      try {
         val repository = LogoutRepository()
         val model = signedInModel(repository)
         model.beforeLogout = { repository.calls += "beforeLogout"; throw IllegalStateException("401 Unauthorized") }
         val material = companyLogout(model)
         assertNotNull(material)
         assertEquals(listOf("logoutWithProvider", "beforeLogout"), repository.calls)
         assertEquals("Signed out.", model.logoutMessage)
         assertFalse(model.isAuthenticated)
         assertFalse(model.logoutInProgress)
         assertEquals("", model.sessionUUID)
         assertEquals("", repository.activeSession)
      } finally {
         Dispatchers.resetMain()
      }
   }

   /*******************************************************************************
    ** (c) Ordinary sign-out keeps ending host resources before the core logout.
    *******************************************************************************/
   @Test
   fun ordinaryLogoutEndsHostSessionBeforeCoreLogout() = runTest()
   {
      Dispatchers.setMain(StandardTestDispatcher(testScheduler))
      try {
         val repository = LogoutRepository()
         val model = signedInModel(repository)
         model.beforeLogout = { repository.calls += "beforeLogout" }
         model.requestLogout()
         model.logOut().join()
         assertEquals(listOf("beforeLogout", "logout"), repository.calls)
         assertEquals("Signed out.", model.logoutMessage)
         assertFalse(model.isAuthenticated)
         assertEquals("", model.sessionUUID)
      } finally {
         Dispatchers.resetMain()
      }
   }

   /*******************************************************************************
    ** Browser login adopts the server-reported display name, as password login already does.
    *******************************************************************************/
   @Test
   fun browserLoginAdoptsServerReportedUserName() = runTest()
   {
      Dispatchers.setMain(StandardTestDispatcher(testScheduler))
      try {
         val repository = LogoutRepository()
         val model = signedInModel(repository)
         assertTrue(model.isAuthenticated)
         assertEquals("scanner-b", model.sessionUserFullName)
      } finally {
         Dispatchers.resetMain()
      }
   }

   /*******************************************************************************
    ** The user object is optional and unknown keys stay tolerated; blank or non-object users yield no name.
    *******************************************************************************/
   @Test
   fun manageSessionResponseExposesOptionalUserName() {
      val json = Json { ignoreUnknownKeys = true }
      val full = json.decodeFromString<ManageSessionResponse>(
         """{"uuid":"s","values":{"user":{"name":"scanner-b","email":"scanner-b@example.invalid","extra":1},"other":true},"unknown":"x"}"""
      )
      assertEquals("scanner-b", full.userName)
      assertNull(json.decodeFromString<ManageSessionResponse>("""{"uuid":"s"}""").userName)
      assertNull(json.decodeFromString<ManageSessionResponse>("""{"uuid":"s","values":{"user":"scanner-b"}}""").userName)
      assertNull(json.decodeFromString<ManageSessionResponse>("""{"uuid":"s","values":{"user":{"name":"  "}}}""").userName)
   }

   /*******************************************************************************
    ** Complete a native browser login against the fake repository so sign-out tests start authenticated.
    *******************************************************************************/
   private fun TestScope.signedInModel(repository: LogoutRepository): QViewModel {
      QAppContainer.availableEnvironments = listOf(Environment("Test", SERVER))
      val model = QViewModel(repository, MockPreferencesDataStore())
      advanceUntilIdle()
      val generation = requireNotNull(model.prepareOAuth2Login(loginBinding))
      assertTrue(model.beginOAuth2Browser(generation, loginBinding, STATE, NONCE, VERIFIER))
      model.completeOAuth2Login(loginBinding, STATE, STATE, NONCE, "client", REDIRECT, "login", "code")
      advanceUntilIdle()
      assertTrue(model.isAuthenticated)
      assertEquals("session-1", model.sessionUUID)
      repository.calls.clear()
      return model
   }

   /*******************************************************************************
    ** Run the company sign-out preparation exactly as QOAuth2Logout does before opening the browser.
    *******************************************************************************/
   private suspend fun companyLogout(model: QViewModel): NativeLogoutData? {
      val binding = OAuth2LogoutBinding(loginBinding, REDIRECT)
      model.requestCompanyLogout(binding)
      assertEquals(OAuth2LogoutPhase.PREPARING, model.companyLogoutPhase)
      return model.logOutWithProvider(model.companyLogoutGeneration, binding)
   }

   private val loginBinding = OAuth2LoginBinding(
      SERVER, "oauth", ISSUER, "$ISSUER/.well-known/openid-configuration", "client", "openid", REDIRECT, "login", "REQUIRED"
   )

   /*******************************************************************************
    ** Records the order of logout-related calls; any call after the session ended returns no provider material.
    *******************************************************************************/
   private class LogoutRepository : QQQRepository {
      val calls = mutableListOf<String>()
      var activeSession = ""

      override suspend fun getAuthenticationMetaData(): BaseAuthenticationMetaData = OAuth2AuthenticationMetaData(
         "oauth", values = OAuth2AuthenticationMetaData.Values(
            clientId = "client", scopes = "openid",
            nativeSessionLogin = OAuth2AuthenticationMetaData.NativeSessionLogin(
               OAuth2AuthenticationMetaData.SESSION_FLOW, "S256", ISSUER, "$ISSUER/.well-known/openid-configuration", listOf(REDIRECT), "login", "REQUIRED"
            ),
            nativeSessionLogout = OAuth2AuthenticationMetaData.NativeSessionLogout(OAuth2AuthenticationMetaData.LOGOUT_FLOW, listOf(REDIRECT))
         )
      )

      override suspend fun exchangeNativeSession(body: ManageSessionRequest): ManageSessionResponse =
         ManageSessionResponse("session-1", mapOf("user" to mapOf("name" to "scanner-b", "email" to "scanner-b@example.invalid")))

      override suspend fun logout() {
         calls += "logout"
         activeSession = ""
      }

      override suspend fun logoutWithProvider(postLogoutRedirectUri: String): NativeLogoutData? {
         calls += "logoutWithProvider"
         val material = if (activeSession.isBlank()) null else NativeLogoutData(ISSUER, "$ISSUER/end-session", postLogoutRedirectUri, "hint")
         activeSession = ""
         return material
      }

      override fun setSessionUUID(sessionUUID: String) {
         activeSession = sessionUUID
      }

      override fun setEnvironment(environment: Environment) {}
      override suspend fun manageSession(body: ManageSessionRequest): ManageSessionResponse = error("Not used")
      override suspend fun getMetaData(frontendName: String, frontendVersion: String, applicationName: String, applicationVersion: String): QInstance = QInstance()
      override suspend fun getProcessMetaData(processName: String): QProcessMetaData = error("Not used")
      override suspend fun processInit(processName: String, values: Map<String, Any?>, recordsParam: String?, recordIds: String?, filterJSON: String?, stepTimeoutMillis: Int?): ProcessStepResult = error("Not used")
      override suspend fun processStep(processName: String, processUUID: String, stepName: String, values: Map<String, Any?>, stepTimeoutMillis: Int?): ProcessStepResult = error("Not used")
      override suspend fun processJobStatus(processName: String, processUUID: String, jobUUID: String): ProcessStepResult = error("Not used")
      override fun getURI(path: String): String = error("Not used")
   }
}
