/*
 * SPDX-License-Identifier: Apache-2.0
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2004-2026.  Kingsrook, LLC
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

package com.kingsrook.qqq.frontend.android.mobileapp.ui.processes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.ProcessViewModel

/*******************************************************************************
 *******************************************************************************/
@Composable
fun ProcessBottomButtons(processViewModel: ProcessViewModel) {
   Row(
      horizontalArrangement = Arrangement.SpaceEvenly,
      modifier = Modifier.fillMaxWidth()
   )
   {
      val noMoreSteps = processViewModel.getNoMoreSteps()

      Button(onClick = processViewModel::requestClose, enabled = !processViewModel.cancelling, modifier = Modifier.requiredWidth(100.dp))
      {
         Text(if (noMoreSteps) "Close" else "Cancel")
      }

      if (!noMoreSteps) {
         Button(onClick = { processViewModel.runStep() }, enabled = !processViewModel.waitingOnBackend && !processViewModel.cancelling && processViewModel.clientWorkBlockReason == null && processViewModel.unsupportedReason() == null, modifier = Modifier.requiredWidth(100.dp))
         {
            Text(processViewModel.getNextButtonLabel())
         }
      }
   }
}
