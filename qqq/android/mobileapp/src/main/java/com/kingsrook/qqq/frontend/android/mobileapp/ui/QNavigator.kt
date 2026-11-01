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

package com.kingsrook.qqq.frontend.android.mobileapp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
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
class QNavigator(initialController: NavHostController?, val qViewModel: QViewModel) : ViewModel()
{
   var navDepth: Int by mutableStateOf(0)
      private set

   var atHome: Boolean by mutableStateOf(true)
      private set

   var titleStack: List<String?> by mutableStateOf(mutableListOf())
      private set

   var navigationRevision: Long by mutableStateOf(0L)
      private set
   var currentAppName: String? by mutableStateOf(null)
      private set

   /** A destination is applied only after the current process confirms cancellation. */
   class LeaveRequest(val title: String, val confirmLabel: String, val action: () -> Unit)
   var leaveRequest: LeaveRequest? by mutableStateOf(null)
      private set

   fun dismissLeave() { if(qViewModel.activeProcessViewModel?.cancelling != true) leaveRequest = null }

   fun requestLeave(title: String, confirmLabel: String = "Leave process", alwaysConfirm: Boolean = false, action: () -> Unit)
   {
      if(leaveRequest != null || qViewModel.logoutInProgress) return
      val process = qViewModel.activeProcessViewModel
      if(alwaysConfirm || process != null && !process.getNoMoreSteps())
         leaveRequest = LeaveRequest(title, confirmLabel, action)
      else finishLeave(action)
   }

   fun confirmLeave()
   {
      val request = leaveRequest ?: return
      val process = qViewModel.activeProcessViewModel
      if(process?.navigationBlockReason() != null || process?.cancelling == true) return
      finishLeave(request.action)
   }

   /** A second explicit choice after failed cancellation; this does not claim the server stopped. */
   fun leaveAnyway()
   {
      val request = leaveRequest ?: return
      val process = qViewModel.activeProcessViewModel ?: return
      if(!process.cancellationFailed || process.cancelling || process.navigationBlockReason() != null) return
      process.reset()
      qViewModel.activeProcessViewModel = null
      leaveRequest = null
      request.action()
   }

   private fun finishLeave(action: () -> Unit)
   {
      val process = qViewModel.activeProcessViewModel
      if(process != null && !process.getNoMoreSteps())
      {
         process.closeProcessCallback = {
            qViewModel.activeProcessViewModel = null
            leaveRequest = null
            action()
         }
         process.cancel()
      }
      else
      {
         qViewModel.activeProcessViewModel = null
         leaveRequest = null
         action()
      }
   }

   fun requestSignOut() = requestLeave("Sign out?", "Sign out", alwaysConfirm = true) { qViewModel.requestLogout() }

   /** The same guarded leave/cancel policy applies to explicitly enabled provider sign-out. */
   fun requestCompanySignOut(binding: com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication.OAuth2LogoutBinding) =
      requestLeave("Sign out of company?", "Sign out of company", alwaysConfirm = true) { qViewModel.requestCompanyLogout(binding) }

   private val destinationListener = NavController.OnDestinationChangedListener { controller, _, _ -> reconcile(controller) }
   var navController: NavHostController? = null
      set(value)
      {
         if(field === value) return
         field?.removeOnDestinationChangedListener(destinationListener)
         field = value
         value?.addOnDestinationChangedListener(destinationListener)
         if(value?.currentBackStackEntry == null) clearDestination()
      }

   init { navController = initialController }

   /** NavController is authoritative, including Android Back and restored controller state. */
   private fun reconcile(controller: NavController)
   {
      val entry = controller.currentBackStackEntry ?: return clearDestination()
      val instance = (qViewModel.qInstanceLoadState as? LoadState.Success<QInstance>)?.value
      val processName = if(entry.destination.hasRoute<ProcessRoute>()) entry.toRoute<ProcessRoute>().name else null
      val appName = if(entry.destination.hasRoute<AppRoute>()) entry.toRoute<AppRoute>().name else
         if(entry.destination.hasRoute<ProcessRoute>()) entry.toRoute<ProcessRoute>().appName else null
      atHome = entry.destination.hasRoute<HomeRoute>()
      navDepth = if(atHome) 0 else 1
      currentAppName = if(atHome) null else appName
      val label = entry.savedStateHandle.get<String>("qqqTitle") ?: processName?.let { instance?.processes?.get(it)?.label ?: it }
         ?: appName?.let { instance?.apps?.get(it)?.label ?: it }
      titleStack = if(atHome) emptyList() else listOf(label)
      navigationRevision++
      qViewModel.topBarStatusText = null
      qViewModel.activeProcessViewModel?.let { process ->
         if(process.processName != processName)
         {
            process.reset()
            qViewModel.activeProcessViewModel = null
         }
      }
   }

   private fun clearDestination()
   {
      atHome = true
      navDepth = 0
      titleStack = emptyList()
      currentAppName = null
      navigationRevision++
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun navigateToApp(name: String, label: String)
   {
      if(currentAppName == name && qViewModel.activeProcessViewModel == null) return
      requestLeave("Switch app?") {
         homeNow()
         navigateToRoute(AppRoute(name), label)
      }
   }

   /***************************************************************************
    ** As part of preserving the process view-model in case of activity restarts
    ** (e.g., when screen orientation changes) - manage the lifecycle of the
    ** active process view model in here - activating a new one when navigating.
    ***************************************************************************/
   fun navigateToProcess(name: String, label: String, processViewModel: ProcessViewModel)
   {
      if(qViewModel.activeProcessViewModel != null || leaveRequest != null) return
      processViewModel.reset()
      processViewModel.qqqRepository = qViewModel.qqqRepository
      processViewModel.qInstance = (qViewModel.qInstanceLoadState as LoadState.Success<QInstance>).value
      processViewModel.processName = name
      processViewModel.qViewModel = qViewModel
      processViewModel.supportedCustomWidgetTypes = qViewModel.supportedCustomWidgetTypes

      processViewModel.closeProcessCallback =
         {
            qViewModel.activeProcessViewModel = null
            navController?.popBackStack()
         }

      processViewModel.doInit()

      qViewModel.activeProcessViewModel = processViewModel

      navigateToRoute(ProcessRoute(name, currentAppName), label)
   }

   /***************************************************************************
    **
    ***************************************************************************/
   private fun navigateToRoute(route: Any, label: String)
   {
      navController?.let { controller ->
         controller.navigate(route)
         controller.currentBackStackEntry?.savedStateHandle?.set("qqqTitle", label)
         reconcile(controller)
      }
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun popStack()
   {
      if(atHome) return
      val title = titleStack.lastOrNull() ?: "process"
      requestLeave("Leave $title?") { navController?.popBackStack() }
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun goHome(cancelActiveProcess: Boolean = true)
   {
      if(!cancelActiveProcess)
      {
         leaveRequest = null
         qViewModel.activeProcessViewModel?.reset()
         qViewModel.activeProcessViewModel = null
         homeNow()
         return
      }
      requestLeave("Return to Apps?") { homeNow() }
   }

   private fun homeNow()
   {
      navController?.let { controller ->
         if(!controller.popBackStack<HomeRoute>(inclusive = false) && !atHome)
         {
            controller.navigate(HomeRoute) { popUpTo(controller.graph.id) { inclusive = true } }
         }
      }
   }

}
