/*
 * SPDX-License-Identifier: Apache-2.0
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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kingsrook.qqq.frontend.android.core.model.metadata.QInstance
import com.kingsrook.qqq.frontend.android.mobileapp.ui.QNavigator
import com.kingsrook.qqq.frontend.android.mobileapp.ui.LocalNavigationBackAction
import com.kingsrook.qqq.frontend.android.mobileapp.ui.apps.QAppHome
import com.kingsrook.qqq.frontend.android.mobileapp.ui.common.FullSizedAllCenteredColumn
import com.kingsrook.qqq.frontend.android.mobileapp.ui.processes.QProcessHome
import com.kingsrook.qqq.frontend.android.mobileapp.ui.utils.LoadStateView
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.LoadState
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel
import kotlinx.serialization.Serializable

/*******************************************************************************
 ** Composable that wraps our entire application - providing a login form
 ** when needed, else showing the app.
 *******************************************************************************/
@Composable
fun QApplicationWrapper(qViewModel: QViewModel, modifier: Modifier = Modifier, qNavigator: QNavigator? = null) {
   val qInstanceLoadState = qViewModel.qInstanceLoadState
   val loadStates = listOf(qInstanceLoadState)

   val navController = qNavigator?.navController ?: rememberNavController()
   val hostBackAction = LocalNavigationBackAction.current
   BackHandler(enabled = qNavigator?.atHome == false) {
      if (hostBackAction != null) hostBackAction() else qNavigator?.popStack()
   }

   LoadStateView(
      loadStates,
      modifier,
      reloader = com.kingsrook.qqq.frontend.android.mobileapp.ui.utils.Reloader("Retry loading apps", { qViewModel.loadQInstance() }),
      loadingText = "Loading apps for environment: ${qViewModel.environment.label}"
   )
   {
      val qInstance = (qInstanceLoadState as LoadState.Success<QInstance>).value
      Column(modifier = Modifier)
      {
         NavHost(navController, startDestination = HomeRoute, modifier = modifier) {

            composable<HomeRoute>()
            {
               QAppHome(qInstance, modifier, qNavigator = qNavigator)
            }

            composable<AppRoute>()
            { backStackEntry ->
               val appRoute: AppRoute = backStackEntry.toRoute()
               val app = qInstance.apps?.get(appRoute.name)
               if (app != null) {
                  QAppHome(qInstance, modifier, app = app, qNavigator = qNavigator)
               } else {
                  NotFound(qNavigator, "App not found (name=${appRoute.name})")
               }
            }

            composable<ProcessRoute>()
            { backStackEntry ->
               val processRoute: ProcessRoute = backStackEntry.toRoute()
               val process = qInstance.processes?.get(processRoute.name)
               if (process != null) {
                  QProcessHome(qViewModel, qInstance, modifier)
               } else {
                  NotFound(qNavigator, "Process not found (name=${processRoute.name})")
               }
            }

         }
      }
   }
}

/*******************************************************************************
 *******************************************************************************/
@Composable
private fun NotFound(qNavigator: QNavigator?, message: String) {
   FullSizedAllCenteredColumn()
   {
      Text(message, modifier = Modifier.padding(bottom = 16.dp))
      Button(onClick = { qNavigator?.goHome() })
      {
         Text("Return Home")
      }
   }
}

/*******************************************************************************
 ** route/data (singleton) to represent the home screen
 *******************************************************************************/
@Serializable
object HomeRoute

/*******************************************************************************
 ** route/data to represent an app
 *******************************************************************************/
@Serializable
data class AppRoute(val name: String)

/*******************************************************************************
 ** route/data to represent a process
 *******************************************************************************/
@Serializable
data class ProcessRoute(val name: String, val appName: String? = null)

