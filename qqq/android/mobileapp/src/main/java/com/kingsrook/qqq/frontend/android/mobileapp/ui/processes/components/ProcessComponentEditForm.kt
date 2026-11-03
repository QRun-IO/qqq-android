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

package com.kingsrook.qqq.frontend.android.mobileapp.ui.processes.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kingsrook.qqq.frontend.android.core.model.metadata.QFieldType
import com.kingsrook.qqq.frontend.android.core.model.metadata.QFrontendComponent
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.ProcessViewModel
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.SemanticProcess
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.semanticFieldHint
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.semanticInitialValue

/*******************************************************************************
 ** Render server-selected editable fields and write changes into the current process values using semantic metadata.
 *******************************************************************************/
@Composable
fun ProcessComponentEditForm(processViewModel: ProcessViewModel, component: QFrontendComponent) {
   val focusManager = LocalFocusManager.current
   val keyboard = LocalSoftwareKeyboardController.current
   Column {
      (component.values["sectionLabel"] as? String)?.let { Text(it) }
      SemanticProcess.fields(processViewModel.activeStep, component).forEach { field ->
         var value by remember(processViewModel.stepInstanceCounter, field.name) {
            mutableStateOf(semanticInitialValue(field, processViewModel.processValues))
         }
         val enabled = field.isEditable && !processViewModel.waitingOnBackend && !processViewModel.cancelling && processViewModel.unsupportedReason() == null
         val error = processViewModel.fieldErrors[field.name]

         /*******************************************************************************
          ** Keep the displayed field value and submission map synchronized for the same semantic field.
          *******************************************************************************/
         fun change(newValue: Any?) {
            value = newValue
            processViewModel.processValues[field.name] = newValue
            // Dependent possible-value sources (for example media for a printer) re-query, disable or drop their selection.
            processViewModel.fieldChanged(field.name)
         }
         val remote = field.possibleValueSourceName != null && field.inlinePossibleValueSource.isNullOrEmpty()
         val blocked = processViewModel.remoteOptionsBlockedReason(field)
         val reset = processViewModel.fieldResets[field.name]
         LaunchedEffect(processViewModel.stepInstanceCounter, field.name) {
            if (!processViewModel.processValues.containsKey(field.name)) processViewModel.processValues[field.name] = value
            if (field.isEditable) processViewModel.registerFormFieldName(field.name)
            if (remote) processViewModel.loadRemoteOptions(field)
         }
         // The view model cleared this selection because its controlling field changed or the choice no longer exists.
         LaunchedEffect(reset) { if (reset != null) value = processViewModel.processValues[field.name] }
         Column(Modifier.fillMaxWidth().padding(8.dp)) {
            val options = if (remote) processViewModel.remoteOptions[field.name].orEmpty() else field.inlinePossibleValueSource.orEmpty()
            if (remote || options.isNotEmpty()) {
               var expanded by remember { mutableStateOf(false) }
               Text(field.label)
               OutlinedButton(onClick = { expanded = true }, enabled = enabled && blocked == null) {
                  Text(options.firstOrNull { it.id.toString() == value?.toString() }?.label ?: "Choose a value")
               }
               DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                  if (!field.isRequired && field.validation?.required != true) DropdownMenuItem(text = { Text("None") }, onClick = { change(null); expanded = false })
                  options.forEach { option -> DropdownMenuItem(text = { Text(option.label) }, onClick = { change(option.id); expanded = false }) }
               }
            } else if (field.type == QFieldType.BOOLEAN) {
               Row { Checkbox(checked = value == true || value == "true", onCheckedChange = { change(it) }, enabled = enabled); Text(field.label) }
            } else {
               val multiline = field.type in setOf(QFieldType.TEXT, QFieldType.HTML)
               TextField(
                  value = value?.toString().orEmpty(), onValueChange = { change(it) },
                  label = { Text(field.label + if (field.isRequired || field.validation?.required == true) " *" else "") },
                  enabled = enabled, isError = error != null,
                  singleLine = !multiline,
                  keyboardOptions = KeyboardOptions(
                     imeAction = if (multiline) ImeAction.Default else ImeAction.Done,
                     keyboardType = if (field.type == QFieldType.PASSWORD) KeyboardType.Password else KeyboardType.Text
                  ),
                  keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); keyboard?.hide() }),
                  visualTransformation = if (field.type == QFieldType.PASSWORD) PasswordVisualTransformation() else VisualTransformation.None,
                  modifier = Modifier.fillMaxWidth()
               )
            }
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            blocked?.let { Text(it) }
            processViewModel.remoteOptionErrors[field.name]?.let { message ->
               Text(message, color = MaterialTheme.colorScheme.error)
               OutlinedButton(onClick = { processViewModel.loadRemoteOptions(field) }) { Text("Retry choices") }
            }
            semanticFieldHint(field)?.let { Text(it) }
            field.helpContents.orEmpty().forEach { Text(it.contentAsText ?: plainText(it.content)) }
         }
      }
   }
}

/*******************************************************************************
 ** Render visible server-selected fields read-only, masking password values and stripping display HTML.
 *******************************************************************************/
@Composable
fun ProcessComponentViewForm(processViewModel: ProcessViewModel, component: QFrontendComponent) {
   Column {
      SemanticProcess.fields(processViewModel.activeStep, component).forEach { field ->
         val value = processViewModel.processValues[field.name]
         val display = field.inlinePossibleValueSource?.firstOrNull { it.id.toString() == value?.toString() }?.label
            ?: if (field.type == QFieldType.PASSWORD && value != null) "••••••" else value?.toString().orEmpty()
         Text("${field.label}: ${if (field.type == QFieldType.HTML) plainText(display) else display}", Modifier.padding(8.dp))
      }
   }
}
