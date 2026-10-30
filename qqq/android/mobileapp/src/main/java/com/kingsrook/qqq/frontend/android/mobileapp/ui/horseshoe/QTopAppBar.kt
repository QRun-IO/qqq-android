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

package com.kingsrook.qqq.frontend.android.mobileapp.ui.horseshoe

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat.getSystemService
import com.kingsrook.qqq.frontend.android.mobileapp.ui.QNavigator
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel

/***************************************************************************
 **
 ***************************************************************************/
@OptIn(ExperimentalMaterial3Api::class) // needed for TopAppBar
@Composable
fun QTopAppBar(qViewModel: QViewModel, defaultTitle: String = "A QQQ Android Application", navMenuCallback: (() -> Unit)? = null, qNavigator: QNavigator? = null, showNetworkDialog: Boolean = true)
{
   val isMenuDialogOpen = remember { mutableStateOf(false) }

   val title = if(qNavigator?.titleStack?.isEmpty() == false) qNavigator.titleStack.get(qNavigator.titleStack.size - 1) ?: defaultTitle else defaultTitle

   TopAppBar(
      colors = TopAppBarDefaults.topAppBarColors(
         containerColor = MaterialTheme.colorScheme.primaryContainer,
         titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
      ),
      title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("topAppBar.title")) },
      navigationIcon =
      {
         /////////////////////////////////////////////////////////
         // this was for the left-menu - but - not using today! //
         /////////////////////////////////////////////////////////
         // navMenuCallback?.let()
         // {
         //    IconButton(onClick = { it() })
         //    {
         //       Icon(imageVector = Icons.Filled.Menu, contentDescription = "Open Menu")
         //    }
         // }

         key(qNavigator?.navDepth ?: 0)
         {
            qNavigator?.let()
            {
               IconButton(onClick = { qNavigator.popStack() },
                  enabled = !qNavigator.atHome,
                  modifier = Modifier.focusProperties { canFocus = false }
               )
               {
                  if(qNavigator.atHome)
                  {
                     Icon(imageVector = Icons.Filled.Home, contentDescription = "At Home", tint = Color.Black)
                  }
                  else
                  {
                     Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go Back")
                  }
               }
            }
         }
      },
      actions =
      {
         qViewModel.topBarStatusText?.let()
         {
            Text(it)
         }

         IconButton(onClick = { isMenuDialogOpen.value = true }, modifier = Modifier
            .focusProperties { canFocus = false }
            .testTag("topBar.menuIcon"))
         {
            Icon(imageVector = Icons.Default.Menu, contentDescription = "Information")
         }
      }
   )

   if(showNetworkDialog)
   {
      NetworkConnectionDialog(qViewModel)
   }

   if(isMenuDialogOpen.value)
   {
      MenuDialog(qViewModel, defaultTitle, isMenuDialogOpen)
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

