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

package com.kingsrook.qqq.frontend.android.mobileapp.ui.processes

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kingsrook.qqq.frontend.android.mobileapp.R
import com.kingsrook.qqq.frontend.android.mobileapp.ui.common.FullSizedAllCenteredColumn
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.ProcessViewModel
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter


/***************************************************************************
 ** Loading screen within a process - e.g., when server-side async processing
 ** is running long
 ***************************************************************************/
@Composable
fun ProcessStepLoading(processViewModel: ProcessViewModel)
{
   val context = LocalContext.current
   val playCount = remember { mutableIntStateOf(0) }
   var mediaPlayer: MediaPlayer? = null

   ////////////////////////////////////////////////////////////////////////////////////////////
   // monitor the jobRunningLastUpdate timestamp - it gets updated after a checkIn completes //
   // re-run checkins until the state becomes success or error                               //
   ////////////////////////////////////////////////////////////////////////////////////////////
   LaunchedEffect(processViewModel.jobRunningLastUpdated)
   {
      delay(1000)
      if(processViewModel.waitingOnBackend)
      {
         processViewModel.checkIn()
      }
   }

   /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
   // use a separate effect, that will fire every second (regardless of if backend is responding), to play 'tick' //
   /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
   val format = FrontendStepFormat.of(processViewModel.activeStep?.format)
   if(format.playOnLoadingSound())
   {
      LaunchedEffect(playCount.intValue)
      {
         if(processViewModel.waitingOnBackend)
         {
            if(mediaPlayer == null)
            {
               mediaPlayer = createMediaPlayer(context, R.raw.tick1b)
            }

            mediaPlayer?.start()
            delay(1000)
            playCount.intValue += 1
         }
      }
   }

   //////////////////////////////////////////////////////////////
   // try to remove the media player when the screen goes away //
   //////////////////////////////////////////////////////////////
   val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
   DisposableEffect(lifecycleOwner, disposeMediaPlayer(mediaPlayer, lifecycleOwner))

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

         if(processViewModel.jobRunningMessage != "")
         {
            Text(processViewModel.jobRunningMessage, modifier = commonModifier)
         }
         else
         {
            Text("Working...", modifier = commonModifier)
         }

         if(processViewModel.currentOfTotalMessage != "")
         {
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
