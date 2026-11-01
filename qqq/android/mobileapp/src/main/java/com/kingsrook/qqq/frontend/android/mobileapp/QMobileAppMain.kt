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

package com.kingsrook.qqq.frontend.android.mobileapp

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication.QAuthenticationWrapper
import com.kingsrook.qqq.frontend.android.mobileapp.ui.horseshoe.QTopAppBar
import com.kingsrook.qqq.frontend.android.mobileapp.ui.horseshoe.LeaveProcessDialog
import com.kingsrook.qqq.frontend.android.mobileapp.ui.horseshoe.LocalBusinessNavigationEnabled
import com.kingsrook.qqq.frontend.android.mobileapp.ui.horseshoe.LocalOpenDiagnostics
import com.kingsrook.qqq.frontend.android.mobileapp.ui.LocalProcessInputAllowed
import com.kingsrook.qqq.frontend.android.mobileapp.ui.LocalNavigationBackAction
import androidx.compose.runtime.CompositionLocalProvider
import com.kingsrook.qqq.frontend.android.mobileapp.ui.horseshoe.MenuDialog
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.LoadState
import com.kingsrook.qqq.frontend.android.core.model.metadata.QInstance
import com.kingsrook.qqq.frontend.android.core.model.metadata.QAppNodeType
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.TopLevelAppState
import kotlinx.coroutines.delay

/*******************************************************************************
 ** Keep the optional splash transition separate from authentication and business-navigation state.
 *******************************************************************************/
enum class SplashState {
   INITIAL, ACTIVE, DONE
}

/*******************************************************************************
 *******************************************************************************/
@Composable
fun QMobileAppMain(qViewModel: QViewModel, title: String, splashImage: Painter? = null, showNetworkDialog: Boolean = true) {
   val splashState = remember { mutableStateOf(if (splashImage == null || qViewModel.didRunSplashAnimation) SplashState.DONE else SplashState.INITIAL) }
   val splashFadeOutMillis = remember { mutableIntStateOf(1500) }
   val splashDurationMillis = 3000L
   val splashSlideInMillis = 2500

   LaunchedEffect(splashState.value)
   {
      when (splashState.value) {
         SplashState.INITIAL -> {
            if (!qViewModel.didRunSplashAnimation) {
               qViewModel.didRunSplashAnimation = true;
               splashState.value = SplashState.ACTIVE
            }
         }

         SplashState.ACTIVE -> {
            delay(splashDurationMillis)
            splashState.value = SplashState.DONE
         }

         SplashState.DONE -> {
            //////////
            // noop //
            //////////
         }
      }
   }

   if (splashImage != null) {
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
   val settingsOpen = remember { mutableStateOf(false) }
   val diagnosticsOpen = remember { mutableStateOf(false) }
   val accountOpen = remember { mutableStateOf(false) }
   val drawerWidth = remember { mutableIntStateOf(0) }
   val parentInputAllowed = LocalProcessInputAllowed.current
   val openDiagnostics = LocalOpenDiagnostics.current
   val businessReady = LocalBusinessNavigationEnabled.current && qViewModel.isAuthenticated && !qViewModel.isLogoutRequested

   /*******************************************************************************
    ** Block process input during partial drawer gestures and animations, not only when the drawer is fully open.
    *******************************************************************************/
   fun drawerActive(): Boolean {
      val drawer = topLevelAppState.navDrawerState
      return topLevelAppState.drawerRequested || drawer.isOpen || drawer.targetValue == DrawerValue.Open ||
         drawer.isAnimationRunning ||
         // Material's closed anchor is minus the measured width, including partial swipes.
         (drawerWidth.intValue > 0 && drawer.currentOffset > -drawerWidth.intValue.toFloat())
   }

   val navigateBack: () -> Unit = {
      if (drawerActive()) topLevelAppState.closeNavDrawer() else qViewModel.qNavigator.popStack()
   }
   LaunchedEffect(qViewModel.isAuthenticated, qViewModel.isLogoutRequested, businessReady)
   {
      if (!businessReady) topLevelAppState.closeNavDrawer()
      if (qViewModel.isLogoutRequested) {
         settingsOpen.value = false
         diagnosticsOpen.value = false
      }
   }

   AnimatedVisibility(
      splashState.value == SplashState.DONE,
      enter = fadeIn(animationSpec = tween(splashFadeOutMillis.intValue)),
      exit = ExitTransition.None
   )
   {
      ModalNavigationDrawer(
         drawerState = topLevelAppState.navDrawerState,
         drawerContent = {
            ModalDrawerSheet(Modifier.onSizeChanged { drawerWidth.intValue = it.width }) {
               Column(Modifier.fillMaxSize()) {
                  Text(title, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                  Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                     if (businessReady) {
                        NavigationDrawerItem(
                           modifier = Modifier.testTag("drawer.home"), label = { Text("All apps") },
                           selected = qViewModel.qNavigator.atHome, onClick = {
                              topLevelAppState.closeNavDrawer(); qViewModel.qNavigator.goHome()
                           })
                        val instance = (qViewModel.qInstanceLoadState as? LoadState.Success<QInstance>)?.value
                        instance?.appTree?.filter { it.type == QAppNodeType.APP }?.forEach { app ->
                           NavigationDrawerItem(
                              modifier = Modifier.testTag("drawer.app:${app.name}"), label = { Text(app.label) },
                              selected = qViewModel.qNavigator.currentAppName == app.name, onClick = {
                                 topLevelAppState.closeNavDrawer(); qViewModel.qNavigator.navigateToApp(app.name, app.label)
                              })
                        }
                     }
                  }
                  HorizontalDivider()
                  Column(Modifier.padding(vertical = 8.dp)) {
                     NavigationDrawerItem(modifier = Modifier.testTag("drawer.settings"), label = { Text("Settings") }, selected = false, onClick = {
                        topLevelAppState.closeNavDrawer(); settingsOpen.value = true
                     })
                     NavigationDrawerItem(modifier = Modifier.testTag("drawer.diagnostics"), label = { Text("Diagnostics") }, selected = false, onClick = {
                        topLevelAppState.closeNavDrawer()
                        if (openDiagnostics != null) openDiagnostics() else diagnosticsOpen.value = true
                     })
                  }
               }
            }
         }) {
         CompositionLocalProvider(LocalNavigationBackAction provides navigateBack) {
            Scaffold(
               Modifier.fillMaxSize(),
               topBar = {
                  QTopAppBar(
                     qViewModel,
                     navMenuCallback = topLevelAppState::openNavDrawer,
                     defaultTitle = title,
                     qNavigator = qViewModel.qNavigator,
                     showNetworkDialog = showNetworkDialog,
                     accountOpenState = accountOpen
                  )
               }
            ) { innerPadding ->
               Column(
                  Modifier
                     .padding(innerPadding)
                     .fillMaxSize()
               )
               {
                  CompositionLocalProvider(LocalProcessInputAllowed provides {
                     parentInputAllowed() && businessReady && qViewModel.isAuthenticated && !qViewModel.isLogoutRequested &&
                        !drawerActive() && drawerWidth.intValue > 0 && topLevelAppState.navDrawerState.currentOffset.isFinite() &&
                        !settingsOpen.value && !diagnosticsOpen.value && !accountOpen.value &&
                        qViewModel.qNavigator.leaveRequest == null
                  }) {
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
      }
      // Authenticated content can register later; both handlers use the same live priority.
      BackHandler(enabled = drawerActive(), onBack = navigateBack)
      if (settingsOpen.value) MenuDialog(qViewModel, title, settingsOpen)
      if (diagnosticsOpen.value) AlertDialog(
         onDismissRequest = { diagnosticsOpen.value = false },
         title = { Text("Diagnostics") }, text = { Text("Server: ${qViewModel.environment.baseUrl}\nApplication version: ${QViewModel.applicationVersion}") },
         confirmButton = { TextButton(onClick = { diagnosticsOpen.value = false }) { Text("Close") } })
      LeaveProcessDialog(qViewModel)
   }

}


