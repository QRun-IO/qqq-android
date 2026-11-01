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

package com.kingsrook.qqq.frontend.android.mobileapp.ui.processes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kingsrook.qqq.frontend.android.mobileapp.ui.common.FullSizedAllCenteredColumn
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.ProcessViewModel
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter


/*******************************************************************************
 ** Loading screen within a process - e.g., when server-side async processing
 ** is running long
 *******************************************************************************/
@Composable
fun ProcessStepLoading(processViewModel: ProcessViewModel) {
   val playCount = remember { mutableIntStateOf(0) }
   val playTick = rememberProcessSound(ProcessTone.LOADING)

   ////////////////////////////////////////////////////////////////////////////////////////////
   // monitor the jobRunningLastUpdate timestamp - it gets updated after a checkIn completes //
   // re-run checkins until the state becomes success or error                               //
   ////////////////////////////////////////////////////////////////////////////////////////////
   LaunchedEffect(processViewModel.jobRunningLastUpdated)
   {
      delay(1000)
      if (processViewModel.waitingOnBackend) {
         processViewModel.checkIn()
      }
   }

   /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
   // use a separate effect, that will fire every second (regardless of if backend is responding), to play 'tick' //
   /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
   val format = FrontendStepFormat.of(processViewModel.activeStep?.format)
   if (format.playOnLoadingSound()) {
      LaunchedEffect(playCount.intValue)
      {
         if (processViewModel.waitingOnBackend) {
            playTick()
            delay(1000)
            playCount.intValue += 1
         }
      }
   }

   //////////////////////////////////////////////////////////////
   // try to remove the media player when the screen goes away //
   //////////////////////////////////////////////////////////////

   val indicatorSize = 64.dp

   FullSizedAllCenteredColumn(Modifier)
   {
      /////////////////
      // status text //
      /////////////////
      Column(
         modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .fillMaxWidth(.75f)
      )
      {
         val commonModifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(bottom = 4.dp)

         if (processViewModel.jobRunningMessage != "") {
            Text(processViewModel.jobRunningMessage, modifier = commonModifier)
         } else {
            Text("Working...", modifier = commonModifier)
         }

         if (processViewModel.currentOfTotalMessage != "") {
            Text(processViewModel.currentOfTotalMessage, modifier = commonModifier)
         }

         Text("Updated at: ${processViewModel.jobRunningLastUpdated.format(DateTimeFormatter.ofPattern("h:mm:ss"))}", modifier = commonModifier)
      }

      CircularProgressIndicator(
         modifier = Modifier
            .width(indicatorSize)
            .height(indicatorSize),
         color = MaterialTheme.colorScheme.secondary,
         trackColor = MaterialTheme.colorScheme.surfaceVariant,
      )
      androidx.compose.material3.Button(onClick = processViewModel::cancel, enabled = !processViewModel.cancelling) { Text("Cancel") }

   }
}
