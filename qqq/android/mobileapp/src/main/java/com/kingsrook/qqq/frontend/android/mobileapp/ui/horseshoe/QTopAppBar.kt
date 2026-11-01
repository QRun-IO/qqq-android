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

package com.kingsrook.qqq.frontend.android.mobileapp.ui.horseshoe

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat.getSystemService
import com.kingsrook.qqq.frontend.android.mobileapp.ui.QNavigator
import com.kingsrook.qqq.frontend.android.mobileapp.ui.LocalNavigationBackAction
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel

/***************************************************************************
 **
 ***************************************************************************/
@OptIn(ExperimentalMaterial3Api::class) // needed for TopAppBar
@Composable
fun QTopAppBar(qViewModel: QViewModel, defaultTitle: String = "A QQQ Android Application", navMenuCallback: (() -> Unit)? = null, qNavigator: QNavigator? = null, showNetworkDialog: Boolean = true, accountOpenState: MutableState<Boolean>? = null)
{
   val accountOpen = accountOpenState ?: remember { mutableStateOf(false) }
   val hostBackAction = LocalNavigationBackAction.current
   val authenticated = qViewModel.isAuthenticated && !qViewModel.isLogoutRequested
   val businessReady = authenticated && LocalBusinessNavigationEnabled.current
   LaunchedEffect(qViewModel.isLogoutRequested)
   {
      if(qViewModel.isLogoutRequested)
      {
         accountOpen.value = false
      }
   }
   val title = if(businessReady) qNavigator?.titleStack?.lastOrNull() ?: "Apps" else defaultTitle

   TopAppBar(
      colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
      title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("topAppBar.title")) },
      navigationIcon = {
         Row {
            if(navMenuCallback != null)
               IconButton(onClick = navMenuCallback, modifier = Modifier.testTag("topBar.menuIcon"))
               { Icon(Icons.Default.Menu, "Open navigation menu") }
            if(businessReady && qNavigator?.atHome == false)
               IconButton(onClick = hostBackAction ?: qNavigator::popStack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Go Back") }
         }
      },
      actions = {
         IconButton(onClick = { accountOpen.value = true }, modifier = Modifier.testTag("topBar.accountIcon"))
         { Icon(Icons.Default.AccountCircle, "Account") }
      }
   )
   if(accountOpen.value) AccountDialog(qViewModel) { accountOpen.value = false }

   if(showNetworkDialog)
   {
      NetworkConnectionDialog(qViewModel)
   }

}



/** Legacy internet-presence prompt; hosts with server reachability monitoring can omit it. */
@Composable
private fun NetworkConnectionDialog(qViewModel: QViewModel)
{
   val networkAvailable = remember { mutableStateOf(true) }
   val connectivityManager = getSystemService(LocalContext.current, ConnectivityManager::class.java) as ConnectivityManager
   DisposableEffect(connectivityManager)
   {
      val networks = NetworkPresence()
      val request = NetworkRequest.Builder()
         .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
         .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
         .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
         .build()
      val callback = object : ConnectivityManager.NetworkCallback()
      {
         override fun onAvailable(network: Network)
         {
            networkAvailable.value = networks.onAvailable(network.networkHandle)
         }

         override fun onLost(network: Network)
         {
            networkAvailable.value = networks.onLost(network.networkHandle)
         }
      }
      connectivityManager.registerNetworkCallback(request, callback)
      onDispose { connectivityManager.unregisterNetworkCallback(callback) }
   }
   if(!networkAvailable.value)
   {
      NoNetworkDialog(qViewModel)
   }
}

