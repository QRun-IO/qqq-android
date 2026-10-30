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

package com.kingsrook.qqq.frontend.android.mobileapp.ui.processes.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kingsrook.qqq.frontend.android.core.model.metadata.QFieldType
import com.kingsrook.qqq.frontend.android.core.model.metadata.QFrontendComponent
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.ProcessViewModel
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.SemanticProcess
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.semanticFieldHint

@Composable
fun ProcessComponentEditForm(processViewModel: ProcessViewModel, component: QFrontendComponent)
{
   Column {
      (component.values["sectionLabel"] as? String)?.let { Text(it) }
      SemanticProcess.fields(processViewModel.activeStep, component).forEach { field ->
         var value by remember(processViewModel.stepInstanceCounter, field.name) {
            mutableStateOf(processViewModel.processValues[field.name] ?: field.defaultValue)
         }
         val enabled = field.isEditable && !processViewModel.waitingOnBackend && !processViewModel.cancelling && processViewModel.unsupportedReason() == null
         val error = processViewModel.fieldErrors[field.name]
         fun change(newValue: Any?) {
            value = newValue
            processViewModel.processValues[field.name] = newValue
         }
         LaunchedEffect(processViewModel.stepInstanceCounter, field.name) {
            if(!processViewModel.processValues.containsKey(field.name)) processViewModel.processValues[field.name] = value
            if(field.isEditable) processViewModel.registerFormFieldName(field.name)
         }
         Column(Modifier.fillMaxWidth().padding(8.dp)) {
            val options = field.inlinePossibleValueSource
            if(!options.isNullOrEmpty()) {
               var expanded by remember { mutableStateOf(false) }
               Text(field.label)
               OutlinedButton(onClick = { expanded = true }, enabled = enabled) {
                  Text(options.firstOrNull { it.id.toString() == value?.toString() }?.label ?: "Choose a value")
               }
               DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                  if(!field.isRequired && field.validation?.required != true) DropdownMenuItem(text = { Text("None") }, onClick = { change(null); expanded = false })
                  options.forEach { option -> DropdownMenuItem(text = { Text(option.label) }, onClick = { change(option.id); expanded = false }) }
               }
            } else if(field.type == QFieldType.BOOLEAN) {
               Row { Checkbox(checked = value == true || value == "true", onCheckedChange = { change(it) }, enabled = enabled); Text(field.label) }
            } else {
               TextField(value = value?.toString().orEmpty(), onValueChange = { change(it) },
                  label = { Text(field.label + if(field.isRequired || field.validation?.required == true) " *" else "") },
                  enabled = enabled, isError = error != null,
                  visualTransformation = if(field.type == QFieldType.PASSWORD) PasswordVisualTransformation() else VisualTransformation.None,
                  modifier = Modifier.fillMaxWidth())
            }
            if(error != null) Text(error, color = MaterialTheme.colorScheme.error)
            semanticFieldHint(field)?.let { Text(it) }
            field.helpContents.orEmpty().forEach { Text(it.contentAsText ?: plainText(it.content)) }
         }
      }
   }
}

@Composable
fun ProcessComponentViewForm(processViewModel: ProcessViewModel, component: QFrontendComponent)
{
   Column {
      SemanticProcess.fields(processViewModel.activeStep, component).forEach { field ->
         val value = processViewModel.processValues[field.name]
         val display = field.inlinePossibleValueSource?.firstOrNull { it.id.toString() == value?.toString() }?.label
            ?: if(field.type == QFieldType.PASSWORD && value != null) "••••••" else value?.toString().orEmpty()
         Text("${field.label}: ${if(field.type == QFieldType.HTML) plainText(display) else display}", Modifier.padding(8.dp))
      }
   }
}
