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

import android.util.Log
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.auth0.android.authentication.AuthenticationException
import com.auth0.android.callback.Callback
import com.auth0.android.provider.WebAuthProvider
import com.kingsrook.qqq.frontend.android.mobileapp.ui.common.FullScreenLoading
import com.kingsrook.qqq.frontend.android.mobileapp.ui.common.FullSizedAllCenteredColumn
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel
import kotlinx.coroutines.delay

private const val TAG = "QAuth0Logout"

/***************************************************************************
 **
 ***************************************************************************/
@Composable
fun QAuth0Logout(qViewModel: QViewModel, modifier: Modifier = Modifier)
{
   val auth0Instance = QAuth0Service.makeAuth0Instance(qViewModel);

   val auth0Success = remember { mutableStateOf(false) }

   val callback = object : Callback<Void?, AuthenticationException>
   {
      /***************************************************************************
       **
       ***************************************************************************/
      override fun onFailure(error: AuthenticationException)
      {
         Log.w(TAG, "Auth0 Logout Called back to failure", error)
      }

      /***************************************************************************
       **
       ***************************************************************************/
      override fun onSuccess(result: Void?)
      {
         Log.d(TAG, "Auth0 Logout Called back success")
         auth0Success.value = true;
         qViewModel.logOut()
      }
   }

   //////////////////////////////////////////////////////////////////////////////
   // pick what we show based on state - later states at the top of the switch //
   //////////////////////////////////////////////////////////////////////////////
   if(!qViewModel.isAuthenticated)
   {
      Log.d(TAG, "User is not authenticated, so, idk, shouldn't be in here.")

      FullSizedAllCenteredColumn()
      {
         Text("Logout Successful.", modifier = Modifier.padding(bottom = 16.dp))
         Button(onClick = { qViewModel.requestLogin() })
         {
            Text("Log back in")
         }
      }
   }
   else if(auth0Success.value)
   {
      Log.d(TAG, "Auth0Success is true - where do we go now?")
      FullScreenLoading("Completing Logout...")
   }
   else
   {
      Log.d(TAG, "Going to Auth0 logout (WebAuthProvider)")

      FullScreenLoading("Logging out...")
      WebAuthProvider.logout(auth0Instance)
         .withScheme(QAuth0Service.scheme)
         .start(LocalContext.current, callback)
   }

}
