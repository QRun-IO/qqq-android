/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kingsrook.qqq.frontend.android.core.model.metadata.QFieldType
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.LoadState
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel

internal fun sessionScreen(model: QViewModel, foreground: Boolean, focused: Boolean, approved: Boolean, laidOut: Boolean): SessionScreen
{
   val process = model.activeProcessViewModel
   val step = process?.activeStep
   val hasPassword = (step?.formFields.orEmpty() + step?.viewFields.orEmpty()).any { !it.isHidden && it.type == QFieldType.PASSWORD } ||
      containsPasswordWidget(step?.components?.map { it.values }) || containsPasswordWidget(process?.processValues)
   return SessionScreen(foreground, model.isAuthenticated && !model.isLogoutRequested && model.sessionUUID.isNotBlank(),
      focused, approved, hasPassword, laidOut && model.qInstanceLoadState is LoadState.Success &&
         (process == null || step != null || process.getNoMoreSteps()), false,
      model.qNavigator.navigationRevision, process?.stepInstanceCounter ?: 0,
      model.qNavigator.currentAppName, process?.processName, process?.processUUID, process?.activeStepName?.ifEmpty { null },
      (step?.label ?: model.qNavigator.titleStack.lastOrNull() ?: "Apps").take(200))
}

/** Widget field metadata is nested in composite blocks; inspect types without transmitting any values. */
private fun containsPasswordWidget(value: Any?): Boolean = when(value)
{
   is Map<*, *> -> (value["fieldMetaData"] as? Map<*, *>)?.get("type") == "PASSWORD" || value.values.any(::containsPasswordWidget)
   is List<*> -> value.any(::containsPasswordWidget)
   else -> false
}

@Composable
internal fun ScreenViewingIndicator(controller: MobileSessionController)
{
   if(controller.viewing || controller.operatorPaused) Surface(color = MaterialTheme.colorScheme.secondaryContainer)
   {
      Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically)
      {
         Text(if(controller.operatorPaused) "Screen viewing paused" else "Screen being viewed by an administrator", Modifier.weight(1f))
         TextButton(onClick = { if(controller.operatorPaused) controller.allowViewing() else controller.pauseViewing() })
         { Text(if(controller.operatorPaused) "Allow viewing" else "Stop viewing") }
      }
   }
}
