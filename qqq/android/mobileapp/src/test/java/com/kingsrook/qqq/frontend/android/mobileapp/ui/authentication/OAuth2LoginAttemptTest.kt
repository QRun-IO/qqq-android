/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication

import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.OAuth2AuthenticationMetaData
import org.junit.Assert.*
import org.junit.Test

class OAuth2LoginAttemptTest
{
   private val binding = OAuth2LoginBinding("https://qqq.example/", "company", "https://id.example/application/o/scanner/",
      "https://id.example/application/o/scanner/.well-known/openid-configuration", "server-client", "openid profile", oauth2RedirectUri("io.qrun.mobile.scanner"), "login")
   private val state = "state-random-at-least-sixteen"
   private val nonce = "nonce-random-at-least-sixteen"
   private val verifier = "v".repeat(64)

   private fun consume(attempt: OAuth2LoginAttempt, context: OAuth2LoginBinding = binding, returnedState: String? = state,
      requestState: String? = state, requestNonce: String? = nonce, client: String = binding.clientId,
      redirect: String = binding.redirectUri, prompt: String? = binding.prompt, code: String? = "authorization-code") =
      attempt.consume(context, returnedState, requestState, requestNonce, client, redirect, prompt, code)

   @Test fun successfulResponseUsesOriginalVerifierExactlyOnce()
   {
      val attempt = OAuth2LoginAttempt()
      attempt.begin(binding, state, nonce, verifier)
      val exchange = requireNotNull(consume(attempt))
      assertEquals(verifier, exchange.verifier)
      assertEquals(binding.redirectUri, exchange.redirectUri)
      assertFalse(exchange.toString().contains(verifier))
      assertNull(consume(attempt))
   }

   @Test fun staleServerProviderClientScopesAndRedirectNeverExchange()
   {
      for(context in listOf(binding.copy(server = "https://other.example/"), binding.copy(providerName = "other"),
         binding.copy(issuer = "https://other.example/"), binding.copy(discoveryUrl = "https://other.example/discovery"), binding.copy(clientId = "other"),
         binding.copy(scopes = "openid admin"), binding.copy(redirectUri = "other:/callback"), binding.copy(prompt = "none")))
      {
         val attempt = OAuth2LoginAttempt()
         attempt.begin(binding, state, nonce, verifier)
         assertNull(consume(attempt, context))
         assertNull(consume(attempt))
      }
   }

   @Test fun mismatchMissingCodeAndCancelledAttemptAreTerminal()
   {
      val checks: List<(OAuth2LoginAttempt) -> OAuth2LoginAttempt.Exchange?> = listOf(
         { consume(it, returnedState = "wrong") }, { consume(it, requestState = "wrong") },
         { consume(it, requestNonce = "wrong") }, { consume(it, client = "wrong") },
         { consume(it, redirect = "wrong:/callback") }, { consume(it, prompt = null) }, { consume(it, prompt = "none") },
         { consume(it, code = null) }, { consume(it, code = " ") })
      for(check in checks)
      {
         val attempt = OAuth2LoginAttempt()
         attempt.begin(binding, state, nonce, verifier)
         assertNull(check(attempt))
         assertNull(consume(attempt))
      }
      val attempt = OAuth2LoginAttempt()
      attempt.begin(binding, state, nonce, verifier)
      attempt.cancel()
      assertNull(consume(attempt))
      assertNull("Process death has no retained attempt", consume(OAuth2LoginAttempt()))
   }

   @Test fun expiredAttemptFailsClosed()
   {
      var now = 100L
      val attempt = OAuth2LoginAttempt { now }
      attempt.begin(binding, state, nonce, verifier)
      now += 300_001
      assertNull(consume(attempt))
   }

   @Test fun callbackHasExactPackageHostPathAndNoFragment()
   {
      val packageName = "io.qrun.mobile.scanner"
      assertTrue(isOAuth2Callback(binding.redirectUri + "?code=opaque&state=opaque", packageName))
      for(uri in listOf("io.qrun.mobile.scanner.e2e.oauth://callback/oauth2redirect", binding.redirectUri + "/",
         binding.redirectUri + "#code=opaque", "io.qrun.mobile.scanner.oauth://user@callback/oauth2redirect",
         "io.qrun.mobile.scanner.oauth://callback:80/oauth2redirect", "io.qrun.mobile.scanner.oauth://callback/%6fauth2redirect",
         "io.qrun.mobile.scanner.oauth://elsewhere/oauth2redirect", "https://callback/oauth2redirect"))
         assertFalse(uri, isOAuth2Callback(uri, packageName))
   }

   @Test fun configuredUrlsRequireTlsAndRejectEmbeddedCredentialsAndResponseData()
   {
      assertTrue(isOAuth2HttpsUrl(binding.issuer))
      for(uri in listOf("http://id.example/", "javascript:alert(1)", "https://user:password@id.example/",
         "https://id.example/?code=opaque", "https://id.example/#fragment", "https:///missing", "https://id.example:0/",
         "https://id.example:65536/")) assertFalse(uri, isOAuth2HttpsUrl(uri))
   }

   @Test fun onlyAnExplicitSupportedSessionGrantForThisPackageEnablesLogin()
   {
      val grant = OAuth2AuthenticationMetaData.NativeSessionLogin(OAuth2AuthenticationMetaData.SESSION_FLOW, "S256",
         binding.issuer, binding.discoveryUrl, listOf(binding.redirectUri), prompt = "login")
      val metadata = OAuth2AuthenticationMetaData(binding.providerName, values = OAuth2AuthenticationMetaData.Values(
         clientId = binding.clientId, scopes = binding.scopes, nativeClientId = "different-public-client", nativeSessionLogin = grant))
      assertEquals(binding, oauth2LoginBinding(metadata, binding.server, "io.qrun.mobile.scanner"))
      for(invalid in listOf(null, grant.copy(flow = "BEARER"), grant.copy(codeChallengeMethod = "plain"),
         grant.copy(prompt = ""), grant.copy(prompt = "none"), grant.copy(prompt = "select_account"), grant.copy(prompt = "login consent"),
         grant.copy(issuer = "http://id.example/"), grant.copy(discoveryUrl = "https://user@id.example/"),
         grant.copy(redirectUris = listOf("io.qrun.mobile.scanner.e2e.oauth://callback/oauth2redirect"))))
         assertNull(oauth2LoginBinding(metadata.copy(values = metadata.values.copy(nativeSessionLogin = invalid)), binding.server, "io.qrun.mobile.scanner"))
      assertNull(oauth2LoginBinding(metadata, "http://qqq.example/", "io.qrun.mobile.scanner"))
   }
}
