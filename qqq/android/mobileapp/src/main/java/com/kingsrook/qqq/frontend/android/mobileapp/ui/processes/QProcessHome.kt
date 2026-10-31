/*
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

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kingsrook.qqq.frontend.android.core.model.metadata.QComponentType
import com.kingsrook.qqq.frontend.android.core.model.metadata.QInstance
import com.kingsrook.qqq.frontend.android.core.model.processes.QJobComplete
import com.kingsrook.qqq.frontend.android.core.model.processes.QJobError
import com.kingsrook.qqq.frontend.android.core.model.processes.QJobRunning
import com.kingsrook.qqq.frontend.android.core.model.processes.QJobStarted
import com.kingsrook.qqq.frontend.android.mobileapp.ui.processes.components.ProcessComponentEditForm
import com.kingsrook.qqq.frontend.android.mobileapp.ui.processes.components.ProcessComponentViewForm
import com.kingsrook.qqq.frontend.android.mobileapp.ui.processes.components.ProcessComponentHTML
import com.kingsrook.qqq.frontend.android.mobileapp.ui.processes.components.ProcessComponentHelpText
import com.kingsrook.qqq.frontend.android.mobileapp.ui.processes.components.ProcessComponentProcessSummaryResults
import com.kingsrook.qqq.frontend.android.mobileapp.ui.processes.components.ProcessComponentWidget
import com.kingsrook.qqq.frontend.android.mobileapp.ui.processes.components.UnsupportedProcessComponent
import com.kingsrook.qqq.frontend.android.mobileapp.ui.utils.Colors
import com.kingsrook.qqq.frontend.android.mobileapp.ui.utils.LoadStateView
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.LoadState
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.ProcessViewModel
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel
import kotlinx.coroutines.delay

/***************************************************************************
 **
 ***************************************************************************/
@Composable
fun QProcessHome(qViewModel: QViewModel, qInstance: QInstance, modifier: Modifier = Modifier)
{
   val processViewModel = qViewModel.activeProcessViewModel
   if(processViewModel == null)
   {
      Text("Error: ProcessViewModel was not available.", color = Colors.ERROR)
      return
   }

   val processMetaDataLoadState = processViewModel.processMetaDataLoadState
   BackHandler { qViewModel.qNavigator.popStack() }
   val initLoadState = processViewModel.initLoadState
   val topLevelErrorState = processViewModel.topLevelErrorState
   val loadStates = listOf(processMetaDataLoadState, initLoadState, topLevelErrorState)

   val playSubmitSound = rememberProcessSound(ProcessTone.SUBMIT)
   processViewModel.onSubmitCallback =
      {
         val format = FrontendStepFormat.of(processViewModel.activeStep?.format)
         if(format.playOnSubmitSound())
         {
             playSubmitSound()
         }
      }

   //////////////////////////////////////////////////////////////
   // try to remove the media player when the screen goes away //
   //////////////////////////////////////////////////////////////

   ///////////////////////////////////////////////////////////////////////////////////////////
   // in case the init job has gone async - this LaunchedEffect will make us check in on it //
   ///////////////////////////////////////////////////////////////////////////////////////////
   LaunchedEffect(key1 = processViewModel.jobRunningLastUpdated)
   {
      if(processViewModel.initLoadState is LoadState.Loading && processViewModel.processUUID != null && processViewModel.waitingOnBackend && !processViewModel.cancelling)
      {
         delay(1000)
         processViewModel.checkIn()
      }
   }

   Column(modifier.fillMaxSize())
   {
      LoadStateView(
         loadStates,
          modifier = Modifier.weight(1f),
          reloader = processViewModel.retryLabel()?.let { label ->
             com.kingsrook.qqq.frontend.android.mobileapp.ui.utils.Reloader(label, { processViewModel.retryPendingStep() }) },
         loadingText = "Loading Process"
      )
      {
         val lastStepResult = processViewModel.lastStepResult

         Column()
         {
            ///////////////////////////////////////////////////////////////////////////////////////////////////////////
            // use the step-instance-counter as key here - to help drive re-composition when advancing through steps //
            ///////////////////////////////////////////////////////////////////////////////////////////////////////////
            key(processViewModel.stepInstanceCounter)
            {
               when (lastStepResult)
               {
                  is QJobRunning,
                  is QJobStarted ->
                  {
                     ProcessStepLoading(processViewModel)
                  }

                  is QJobComplete ->
                  {
                     ProcessFrontendStepView(processViewModel, qInstance)
                  }

                  is QJobError ->
                  {
                     Text("Error: ${lastStepResult.userFacingError ?: lastStepResult.error}")
                  }
               }
            }
         }

      }
      if(LoadState.areAnyErrors(loadStates) || LoadState.areAnyLoading(loadStates))
      {
         Button(onClick = processViewModel::requestClose, enabled = !processViewModel.cancelling) { Text("Cancel") }
      }
   }
}

/***************************************************************************
 ** composable that outputs a frontend process step - its components basically
 ***************************************************************************/
@Composable
fun ProcessFrontendStepView(processViewModel: ProcessViewModel, qInstance: QInstance, modifier: Modifier = Modifier)
{
   val format = FrontendStepFormat.of(processViewModel.activeStep?.format)

   Column(modifier.fillMaxSize().imePadding())
   {
      Column(
         modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
            .padding(if(format.doesScreenGetPadding()) 8.dp else 0.dp)
            .zIndex(1f)
      )
      {
         if(format.isStepLabelDisplayed())
         {
            Text(processViewModel.activeStep?.label ?: if(processViewModel.getNoMoreSteps()) "Complete" else "", fontWeight = FontWeight.Bold, fontSize = 20.sp)
         }

         processViewModel.activeStep?.components?.forEach()
         { component ->
            when (component.type)
            {
               QComponentType.EDIT_FORM -> ProcessComponentEditForm(processViewModel, component)
               QComponentType.VIEW_FORM -> ProcessComponentViewForm(processViewModel, component)
               QComponentType.HELP_TEXT -> ProcessComponentHelpText(processViewModel, component)
               QComponentType.HTML -> ProcessComponentHTML(processViewModel, component)
               QComponentType.PROCESS_SUMMARY_RESULTS -> ProcessComponentProcessSummaryResults(processViewModel, component)
               QComponentType.WIDGET -> ProcessComponentWidget(processViewModel, qInstance, component, disableControls = processViewModel.waitingOnBackend || processViewModel.cancelling || processViewModel.unsupportedReason() != null || processViewModel.getNoMoreSteps())
               else -> UnsupportedProcessComponent(processViewModel, component)
            }
         }

         processViewModel.unsupportedReason()?.takeUnless { processViewModel.getNoMoreSteps() }?.let { Text(it, color = Colors.ERROR) }
         processViewModel.submissionError?.let { Text(it, color = Colors.ERROR) }
      }
      if(format.areStandardFooterButtonsDisplayed() || processViewModel.unsupportedReason() != null || processViewModel.getNoMoreSteps())
      {
         ProcessBottomButtons(processViewModel)
      }
   }
}

/***************************************************************************
 **
 ***************************************************************************/
fun createMediaPlayer(context: Context, resourceId: Int): MediaPlayer?
{
   val player: MediaPlayer? = MediaPlayer.create(context, resourceId)
   player?.setVolume(1f, 1f)
   return player
}

/***************************************************************************
 **
 ***************************************************************************/
@Composable
internal fun rememberProcessSound(tone: ProcessTone): () -> Unit
{
   val lifecycleOwner = LocalLifecycleOwner.current
   val sound = remember(tone)
   {
      ProcessSound {
         val generator = ToneGenerator(AudioManager.STREAM_MUSIC, 50)
         object : ProcessSound.Playback
         {
            override fun start() { generator.startTone(tone.platformType, tone.durationMs) }
            override fun release() = generator.release()
         }
      }
   }
   DisposableEffect(lifecycleOwner, sound)
   {
      val observer = LifecycleEventObserver()
      { _, event ->
         if(event == Lifecycle.Event.ON_STOP)
         {
            sound.release()
         }
      }

      lifecycleOwner.lifecycle.addObserver(observer)

      onDispose()
      {
         lifecycleOwner.lifecycle.removeObserver(observer)
         sound.release()
      }
   }
   return {
      if(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
      {
         sound.play()
      }
   }
}

/** Platform-generated feedback avoids redistributing recordings of unknown origin. */
internal enum class ProcessTone(val platformType: Int, val durationMs: Int)
{
   SUBMIT(ToneGenerator.TONE_PROP_ACK, 80),
   LOADING(ToneGenerator.TONE_PROP_BEEP, 25)
}

