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

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.Auth0AuthenticationMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.BaseAuthenticationMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.FullyAnonymousAuthenticationMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.TableBasedAuthenticationMetaData
import com.kingsrook.qqq.frontend.android.mobileapp.ui.common.FullSizedAllCenteredColumn
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel
import java.util.UUID


/***************************************************************************
 ** Display an appropriate login form, based on the authenticationMetaData
 ** type.
 ***************************************************************************/
@Composable
fun QLoginForm(qViewModel: QViewModel, authenticationMetaData: BaseAuthenticationMetaData, modifier: Modifier = Modifier)
{
   when (authenticationMetaData)
   {
      is TableBasedAuthenticationMetaData -> QPasswordLogin(qViewModel, modifier)
      is Auth0AuthenticationMetaData ->
      {
         QAuth0Login(qViewModel, modifier)
      }

      is FullyAnonymousAuthenticationMetaData ->
      {
         FullSizedAllCenteredColumn()
         {
            Text("Click to log in Anonymously:", modifier = Modifier.padding(bottom = 16.dp))

            Button(onClick = {
               qViewModel.doSetSessionUserFullName("Anonymous");
               qViewModel.manageSession(UUID.randomUUID().toString())
            }) { Text("Log In") }
         }
      }

      else ->
      {
         Text("Error: Unrecognized AuthenticationMetaData type: ${authenticationMetaData.javaClass.simpleName}", color = Color.Red)
      }
   }
}
