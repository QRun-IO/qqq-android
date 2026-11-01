/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication

import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.NativeLogoutData
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.OAuth2AuthenticationMetaData

enum class OAuth2LogoutPhase { IDLE, PREPARING, BROWSER, FINISHED }

/** Contains public configuration only; login registration alone never authorizes a logout callback. */
data class OAuth2LogoutBinding(val login: OAuth2LoginBinding, val postLogoutRedirectUri: String)

/** An optional explicit capability, independent of the host's choice of when to invoke it. */
fun oauth2LogoutBinding(metadata: OAuth2AuthenticationMetaData, server: String, packageName: String): OAuth2LogoutBinding?
{
   val login = oauth2LoginBinding(metadata, server, packageName) ?: return null
   val logout = metadata.values.nativeSessionLogout ?: return null
   if(logout.flow != OAuth2AuthenticationMetaData.LOGOUT_FLOW || login.redirectUri !in logout.postLogoutRedirectUris) return null
   return OAuth2LogoutBinding(login, login.redirectUri)
}

/** Compare the server response to independent discovery and the originally selected server/provider. */
fun validOAuth2LogoutData(data: NativeLogoutData, binding: OAuth2LogoutBinding,
   discoveryIssuer: String?, discoveryEndSessionEndpoint: String?): Boolean =
   data.issuer == binding.login.issuer && discoveryIssuer == binding.login.issuer &&
      data.endSessionEndpoint == discoveryEndSessionEndpoint && isOAuth2HttpsUrl(data.endSessionEndpoint) &&
      data.postLogoutRedirectUri == binding.postLogoutRedirectUri && data.idTokenHint.isNotBlank() &&
      data.idTokenHint.length <= 32768 && data.idTokenHint.none { it.isWhitespace() || it.isISOControl() }

/** Single-use state-only completion. No token or provider hint is retained here or in saved state. */
class OAuth2LogoutAttempt(private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 })
{
   private class Pending(val binding: OAuth2LogoutBinding, val endpoint: String, val state: String, val startedAt: Long)
   private var pending: Pending? = null

   fun begin(binding: OAuth2LogoutBinding, endpoint: String, state: String)
   {
      require(state.length >= 16 && isOAuth2HttpsUrl(endpoint))
      check(pending == null)
      pending = Pending(binding, endpoint, state, nowMillis())
   }

   /** A rejected response consumes the attempt too; a duplicate can never change the outcome. */
   fun consume(binding: OAuth2LogoutBinding, state: String?, requestState: String?,
      requestEndpoint: String?, requestRedirect: String?): Boolean
   {
      val current = pending ?: return false
      pending = null
      return nowMillis() - current.startedAt in 0..300_000 && binding == current.binding &&
         state == current.state && requestState == current.state && requestEndpoint == current.endpoint &&
         requestRedirect == current.binding.postLogoutRedirectUri
   }

   fun cancel() { pending = null }
}
