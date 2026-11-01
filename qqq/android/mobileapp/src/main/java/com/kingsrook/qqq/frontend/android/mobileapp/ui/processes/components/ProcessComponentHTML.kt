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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import com.kingsrook.qqq.frontend.android.core.model.metadata.QFrontendComponent
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.ProcessViewModel

/*******************************************************************************
 *******************************************************************************/
@Composable
fun ProcessComponentHTML(processViewModel: ProcessViewModel, @Suppress("UNUSED_PARAMETER") component: QFrontendComponent) // Keep the uniform renderer API; this renderer reads HTML from the active process values.
{
   val semanticText = processViewModel.processValues["${processViewModel.activeStep?.name}.text"] as? String
   val processValue = processViewModel.processValues["${processViewModel.activeStep?.name}.html"]
   val html: String = if (processValue is String) {
      processValue
   } else if (processValue == null) {
      ""
   } else {
      processValue.toString()
   }

   Text(
      semanticText ?: plainText(html),
      modifier = Modifier
         .fillMaxWidth()
         .padding(vertical = 12.dp)
   )
}
