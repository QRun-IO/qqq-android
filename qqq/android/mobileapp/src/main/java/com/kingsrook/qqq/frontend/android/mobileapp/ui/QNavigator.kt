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

package com.kingsrook.qqq.frontend.android.mobileapp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController
import com.kingsrook.qqq.frontend.android.core.model.metadata.QInstance
import com.kingsrook.qqq.frontend.android.mobileapp.ui.horseshoe.AppRoute
import com.kingsrook.qqq.frontend.android.mobileapp.ui.horseshoe.HomeRoute
import com.kingsrook.qqq.frontend.android.mobileapp.ui.horseshoe.ProcessRoute
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.LoadState
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.ProcessViewModel
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel

/***************************************************************************
 **
 ***************************************************************************/
class QNavigator(var navController: NavHostController?, val qViewModel: QViewModel) : ViewModel()
{
   var navDepth: Int by mutableStateOf(0)
      private set

   var atHome: Boolean by mutableStateOf(true)
      private set

   var titleStack: List<String?> by mutableStateOf(mutableListOf())
      private set

   init
   {
      navController?.addOnDestinationChangedListener()
      { controller, destination, _ ->

         /////////////////////////////////////////////////////////////////
         // clear out the top bar status text after upon any navigation //
         /////////////////////////////////////////////////////////////////
         qViewModel.topBarStatusText = null
      }
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun navigateToApp(name: String, label: String)
   {
      navigateToRoute(AppRoute(name), label)
   }

   /***************************************************************************
    ** As part of preserving the process view-model in case of activity restarts
    ** (e.g., when screen orientation changes) - manage the lifecycle of the
    ** active process view model in here - activating a new one when navigating.
    ***************************************************************************/
   fun navigateToProcess(name: String, label: String, processViewModel: ProcessViewModel)
   {
      processViewModel.reset()
      processViewModel.qqqRepository = qViewModel.qqqRepository
      processViewModel.qInstance = (qViewModel.qInstanceLoadState as LoadState.Success<QInstance>).value
      processViewModel.processName = name
      processViewModel.qViewModel = qViewModel
      processViewModel.supportedCustomWidgetTypes = qViewModel.supportedCustomWidgetTypes

      processViewModel.closeProcessCallback =
         {
            qViewModel.activeProcessViewModel = null
            this.popStack()
         }

      processViewModel.doInit()

      qViewModel.activeProcessViewModel = processViewModel

      navigateToRoute(ProcessRoute(name), label)
   }

   /***************************************************************************
    **
    ***************************************************************************/
   private fun navigateToRoute(route: Any, label: String)
   {
      navDepth++
      atHome = false

      val titleStackMutation = titleStack.toMutableList()
      titleStackMutation.add(label)
      titleStack = titleStackMutation

      navController?.navigate(route)
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun popStack()
   {
      qViewModel.activeProcessViewModel?.let { it.cancel(); return }
      navDepth--
      if(navController?.popBackStack() != true)
      {
         navController?.navigate(HomeRoute)
         navDepth = 0
      }

      val titleStackMutation = titleStack.toMutableList()
      titleStackMutation.removeAt(titleStackMutation.size - 1)
      titleStack = titleStackMutation

      if(navDepth <= 0)
      {
         atHome = true
      }
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun goHome(cancelActiveProcess: Boolean = true)
   {
      if(!cancelActiveProcess)
      {
         qViewModel.activeProcessViewModel?.reset()
         qViewModel.activeProcessViewModel = null
      }
      qViewModel.activeProcessViewModel?.let { process ->
         process.closeProcessCallback = { qViewModel.activeProcessViewModel = null; goHome() }
         process.cancel()
         return
      }
      while(!atHome)
      {
         popStack()
      }
   }

}
