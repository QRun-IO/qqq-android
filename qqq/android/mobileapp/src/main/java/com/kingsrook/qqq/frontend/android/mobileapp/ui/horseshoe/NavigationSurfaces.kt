/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.mobileapp.ui.horseshoe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.LoadState
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel

/** Host-owned device settings and diagnostics; these surfaces do not change the business route. */
val LocalDeviceSettings = compositionLocalOf<@Composable () -> Unit> { {} }
val LocalOpenDiagnostics = compositionLocalOf<(() -> Unit)?> { null }
val LocalBusinessNavigationEnabled = compositionLocalOf { true }

@Composable
internal fun AccountDialog(model: QViewModel, dismiss: () -> Unit)
{
   AlertDialog(onDismissRequest = dismiss, title = { Text("Account", Modifier.testTag("accountDialog.title")) },
      text = { Column {
         Text(if(model.isAuthenticated) "Current User:  ${model.sessionUserFullName}" else "Not currently logged in")
         TextButton(enabled = model.isAuthenticated && !model.isLogoutRequested, onClick = {
            dismiss()
            model.qNavigator.requestSignOut()
         }) { Text("Sign out") }
      } }, confirmButton = { TextButton(onClick = dismiss) { Text("Close") } })
}

@Composable
internal fun LeaveProcessDialog(model: QViewModel)
{
   val navigator = model.qNavigator
   val request = navigator.leaveRequest ?: return
   val process = model.activeProcessViewModel
   val blocked = process?.navigationBlockReason()
   val cancelling = process?.cancelling == true
   AlertDialog(onDismissRequest = navigator::dismissLeave, title = { Text(request.title) },
      text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
         if(process != null && !process.getNoMoreSteps())
         {
            Text("Leaving cancels this process. Unsaved input will be lost. Saved scans and other committed records stay saved.")
            if(process.unconfirmedSubmission)
               Text("The last request's result is unknown. Cancellation does not undo saved changes. Ask your administrator to check process ${process.processUUID}.")
         }
         else if(request.confirmLabel == "Sign out") Text("Sign out of this account on this device?")
         blocked?.let { Text(it) }
         if(cancelling) Text("Waiting for the server to confirm cancellation…")
         (process?.topLevelErrorState as? LoadState.Error)?.message?.let { Text(it) }
      } },
      confirmButton = { TextButton(onClick = navigator::confirmLeave, enabled = blocked == null && !cancelling) { Text(request.confirmLabel) } },
      dismissButton = { TextButton(onClick = navigator::dismissLeave, enabled = !cancelling) { Text("Stay") } })
}
