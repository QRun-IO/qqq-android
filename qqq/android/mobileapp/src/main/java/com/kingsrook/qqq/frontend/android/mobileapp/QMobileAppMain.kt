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
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.kingsrook.qqq.frontend.android.mobileapp.ui.horseshoe.LeaveProcessDialog
import com.kingsrook.qqq.frontend.android.mobileapp.ui.horseshoe.LocalBusinessNavigationEnabled
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.LoadState
import com.kingsrook.qqq.frontend.android.core.model.metadata.QInstance
import com.kingsrook.qqq.frontend.android.core.model.metadata.QAppNodeType
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

   val scope = rememberCoroutineScope()
   val topLevelAppState = remember(qViewModel, scope) { TopLevelAppState(coroutineScope = scope, qViewModel = qViewModel) }
   LaunchedEffect(qViewModel.isAuthenticated, qViewModel.isLogoutRequested)
   {
      if(!qViewModel.isAuthenticated || qViewModel.isLogoutRequested) topLevelAppState.closeNavDrawer()
   }

   AnimatedVisibility(
      splashState.value == SplashState.DONE,
      enter = fadeIn(animationSpec = tween(splashFadeOutMillis.intValue)),
      exit = ExitTransition.None
   )
   {
      ModalNavigationDrawer(drawerState = topLevelAppState.navDrawerState, gesturesEnabled = LocalBusinessNavigationEnabled.current && qViewModel.isAuthenticated && !qViewModel.isLogoutRequested,
         drawerContent = {
            ModalDrawerSheet {
               Column(Modifier.verticalScroll(rememberScrollState())) {
                  Text("Switch app", Modifier.padding(16.dp))
                  NavigationDrawerItem(label = { Text("Apps") }, selected = qViewModel.qNavigator.atHome, onClick = {
                     topLevelAppState.closeNavDrawer(); qViewModel.qNavigator.goHome()
                  })
                  val instance = (qViewModel.qInstanceLoadState as? LoadState.Success<QInstance>)?.value
                  instance?.appTree?.filter { it.type == QAppNodeType.APP }?.forEach { app ->
                     NavigationDrawerItem(label = { Text(app.label) }, selected = qViewModel.qNavigator.currentAppName == app.name, onClick = {
                        topLevelAppState.closeNavDrawer(); qViewModel.qNavigator.navigateToApp(app.name, app.label)
                     })
                  }
               }
            }
         }) {
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
      LeaveProcessDialog(qViewModel)
   }

}


