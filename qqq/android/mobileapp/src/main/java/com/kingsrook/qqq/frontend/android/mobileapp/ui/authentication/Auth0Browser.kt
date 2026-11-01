/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.auth0.android.Auth0
import com.auth0.android.authentication.AuthenticationException
import com.auth0.android.callback.Callback
import com.auth0.android.provider.WebAuthProvider
import com.auth0.android.result.Credentials

/*******************************************************************************
 ** Platform boundary: production always uses Auth0's external browser implementation.
 *******************************************************************************/
interface Auth0Browser {
   /*******************************************************************************
    ** Launch the configured provider through the platform browser boundary and return its result to the owning attempt.
    *******************************************************************************/
   fun login(context: Context, account: Auth0, audience: String, scheme: String, callback: Callback<Credentials, AuthenticationException>)

   /*******************************************************************************
    ** Request provider sign-out through the platform browser boundary without recreating a QQQ session.
    *******************************************************************************/
   fun logout(context: Context, account: Auth0, scheme: String, callback: Callback<Void?, AuthenticationException>)
}

val LocalAuth0Browser = staticCompositionLocalOf<Auth0Browser> {
   object : Auth0Browser {
      /*******************************************************************************
       ** Launch the configured provider through the platform browser boundary and return its result to the owning attempt.
       *******************************************************************************/
      override fun login(context: Context, account: Auth0, audience: String, scheme: String, callback: Callback<Credentials, AuthenticationException>) {
         WebAuthProvider.login(account).withAudience(audience).withScheme(scheme).start(context, callback)
      }

      /*******************************************************************************
       ** Request provider sign-out through the platform browser boundary without recreating a QQQ session.
       *******************************************************************************/
      override fun logout(context: Context, account: Auth0, scheme: String, callback: Callback<Void?, AuthenticationException>) {
         WebAuthProvider.logout(account).withScheme(scheme).start(context, callback)
      }
   }
}
