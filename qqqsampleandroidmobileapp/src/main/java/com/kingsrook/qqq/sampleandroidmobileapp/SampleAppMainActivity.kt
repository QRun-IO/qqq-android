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

package com.kingsrook.qqq.sampleandroidmobileapp

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.kingsrook.qqq.frontend.android.core.model.Environment
import com.kingsrook.qqq.frontend.android.mobileapp.QMobileAppMain
import com.kingsrook.qqq.frontend.android.mobileapp.container.QAppContainer
import com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication.QAuth0Service
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel
import com.kingsrook.qqq.sampleandroidmobileapp.ui.theme.AppTheme

/***************************************************************************
 **
 ***************************************************************************/
class SampleAppMainActivity : ComponentActivity()
{
   /***************************************************************************
    **
    ***************************************************************************/
   override fun onCreate(savedInstanceState: Bundle?)
   {
      /////////////////////////////////////////////////////////////////////////////////////////////
      // configure your application:                                                             //
      // availableEnvironments: list of Environments - labels & Urls of your application servers //
      //                                                                                         //
      // and, ideally, you can define these 4 settings (see below) in a strings.xml values file  //
      // title: text displayed in topAppBar                                                      //
      // scheme: unique identifier for your app, used by URL-scheme-based callbacks              //
      // applicationName: name sent to your backend server to identify this frontend application //
      // applicationVersion: version that accompanies applicationName.                           //
      // applicationBuildTimestamp: timestamp of the app's build (e.g., injected by CD pipeline) //
      /////////////////////////////////////////////////////////////////////////////////////////////
      QAppContainer.availableEnvironments = listOf(
         Environment("Production", "https://live.coldtrack.com/"),
         Environment("Staging", "https://live.coldtrack-staging.com/"),
         Environment("Development", "https://live.coldtrack-dev.com/"),
         Environment("Local Dev", "http://192.168.4.122:8000/"),
         Environment("Bad URL", "http://no.such.domain.dot.dot/"),
      )
      val splashResourceId: Int? = null // R.drawable.kingsrook_logo

      ///////////////////////////////////////////////////////////
      // boilerplate - paste in below your app's configuration //
      ///////////////////////////////////////////////////////////
      super.onCreate(savedInstanceState)
      enableEdgeToEdge()
      setContent()
      {
         val title = stringResource(R.string.app_title)
         QAuth0Service.scheme = stringResource(R.string.app_scheme)
         QViewModel.applicationName = stringResource(R.string.app_name)
         QViewModel.applicationVersion = stringResource(R.string.app_version)
         QViewModel.applicationBuildTimestamp = stringResource(R.string.application_build_timestamp)

         QViewModel.dataStore = LocalContext.current.dataStore
         val qViewModel: QViewModel = viewModel(factory = QViewModel.factory)

         val navController = rememberNavController()
         qViewModel.setNavController(navController)

         AppTheme()
         {
            QMobileAppMain(qViewModel, title = title, if(splashResourceId == null) null else painterResource(splashResourceId))
         }
      }
   }
}

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
