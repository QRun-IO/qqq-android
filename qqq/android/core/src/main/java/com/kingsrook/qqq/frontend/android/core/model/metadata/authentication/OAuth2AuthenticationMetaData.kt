/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.core.model.metadata.authentication

import kotlinx.serialization.Serializable

/** UI API OAuth2 configuration. Native bearer and native stored-session grants are distinct. */
@Serializable
data class OAuth2AuthenticationMetaData(
   override val name: String,
   override val type: String = TYPE,
   val values: Values
) : BaseAuthenticationMetaData
{
   @Serializable
   data class Values(val clientId: String = "", val baseUrl: String = "", val scopes: String = "",
      val nativeClientId: String? = null, val nativeClientBaseUrl: String? = null,
      val bearerTokensAccepted: Boolean = false, val nativeSessionLogin: NativeSessionLogin? = null,
      val nativeSessionLogout: NativeSessionLogout? = null)

   @Serializable
   data class NativeSessionLogin(val flow: String = "", val codeChallengeMethod: String = "",
      val issuer: String = "", val discoveryUrl: String = "", val redirectUris: List<String> = emptyList(), val prompt: String = "",
      val nonceValidation: String = "")

   @Serializable
   data class NativeSessionLogout(val flow: String = "", val postLogoutRedirectUris: List<String> = emptyList())

   companion object
   {
      const val TYPE = "OAUTH2"
      const val SESSION_FLOW = "AUTHORIZATION_CODE_PKCE_SESSION"
      const val LOGOUT_FLOW = "OIDC_RP_INITIATED_LOGOUT"
   }
}
