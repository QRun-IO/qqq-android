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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.kingsrook.qqq.frontend.android.core.model.metadata.QFrontendComponent
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.ProcessViewModel

@Composable
fun ProcessComponentProcessSummaryResults(processViewModel: ProcessViewModel, component: QFrontendComponent)
{
   Column {
      (processViewModel.processValues["recordCount"] as? Number)?.let { Text("$it records processed") }
      (processViewModel.processValues["processResults"] as? List<*>)?.forEach { result ->
         if(result is Map<*, *>) {
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
