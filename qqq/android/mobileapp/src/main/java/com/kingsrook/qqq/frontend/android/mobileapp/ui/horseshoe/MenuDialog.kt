/*
 * SPDX-License-Identifier: Apache-2.0
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

package com.kingsrook.qqq.frontend.android.mobileapp.ui.horseshoe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kingsrook.qqq.frontend.android.mobileapp.container.QAppContainer
import com.kingsrook.qqq.frontend.android.mobileapp.ui.utils.Dropdown
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel

/*******************************************************************************
 ** Secondary settings preserve the active process until a server change is confirmed.
 *******************************************************************************/
@Composable
fun MenuDialog(qViewModel: QViewModel, appTitle: String, isMenuDialogOpen: MutableState<Boolean>) {
   val selected = remember { mutableStateOf(qViewModel.environment) }
   AlertDialog(
      onDismissRequest = { isMenuDialogOpen.value = false },
      title = { Text("Settings", Modifier.testTag("menuDialog.title")) },
      text = {
         Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(appTitle)
            Text("Server: ${qViewModel.environment.baseUrl}")
            LocalDeviceSettings.current()
            if (QAppContainer.availableEnvironments.size > 1) {
               Dropdown(
                  value = selected.value, options = QAppContainer.availableEnvironments,
                  labelExtractor = { it.label }, onClick = { selected.value = it })
               TextButton(enabled = selected.value != qViewModel.environment, onClick = {
                  isMenuDialogOpen.value = false
                  qViewModel.qNavigator.requestLeave("Change server and sign out?", "Change server", alwaysConfirm = true) {
                     qViewModel.switchEnvironment(selected.value) {}
                  }
               }) { Text("Switch") }
            }
            Text("Application version: ${QViewModel.applicationVersion}")
         }
      }, confirmButton = { TextButton(onClick = { isMenuDialogOpen.value = false }) { Text("Close") } })
}
