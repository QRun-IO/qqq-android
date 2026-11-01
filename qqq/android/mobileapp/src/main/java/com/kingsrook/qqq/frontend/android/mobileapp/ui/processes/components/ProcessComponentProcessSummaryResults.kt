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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.kingsrook.qqq.frontend.android.core.model.metadata.QFrontendComponent
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.ProcessViewModel

/*******************************************************************************
 ** Render the server's process summary results rather than inferring completion details from client state.
 *******************************************************************************/
@Composable
fun ProcessComponentProcessSummaryResults(processViewModel: ProcessViewModel, @Suppress("UNUSED_PARAMETER") component: QFrontendComponent) // Keep the uniform renderer API; result rows come from authoritative process values.
{
   Column {
      (processViewModel.processValues["recordCount"] as? Number)?.let { Text("$it records processed") }
      (processViewModel.processValues["processResults"] as? List<*>)?.forEach { result ->
         if (result is Map<*, *>) {
            val status = result["status"]?.toString().orEmpty()
            val count = (result["count"] as? Number)?.let { "$it " }.orEmpty()
            val message = result["message"]?.toString().orEmpty()
            Text("$status: $count$message")
            // Keep server-provided destinations visible without assuming web routes are native screens.
            result["link"]?.toString()?.let { Text(it) }
         }
      }
   }
}
