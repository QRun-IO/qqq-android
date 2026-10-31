/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2024-2024.  Kingsrook, LLC
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

package com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.BaseAuthenticationMetaData
import com.kingsrook.qqq.frontend.android.mobileapp.ui.QNavigator
import com.kingsrook.qqq.frontend.android.mobileapp.ui.LocalAuthenticatedContent
import com.kingsrook.qqq.frontend.android.mobileapp.ui.horseshoe.QApplicationWrapper
import com.kingsrook.qqq.frontend.android.mobileapp.ui.utils.LoadStateView
import com.kingsrook.qqq.frontend.android.mobileapp.ui.utils.Reloader
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.LoadState
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel

/***************************************************************************
 ** Composable that wraps our entire application - providing a login form
 ** when needed, or a logout screen, else showing the app.
 ***************************************************************************/
@Composable
fun QAuthenticationWrapper(qViewModel: QViewModel, modifier: Modifier = Modifier, qNavigator: QNavigator? = null)
{
   val isAuthenticated = qViewModel.isAuthenticated;
   val authenticationMetaDataLoadState = qViewModel.authenticationMetaDataLoadState;
   val initialLoadFromDataStoreLoadState = qViewModel.initialLoadFromDataStoreLoadState;
   val loadStates = listOf(initialLoadFromDataStoreLoadState, authenticationMetaDataLoadState);

   Column()
   {
      LoadStateView(
         loadStates,
         modifier,
         reloader = Reloader("Try again", { qViewModel.reInitAfterFailure() }),
         loadingText = "Authenticating app environment: ${qViewModel.environment.label}"
      )
      {
         val authenticationMetaData = (authenticationMetaDataLoadState as LoadState.Success<BaseAuthenticationMetaData>).value

         if(qViewModel.isLogoutRequested)
         {
            QLogoutForm(qViewModel, authenticationMetaData)
         }
         else if(isAuthenticated)
         {
            LocalAuthenticatedContent.current(qViewModel) { QApplicationWrapper(qViewModel, Modifier, qNavigator) }
         }
         else
         {
            QLoginForm(qViewModel, authenticationMetaData)
         }
      }
   }
}
