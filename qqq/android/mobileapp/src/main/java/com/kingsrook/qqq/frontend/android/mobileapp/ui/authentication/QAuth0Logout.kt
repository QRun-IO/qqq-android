/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2024-2024.  Kingsrook, LLC
 * 651 N Broad St Ste 205 # 6917 | Middletown DE 19709 | United States
 * contact@kingsrook.com
 * https://github.com/Kingsrook/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.auth0.android.authentication.AuthenticationException
import com.auth0.android.callback.Callback
import com.kingsrook.qqq.frontend.android.mobileapp.ui.common.FullSizedAllCenteredColumn
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel
import kotlinx.coroutines.launch

@Composable
fun QAuth0Logout(qViewModel: QViewModel, modifier: Modifier = Modifier)
{
   val context = LocalContext.current
   val launcher = LocalAuth0Browser.current
   val scope = rememberCoroutineScope()
   var providerMessage by remember { mutableStateOf("Provider sign-out has not been confirmed.") }
   LaunchedEffect(qViewModel)
   {
      // Local QQQ sign-out must not depend on a browser callback (including cancellation or failure).
      qViewModel.logOut()
      if(!qViewModel.claimAuth0ProviderLogout()) return@LaunchedEffect
      val callback = object : Callback<Void?, AuthenticationException>
      {
         override fun onFailure(error: AuthenticationException)
         { scope.launch { providerMessage = "Provider sign-out could not be confirmed. Your provider may still be signed in." } }
         override fun onSuccess(result: Void?)
         { scope.launch { providerMessage = "Provider sign-out confirmed." } }
      }
      try { launcher.logout(context, QAuth0Service.makeAuth0Instance(qViewModel), QAuth0Service.scheme, callback) }
      catch(exception: Exception) { providerMessage = "Provider sign-out could not be started." }
   }
   FullSizedAllCenteredColumn {
      Text(qViewModel.logoutMessage.ifBlank { "Signing out…" })
      Text(providerMessage)
      Button(onClick = { qViewModel.requestLogin() }, enabled = !qViewModel.logoutInProgress) { Text("Log back in") }
   }
}
