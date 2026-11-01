/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.OAuth2AuthenticationMetaData
import com.kingsrook.qqq.frontend.android.core.controllers.UiApiCompatibilityException
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.connectivity.ConnectionBuilder
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/*******************************************************************************
 ** Hosts/tests may supply discovery transport; returned metadata is still validated before authorization.
 *******************************************************************************/
val LocalOAuth2Discovery = staticCompositionLocalOf<suspend (OAuth2LoginBinding) -> AuthorizationServiceConfiguration> { ::discoverOAuth2 }

/*******************************************************************************
 ** Browser authorization is standard AppAuth; API1.10 exchanges the code and validates the retained original nonce.
 *******************************************************************************/
@Composable
fun QOAuth2Login(viewModel: QViewModel, metadata: OAuth2AuthenticationMetaData, modifier: Modifier = Modifier) {
   val context = LocalContext.current
   val binding = remember(metadata, viewModel.environment.baseUrl, context.packageName) {
      oauth2LoginBinding(metadata, viewModel.environment.baseUrl, context.packageName)
   }
   val service = remember(context, binding) { AuthorizationService(context) }
   val discover = LocalOAuth2Discovery.current
   val scope = rememberCoroutineScope()
   DisposableEffect(viewModel, service, binding)
   {
      onDispose {
         service.dispose()
         if (context.activity()?.isChangingConfigurations != true) viewModel.cancelOAuth2Login()
      }
   }
   val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      val response = runCatching { result.data?.let(AuthorizationResponse::fromIntent) }.getOrNull()
      if (result.resultCode != Activity.RESULT_OK || response == null || binding == null) {
         viewModel.cancelOAuth2Login("Sign-in was cancelled or could not be completed. Please try again.")
      } else {
         viewModel.completeOAuth2Login(
            binding, response.state, response.request.state, response.request.nonce,
            response.request.clientId, response.request.redirectUri.toString(), response.request.prompt, response.authorizationCode
         )
      }
   }
   Column(modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp))
   {
      Text("Sign in")
      if (binding == null) {
         Text("This server has not configured compatible native browser sign-in with required nonce validation. Ask your administrator to enable it.")
      } else {
         Text("Continue in your browser to sign in with your organization.")
         Button(
            modifier = Modifier.testTag("oauth2.signIn"), enabled = viewModel.oauth2LoginPhase == OAuth2LoginPhase.READY,
            onClick = {
               val generation = viewModel.prepareOAuth2Login(binding)
               if (generation != null) scope.launch {
                  try {
                     viewModel.qqqRepository.verifyNativeSessionLogin()
                     val configuration = discover(binding)
                     check(validOAuth2Configuration(configuration, binding))
                     if (viewModel.isCurrentOAuth2Login(generation, binding)) {
                        val request = AuthorizationRequest.Builder(
                           configuration, binding.clientId, ResponseTypeValues.CODE,
                           Uri.parse(binding.redirectUri)
                        ).setScope(binding.scopes).setPrompt(binding.prompt).setResponseMode("query").build()
                        check(request.codeVerifierChallengeMethod == "S256")
                        if (viewModel.beginOAuth2Browser(
                              generation, binding, requireNotNull(request.state),
                              requireNotNull(request.nonce), requireNotNull(request.codeVerifier)
                           )
                        )
                           launcher.launch(service.getAuthorizationRequestIntent(request))
                     }
                  } catch (exception: CancellationException) {
                     if (viewModel.isCurrentOAuth2Login(generation, binding) && viewModel.oauth2LoginPhase == OAuth2LoginPhase.PREPARING)
                        viewModel.cancelOAuth2Login()
                     throw exception
                  } catch (exception: UiApiCompatibilityException) {
                     if (viewModel.isCurrentOAuth2Login(generation, binding))
                        viewModel.cancelOAuth2Login("This server does not support native sign-in with required nonce validation. Ask your administrator to upgrade or enable it.")
                  } catch (exception: Exception) {
                     if (viewModel.isCurrentOAuth2Login(generation, binding))
                        viewModel.cancelOAuth2Login("Browser sign-in is unavailable. Check your connection, browser and server configuration.")
                  }
               }
            }) { Text(if (viewModel.oauth2LoginPhase == OAuth2LoginPhase.READY) "Sign in with organization" else "Signing in…") }
      }
      viewModel.oauth2LoginMessage?.let { Text(it, Modifier.testTag("oauth2.message")) }
   }
}

/*******************************************************************************
 ** AppAuth parses discovery. Restrict its transport and compare issuer to the explicit semantic contract.
 *******************************************************************************/
private suspend fun discoverOAuth2(binding: OAuth2LoginBinding): AuthorizationServiceConfiguration = suspendCancellableCoroutine { continuation ->
   val connection = ConnectionBuilder { uri ->
      require(uri.toString() == binding.discoveryUrl && isOAuth2HttpsUrl(uri.toString()))
      (URL(uri.toString()).openConnection() as HttpURLConnection).apply {
         instanceFollowRedirects = false
         connectTimeout = 10_000
         readTimeout = 10_000
      }
   }
   AuthorizationServiceConfiguration.fetchFromUrl(Uri.parse(binding.discoveryUrl), { configuration, _ ->
      if (continuation.isActive) {
         if (configuration != null && validOAuth2Configuration(configuration, binding))
            continuation.resume(configuration)
         else continuation.resumeWithException(IllegalStateException("Invalid identity provider configuration"))
      }
   }, connection)
}

/*******************************************************************************
 ** Bind discovered endpoints to the advertised issuer and require direct HTTPS before authorization.
 *******************************************************************************/
private fun validOAuth2Configuration(configuration: AuthorizationServiceConfiguration, binding: OAuth2LoginBinding): Boolean =
   configuration.discoveryDoc?.issuer?.toString() == binding.issuer &&
      isOAuth2HttpsUrl(configuration.authorizationEndpoint.toString()) && isOAuth2HttpsUrl(configuration.tokenEndpoint.toString())

/*******************************************************************************
 ** Unwrap Android context wrappers until the external browser can use the owning activity.
 *******************************************************************************/
private fun Context.activity(): Activity? = when (this) {
   is Activity -> this
   is ContextWrapper -> baseContext.activity()
   else -> null
}
