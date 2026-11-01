/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication

import java.net.URI
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.OAuth2AuthenticationMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.isValidExpectedNonce

/*******************************************************************************
 ** Expose one native login lifecycle so recreation cannot launch competing browser exchanges.
 *******************************************************************************/
enum class OAuth2LoginPhase { READY, PREPARING, BROWSER, EXCHANGING }

/*******************************************************************************
 ** Identity of one server-advertised login configuration, never inferred from a provider brand.
 *******************************************************************************/
data class OAuth2LoginBinding(
   val server: String,
   val providerName: String,
   val issuer: String,
   val discoveryUrl: String,
   val clientId: String,
   val scopes: String,
   val redirectUri: String,
   val prompt: String,
   val nonceValidation: String = ""
)

/*******************************************************************************
 ** Only an explicit compatible semantic grant enables sign-in; a bearer client is never a fallback.
 *******************************************************************************/
fun oauth2LoginBinding(metadata: OAuth2AuthenticationMetaData, server: String, packageName: String): OAuth2LoginBinding? {
   val grant = metadata.values.nativeSessionLogin ?: return null
   val redirect = oauth2RedirectUri(packageName)
   if (grant.flow != OAuth2AuthenticationMetaData.SESSION_FLOW || grant.codeChallengeMethod != "S256" || grant.prompt != "login" ||
      grant.nonceValidation != "REQUIRED" ||
      redirect !in grant.redirectUris || !isOAuth2HttpsUrl(server) || !isOAuth2HttpsUrl(grant.issuer) ||
      !isOAuth2HttpsUrl(grant.discoveryUrl) || metadata.values.clientId.isBlank() ||
      "openid" !in metadata.values.scopes.split(' ')
   ) return null
   return OAuth2LoginBinding(
      server, metadata.name, grant.issuer, grant.discoveryUrl, metadata.values.clientId,
      metadata.values.scopes, redirect, grant.prompt, grant.nonceValidation
   )
}

/*******************************************************************************
 ** One in-memory browser attempt. Activity recreation retains it; process death requires a new login.
 *******************************************************************************/
class OAuth2LoginAttempt(private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 }) {
   /*******************************************************************************
    ** Keep the original state, nonce, verifier and provider binding only for the lifetime of one login attempt.
    *******************************************************************************/
   private class Pending(
      val binding: OAuth2LoginBinding, val state: String, val nonce: String,
      val verifier: String, val startedAt: Long
   )

   private var pending: Pending? = null

   /*******************************************************************************
    ** Bind fresh state, nonce and PKCE verifier to the advertised configuration before opening the browser.
    *******************************************************************************/
   fun begin(binding: OAuth2LoginBinding, state: String, nonce: String, verifier: String) {
      require(state.length >= 16 && isValidExpectedNonce(nonce) && binding.nonceValidation == "REQUIRED")
      require(Regex("[A-Za-z0-9._~-]{43,128}").matches(verifier))
      check(pending == null)
      pending = Pending(binding, state, nonce, verifier, nowMillis())
   }

   /*******************************************************************************
    ** Consume even rejected callbacks so a stale or failed attempt cannot later establish a session.
    *******************************************************************************/
   fun consume(
      binding: OAuth2LoginBinding, state: String?, requestState: String?, requestNonce: String?,
      requestClientId: String, requestRedirectUri: String, requestPrompt: String?, code: String?
   ): Exchange? {
      val attempt = pending ?: return null
      pending = null
      val age = nowMillis() - attempt.startedAt
      if (age !in 0..300_000 || binding != attempt.binding || state != attempt.state || requestState != attempt.state ||
         requestNonce != attempt.nonce || requestClientId != binding.clientId || requestRedirectUri != binding.redirectUri ||
         requestPrompt != binding.prompt || code.isNullOrBlank()
      ) return null
      return Exchange(code, attempt.verifier, attempt.binding.redirectUri, attempt.nonce)
   }

   /*******************************************************************************
    ** Drop the pending exchange material so an abandoned callback cannot later establish identity.
    *******************************************************************************/
   fun cancel() {
      pending = null
   }

   /*******************************************************************************
    ** Not a data class: credentials must not appear in an automatically generated toString().
    *******************************************************************************/
   class Exchange(val code: String, val verifier: String, val redirectUri: String, val expectedNonce: String)
}

/*******************************************************************************
 ** Fixed package identity is the callback transport; the server must explicitly advertise this exact URI.
 *******************************************************************************/
fun oauth2RedirectUri(packageName: String): String = "$packageName.oauth://callback/oauth2redirect"

/*******************************************************************************
 ** Match only this application's exact callback authority/path and reject opaque or fragment-bearing results.
 *******************************************************************************/
fun isOAuth2Callback(uri: String, packageName: String): Boolean = runCatching {
   val actual = URI(uri)
   val expected = URI(oauth2RedirectUri(packageName))
   actual.scheme == expected.scheme && actual.rawAuthority == expected.rawAuthority && actual.rawPath == expected.rawPath &&
      actual.rawFragment == null && !actual.isOpaque
}.getOrDefault(false)

/*******************************************************************************
 ** Reject credentials, query strings and fragments in configured issuer/server URLs.
 *******************************************************************************/
fun isOAuth2HttpsUrl(value: String): Boolean = runCatching {
   val uri = URI(value)
   uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.rawQuery == null &&
      uri.rawFragment == null && uri.port in -1..65535 && uri.port != 0 && !uri.isOpaque
}.getOrDefault(false)
