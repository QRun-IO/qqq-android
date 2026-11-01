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

package com.kingsrook.qqq.frontend.android.mobileapp.ui.widgets.blocks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.kingsrook.qqq.frontend.android.core.model.metadata.QFieldType
import com.kingsrook.qqq.frontend.android.mobileapp.ui.utils.Colors
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.SemanticProcess
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.semanticInitialValue
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.semanticFieldHint
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.validateSemanticField

/*******************************************************************************
 ** A typed native input: autofocus changes focus only and never reinterprets field data as an action.
 *******************************************************************************/
@Composable
fun InputFieldWidgetBlock(params: WidgetBlockParameters, modifier: Modifier = Modifier, disableControls: Boolean = false) {
   val field = SemanticProcess.inputField(params.widgetBlock.values)
   if (field == null || (!field.isHidden && !SemanticProcess.supportedWidgetInput(field))) {
      Text("This field requires an input control unavailable in this client.", color = Colors.ERROR)
      return
   }
   if (field.isHidden) return
   var text by remember(field.name) { mutableStateOf(semanticInitialValue(field, params.values)?.toString().orEmpty()) }
   var error by remember(field.name) { mutableStateOf<String?>(null) }
   val enabled = field.isEditable && !disableControls
   val submitOnEnter = params.widgetBlock.values["submitOnEnter"] == true
   val focusRequester = remember { FocusRequester() }
   LaunchedEffect(field.name)
   {
      if (field.isEditable) {
         if (!params.values.containsKey(field.name)) params.values[field.name] = semanticInitialValue(field, params.values)
         params.qViewModel?.activeProcessViewModel?.registerFormFieldName(field.name)
         if (params.widgetBlock.values["autoFocus"] == true) focusRequester.requestFocus()
      }
   }

   /*******************************************************************************
    ** The process submission boundary also validates/converts this declared field.
    *******************************************************************************/
   fun submit(value: String) {
      if (!enabled) return
      error = validateSemanticField(field, value)
      if (error == null) params.actionCallback?.invoke(mapOf(field.name to value))
   }

   Column(modifier)
   {
      TextField(
         value = text,
         enabled = enabled,
         isError = error != null,
         onValueChange = { value ->
            val submitted = submitOnEnter && value.endsWith('\n')
            text = if (submitted) value.dropLast(1) else value
            params.values[field.name] = text
            if (submitted) submit(text)
         },
         label = { Text(field.label) },
         singleLine = true,
         visualTransformation = if (field.type == QFieldType.PASSWORD) PasswordVisualTransformation() else VisualTransformation.None,
         keyboardOptions = KeyboardOptions(imeAction = if (submitOnEnter) ImeAction.Done else ImeAction.Default),
         keyboardActions = KeyboardActions(onDone = { if (submitOnEnter) submit(text) }),
         modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).onPreviewKeyEvent { event ->
            if (submitOnEnter && event.key == Key.Enter) {
               if (event.type == KeyEventType.KeyUp) submit(text)
               true
            } else false
         }
      )
      error?.let { Text(it, color = Colors.ERROR) }
      semanticFieldHint(field)?.let { Text(it) }
   }
}
