/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import net.openid.appauth.AuthorizationManagementActivity

/*******************************************************************************
 ** Validate explicit intents too: Android intent filters alone do not protect an exported callback.
 *******************************************************************************/
class OAuth2RedirectActivity : Activity() {
   /*******************************************************************************
    ** Deliver only the expected native callback transport to AppAuth, then finish this transient activity.
    *******************************************************************************/
   override fun onCreate(savedInstanceState: Bundle?) {
      super.onCreate(savedInstanceState)
      val callback = intent.data
      if (intent.action == Intent.ACTION_VIEW && callback != null && isOAuth2Callback(callback.toString(), packageName) &&
         listOf("state", "code", "error").all { callback.getQueryParameters(it).size <= 1 } &&
         callback.getQueryParameter("state")?.isNotBlank() == true &&
         (callback.getQueryParameter("code") == null || callback.getQueryParameter("error") == null)
      ) {
         startActivity(AuthorizationManagementActivity.createResponseHandlingIntent(this, callback))
      }
      finish()
   }
}
