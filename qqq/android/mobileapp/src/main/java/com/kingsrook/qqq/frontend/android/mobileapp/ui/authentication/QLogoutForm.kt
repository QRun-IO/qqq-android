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

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.Auth0AuthenticationMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.BaseAuthenticationMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.FullyAnonymousAuthenticationMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.TableBasedAuthenticationMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.OAuth2AuthenticationMetaData
import com.kingsrook.qqq.frontend.android.mobileapp.ui.common.FullSizedAllCenteredColumn
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel


/***************************************************************************
 ** Display an appropriate logout screen/form, based on the authenticationMetaData
 ** type.
 ***************************************************************************/
@Composable
fun QLogoutForm(qViewModel: QViewModel, authenticationMetaData: BaseAuthenticationMetaData, modifier: Modifier = Modifier)
{
   when (authenticationMetaData)
   {
      is Auth0AuthenticationMetaData ->
      {
         QAuth0Logout(qViewModel, modifier)
      }

      is FullyAnonymousAuthenticationMetaData, is TableBasedAuthenticationMetaData, is OAuth2AuthenticationMetaData ->
      {
         LaunchedEffect(true)
         {
            ///////////////////////////////////////////////////////////////////
            // only run the logout code the first time this code is rendered //
            ///////////////////////////////////////////////////////////////////
            qViewModel.logOut();
         }

         FullSizedAllCenteredColumn()
         {
            Text(qViewModel.logoutMessage.ifEmpty { "Signing out…" }, modifier = Modifier.padding(bottom = 16.dp))

            Button(enabled = !qViewModel.logoutInProgress, onClick = { qViewModel.requestLogin() })
            {
               Text("Log back in")
            }
         }
      }

      else ->
      {
         Text("Error: Unrecognized AuthenticationMetaData type: ${authenticationMetaData.javaClass.simpleName}", color = Color.Red)
      }
   }
}
