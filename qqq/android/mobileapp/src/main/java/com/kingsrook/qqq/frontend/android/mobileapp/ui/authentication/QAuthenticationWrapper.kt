/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2024-2024.  Kingsrook, LLC
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
