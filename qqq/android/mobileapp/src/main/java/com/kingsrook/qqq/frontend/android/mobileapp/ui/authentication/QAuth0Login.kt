/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2004-2024.  Kingsrook, LLC
 * 651 N Broad St Ste 205 # 6917 | Middletown DE 19709 | United States
 * contact@kingsrook.com
 * https://github.com/Kingsrook/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication

import android.util.Log
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.auth0.android.Auth0
import com.auth0.android.authentication.AuthenticationException
import com.auth0.android.callback.Callback
import com.auth0.android.provider.WebAuthProvider
import com.auth0.android.result.Credentials
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.Auth0AuthenticationMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.BaseAuthenticationMetaData
import com.kingsrook.qqq.frontend.android.core.utils.firstN
import com.kingsrook.qqq.frontend.android.mobileapp.ui.common.FullScreenLoading
import com.kingsrook.qqq.frontend.android.mobileapp.ui.common.FullSizedAllCenteredColumn
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.LoadState
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel
import kotlinx.coroutines.delay

private const val TAG = "QAuth0Form"

/***************************************************************************
 ** form for logging in using auth0
 ***************************************************************************/
@Composable
fun QAuth0Login(qViewModel: QViewModel, modifier: Modifier = Modifier)
{
   val authenticationMetaData = (qViewModel.authenticationMetaDataLoadState as LoadState.Success<BaseAuthenticationMetaData>).value as Auth0AuthenticationMetaData
   val auth0Instance: Auth0 = QAuth0Service.makeAuth0Instance(qViewModel)

   val auth0Success = remember { mutableStateOf(false) }
   val auth0Failure = remember { mutableStateOf(false) }

   val callback = object : Callback<Credentials, AuthenticationException>
   {
      /***************************************************************************
       **
       ***************************************************************************/
      override fun onFailure(error: AuthenticationException)
      {
         Log.w(TAG, "Auth0 Called back to failure", error)
         error.printStackTrace()
         qViewModel.logInFailed()
         auth0Failure.value = true
      }

      /***************************************************************************
       **
       ***************************************************************************/
      override fun onSuccess(result: Credentials)
      {
         Log.d(TAG, "Auth0 sign-in succeeded")
         auth0Success.value = true

         qViewModel.doSetSessionUserFullName(result.user.nickname ?: "Unknown")
         qViewModel.manageSession(result.accessToken)
      }
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun tryAgain()
   {
      /////////////////////////////////////////////////////////////////////////
      // just by resetting these flags, we should re-run the WebAuthProvider //
      /////////////////////////////////////////////////////////////////////////
      auth0Failure.value = false
      auth0Success.value = false
   }

   //////////////////////////////////////////////////////////////////////////////////////////////////////
   // do a little count-down before going to auth0 - probably just for dev, so we can see it happening //
   //////////////////////////////////////////////////////////////////////////////////////////////////////
   val countdown = remember { mutableStateOf(0) }

   LaunchedEffect(key1 = countdown.value)
   {
      if(countdown.value > 0)
      {
         delay(100)
         countdown.value = countdown.value - 1
      }
   }

   //////////////////////////////////////////////////////////////////////////////
   // pick what we show based on state - later states at the top of the switch //
   //////////////////////////////////////////////////////////////////////////////
   if(qViewModel.isAuthenticated)
   {
      Log.d(TAG, "User is authenticated - should be seeing apps now")

      FullScreenLoading("Login Successful.  You should be taken to your apps now.")
   }
   else if(auth0Success.value)
   {
      Log.d(TAG, "Auth0Success is true - looking at manageSessionLoadState now...")

      when (val state = qViewModel.manageSessionLoadState)
      {
         is LoadState.Loading ->
         {
            FullScreenLoading("Creating Application Session...")
         }

         is LoadState.Error -> Text("Error creating session: ${state.message}")
         is LoadState.Success ->
         {
            FullScreenLoading("Login Successful.  You should be taken to your apps now.")
         }
      }
   }
   else if(auth0Failure.value)
   {
      Log.d(TAG, "Auth0Failure is true - Offering try-again button")

      FullSizedAllCenteredColumn()
      {
         Text("Login failed... ", modifier = Modifier.padding(bottom = 16.dp))
         Button(onClick = { tryAgain() })
         {
            Text("Try again")
         }
      }
   }
   else if(countdown.value > 0)
   {
      Log.d(TAG, "Sleeping before Auth0 login")
      FullScreenLoading("Taking you to login in ${countdown.value} moment${if(countdown.value == 1) "" else "s"}...")
   }
   else
   {
      FullScreenLoading("Logging in...")

      Log.d(TAG, "Going to Auth0 login (WebAuthProvider)")
      WebAuthProvider
         .login(auth0Instance)
         .withAudience(authenticationMetaData.values.audience)
         .withScheme(QAuth0Service.scheme)
         .start(LocalContext.current, callback)
   }

}
