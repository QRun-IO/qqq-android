/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication

import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.NativeLogoutData
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.OAuth2AuthenticationMetaData
import org.junit.Assert.*
import org.junit.Test

/** Public binding validation and single-use completion do not need Android or a fake provider. */
class OAuth2LogoutAttemptTest
{
   private val callback = "io.qrun.test.oauth://callback/oauth2redirect"
   private val endpoint = "https://identity.example/end-session"
   private val state = "synthetic-single-use-state"
   private val login = OAuth2LoginBinding("https://qqq.example/", "company", "https://identity.example",
      "https://identity.example/discovery", "client", "openid", callback, "login", "REQUIRED")
   private val binding = OAuth2LogoutBinding(login, callback)

   @Test fun completionIsBoundToOriginProviderCallbackEndpointAndConsumesEvenRejectedResponses()
   {
      val alternatives = listOf(binding.copy(login = login.copy(server = "https://other.example/")),
         binding.copy(login = login.copy(providerName = "other")), binding.copy(postLogoutRedirectUri = "$callback/other"))
      for(other in alternatives)
      {
         val attempt = OAuth2LogoutAttempt()
         attempt.begin(binding, endpoint, state)
         assertFalse(attempt.consume(other, state, state, endpoint, callback))
         assertFalse(attempt.consume(binding, state, state, endpoint, callback))
      }
      for(values in listOf(listOf("wrong", state, endpoint, callback), listOf(state, "wrong", endpoint, callback),
         listOf(state, state, "https://other.example/logout", callback), listOf(state, state, endpoint, "$callback/other")))
      {
         val attempt = OAuth2LogoutAttempt()
         attempt.begin(binding, endpoint, state)
         assertFalse(attempt.consume(binding, values[0], values[1], values[2], values[3]))
         assertFalse(attempt.consume(binding, state, state, endpoint, callback))
      }
   }

   @Test fun stateOnlySuccessIsSingleUseAndCancellationOrExpiryCannotBeRevived()
   {
      var now = 0L
      val attempt = OAuth2LogoutAttempt { now }
      attempt.begin(binding, endpoint, state)
      assertTrue(attempt.consume(binding, state, state, endpoint, callback))
      assertFalse(attempt.consume(binding, state, state, endpoint, callback))
      attempt.begin(binding, endpoint, state)
      attempt.cancel()
      assertFalse(attempt.consume(binding, state, state, endpoint, callback))
      attempt.begin(binding, endpoint, state)
      now = 300001
      assertFalse(attempt.consume(binding, state, state, endpoint, callback))
   }

   @Test fun materialMustMatchIndependentDiscoveryAndCannotRedirectHintElsewhere()
   {
      fun data(issuer: String = login.issuer, url: String = endpoint, redirect: String = callback, hint: String = "synthetic.hint.canary") =
         NativeLogoutData(issuer, url, redirect, hint)
      assertTrue(validOAuth2LogoutData(data(), binding, login.issuer, endpoint))
      assertFalse(validOAuth2LogoutData(data(), binding, "https://other.example", endpoint))
      assertFalse(validOAuth2LogoutData(data(url = "https://other.example/logout"), binding, login.issuer, endpoint))
      assertFalse(validOAuth2LogoutData(data(redirect = "$callback/other"), binding, login.issuer, endpoint))
      for(url in listOf("http://identity.example/logout", "https://user@identity.example/logout", "$endpoint#secret", "$endpoint?unexpected=value"))
         assertFalse(validOAuth2LogoutData(data(url = url), binding, login.issuer, url))
      assertFalse(validOAuth2LogoutData(data(hint = " "), binding, login.issuer, endpoint))
      assertFalse(data().toString().contains("canary"))
   }

   @Test fun loginCallbackRegistrationDoesNotEnableProviderLogout()
   {
      val metadata = OAuth2AuthenticationMetaData("company", values = OAuth2AuthenticationMetaData.Values(clientId = "client", scopes = "openid",
         nativeSessionLogin = OAuth2AuthenticationMetaData.NativeSessionLogin(OAuth2AuthenticationMetaData.SESSION_FLOW, "S256",
            login.issuer, login.discoveryUrl, listOf(callback), "login", "REQUIRED")))
      assertNull(oauth2LogoutBinding(metadata, login.server, "io.qrun.test"))
      val configured = metadata.copy(values = metadata.values.copy(nativeSessionLogout =
         OAuth2AuthenticationMetaData.NativeSessionLogout(OAuth2AuthenticationMetaData.LOGOUT_FLOW, listOf(callback))))
      assertEquals(binding, oauth2LogoutBinding(configured, login.server, "io.qrun.test"))
      assertNull(oauth2LogoutBinding(configured.copy(values = configured.values.copy(nativeSessionLogout =
         OAuth2AuthenticationMetaData.NativeSessionLogout("UNKNOWN", listOf(callback)))), login.server, "io.qrun.test"))
   }
}
