/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel
import kotlinx.coroutines.CancellationException
import net.openid.appauth.AuthorizationService
import net.openid.appauth.EndSessionRequest
import net.openid.appauth.EndSessionResponse

/*******************************************************************************
 ** Explicit host policy; default UI and ordinary sign-out never invoke global provider logout.
 *******************************************************************************/
val LocalCompanySignOutEnabled = staticCompositionLocalOf { false }

/*******************************************************************************
 ** Shares the registered receiver, but consumes only AppAuth end-session responses and state.
 *******************************************************************************/
@Composable
fun QOAuth2Logout(model: QViewModel, binding: OAuth2LogoutBinding) {
   val context = LocalContext.current
   val service = remember(context) { AuthorizationService(context) }
   val discover = LocalOAuth2Discovery.current
   val generation = model.companyLogoutGeneration
   val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      val response = runCatching { result.data?.let(EndSessionResponse::fromIntent) }.getOrNull()
      if (result.resultCode != Activity.RESULT_OK || response == null)
         model.finishCompanyLogout(generation, binding)
      else model.completeCompanyLogout(
         generation, binding, response.state, response.request.state,
         response.request.configuration.endSessionEndpoint?.toString(), response.request.postLogoutRedirectUri?.toString()
      )
   }
   DisposableEffect(service) { onDispose { service.dispose() } }
   LaunchedEffect(model, binding, generation)
   {
      if (model.companyLogoutPhase != OAuth2LogoutPhase.PREPARING) return@LaunchedEffect
      try {
         val material = model.logOutWithProvider(generation, binding)
         if (material == null) {
            model.finishCompanyLogout(generation, binding)
            return@LaunchedEffect
         }
         val configuration = discover(binding.login)
         check(
            validOAuth2LogoutData(
               material, binding, configuration.discoveryDoc?.issuer?.toString(),
               configuration.endSessionEndpoint?.toString()
            )
         )
         // The hint crosses AppAuth Intent/saved-activity and browser URL boundaries; never log this request.
         val request = EndSessionRequest.Builder(configuration).setIdTokenHint(material.idTokenHint)
            .setPostLogoutRedirectUri(Uri.parse(binding.postLogoutRedirectUri)).build()
         if (model.beginCompanyLogoutBrowser(generation, binding, material.endSessionEndpoint, requireNotNull(request.state)))
            launcher.launch(service.getEndSessionRequestIntent(request))
      } catch (exception: CancellationException) {
         // A cancelled preparation is deliberately not replayed after recreation or server changes.
         model.finishCompanyLogout(generation, binding)
         throw exception
      } catch (exception: Exception) {
         model.finishCompanyLogout(generation, binding)
      }
   }
   Column()
   {
      Text(model.logoutMessage.ifEmpty { "Signing out…" }, Modifier.testTag("oauth2.logoutMessage"))
      if (model.companyLogoutPhase == OAuth2LogoutPhase.BROWSER)
         Button(onClick = { model.finishCompanyLogout(generation, binding) }) { Text("Cancel company sign-out") }
      Button(
         enabled = !model.logoutInProgress && model.companyLogoutPhase == OAuth2LogoutPhase.FINISHED,
         onClick = model::requestLogin
      ) { Text("Log back in") }
   }
}
