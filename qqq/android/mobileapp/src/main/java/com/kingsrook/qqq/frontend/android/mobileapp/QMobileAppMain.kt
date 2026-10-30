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

package com.kingsrook.qqq.frontend.android.mobileapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication.QAuthenticationWrapper
import com.kingsrook.qqq.frontend.android.mobileapp.ui.horseshoe.QTopAppBar
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.TopLevelAppState
import kotlinx.coroutines.delay

enum class SplashState
{
   INITIAL, ACTIVE, DONE
}

/***************************************************************************
 **
 ***************************************************************************/
@Composable
fun QMobileAppMain(qViewModel: QViewModel, title: String, splashImage: Painter? = null, showNetworkDialog: Boolean = true)
{
   val splashState = remember { mutableStateOf(if(splashImage == null || qViewModel.didRunSplashAnimation) SplashState.DONE else SplashState.INITIAL) }
   val splashFadeOutMillis = remember { mutableIntStateOf(1500) }
   val splashDurationMillis = 3000L
   val splashSlideInMillis = 2500

   LaunchedEffect(splashState.value)
   {
      when (splashState.value)
      {
         SplashState.INITIAL ->
         {
            if(!qViewModel.didRunSplashAnimation)
            {
               qViewModel.didRunSplashAnimation = true;
               splashState.value = SplashState.ACTIVE
            }
         }

         SplashState.ACTIVE ->
         {
            delay(splashDurationMillis)
            splashState.value = SplashState.DONE
         }

         SplashState.DONE ->
         {
            //////////
            // noop //
            //////////
         }
      }
   }

   if(splashImage != null)
   {
      AnimatedVisibility(
         splashState.value == SplashState.ACTIVE,
         enter = slideInVertically(animationSpec = tween(splashSlideInMillis, easing = FastOutSlowInEasing), initialOffsetY = { it }),
         exit = fadeOut(animationSpec = tween(splashFadeOutMillis.intValue))
      )
      {
         Image(
            painter = splashImage,
            contentDescription = null, // decorative element
            modifier = Modifier
               .fillMaxSize()
               .padding(20.dp)
               .indication(indication = null, interactionSource = remember { MutableInteractionSource() }) // try to make no ripple - but not working...
               .clickable()
               {
                  splashState.value = SplashState.DONE
                  splashFadeOutMillis.intValue = 250
               }
         )
      }
   }

   val topLevelAppState = TopLevelAppState(coroutineScope = rememberCoroutineScope(), qViewModel = qViewModel)

   AnimatedVisibility(
      splashState.value == SplashState.DONE,
      enter = fadeIn(animationSpec = tween(splashFadeOutMillis.intValue)),
      exit = ExitTransition.None
   )
   {
      Scaffold(
         Modifier.fillMaxSize(),
         topBar = {
            QTopAppBar(
               qViewModel,
               navMenuCallback = topLevelAppState::openNavDrawer,
               defaultTitle = title,
               qNavigator = qViewModel.qNavigator,
               showNetworkDialog = showNetworkDialog
            )
         }
      ) { innerPadding ->
         Column(
            Modifier
               .padding(innerPadding)
               .fillMaxSize()
         )
         {
            QAuthenticationWrapper(
               qViewModel,
               Modifier
                  .fillMaxSize(),
               qNavigator = qViewModel.qNavigator,
            )
         }
      }
   }

}


