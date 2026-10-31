/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2004-2024.  Kingsrook, LLC
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
 *
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.auth0.android.authentication.AuthenticationException
import com.auth0.android.callback.Callback
import com.auth0.android.result.Credentials
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.Auth0AuthenticationMetaData
import com.kingsrook.qqq.frontend.android.mobileapp.ui.common.FullScreenLoading
import com.kingsrook.qqq.frontend.android.mobileapp.ui.common.FullSizedAllCenteredColumn
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.LoadState
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel
import kotlinx.coroutines.launch

@Composable
fun QAuth0Login(qViewModel: QViewModel, modifier: Modifier = Modifier)
{
   val metadata = (qViewModel.authenticationMetaDataLoadState as? LoadState.Success)?.value as? Auth0AuthenticationMetaData ?: return
   val context = LocalContext.current
   val launcher = LocalAuth0Browser.current
   val scope = rememberCoroutineScope()
   var retry by remember { mutableIntStateOf(0) }
   var failed by remember { mutableStateOf(false) }
   var exchanging by remember { mutableStateOf(false) }
   DisposableEffect(qViewModel, qViewModel.environment.baseUrl, metadata, retry)
   {
      val attempt = qViewModel.prepareAuthentication()
      if(attempt != null)
      {
         val callback = object : Callback<Credentials, AuthenticationException>
         {
            override fun onFailure(error: AuthenticationException)
            {
               scope.launch {
                  if(qViewModel.isCurrentAuthentication(attempt))
                  {
                     qViewModel.cancelAuthentication(attempt)
                     failed = true
                  }
               }
            }
            override fun onSuccess(result: Credentials)
            {
               scope.launch {
                  if(qViewModel.isCurrentAuthentication(attempt))
                  {
                     exchanging = true
                     qViewModel.completeAuth0Login(attempt, result.accessToken, result.user.nickname ?: "Unknown")
                  }
               }
            }
         }
         try { launcher.login(context, QAuth0Service.makeAuth0Instance(qViewModel), metadata.values.audience, QAuth0Service.scheme, callback) }
         catch(exception: Exception) { qViewModel.cancelAuthentication(attempt); failed = true }
      }
      onDispose { if(attempt != null) qViewModel.cancelAuthentication(attempt) }
   }
   if(failed || (exchanging && qViewModel.manageSessionLoadState is LoadState.Error))
   {
      FullSizedAllCenteredColumn {
         Text("Sign-in failed. Please try again.")
         Button(onClick = { failed = false; exchanging = false; retry++ }) { Text("Try again") }
      }
   }
   else FullScreenLoading(if(exchanging) "Creating Application Session…" else "Logging in…")
}
