/*
 * SPDX-License-Identifier: Apache-2.0
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

package com.kingsrook.qqq.frontend.android.mobileapp.ui.horseshoe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.TopLevelAppState

/*******************************************************************************
 *******************************************************************************/
@Composable
fun QNavigationDrawer(topLevelAppState: TopLevelAppState, content: @Composable ColumnScope.() -> Unit) {
   ModalNavigationDrawer(
      drawerState = topLevelAppState.navDrawerState,
      drawerContent =
         {
            ModalDrawerSheet(modifier = Modifier.padding(top = 24.dp))
            {
               Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth())
               {
                  Text("QNav Drawer title", fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))

                  IconButton(onClick = topLevelAppState::closeNavDrawer)
                  {
                     Icon(imageVector = Icons.Filled.Close, contentDescription = "Close Menu")
                  }
               }
               HorizontalDivider()

               if (topLevelAppState.isLoggedIn()) {
                  NavigationDrawerItem(
                     label =
                        {
                           Text(text = "Log Out")
                        },
                     icon = { Icon(imageVector = Icons.Default.AccountCircle, contentDescription = "Log Out") },
                     selected = false,
                     onClick =
                        {
                           topLevelAppState.logOut()
                           topLevelAppState.closeNavDrawer()
                        }
                  )
               } else {
                  Row(
                     horizontalArrangement = Arrangement.spacedBy(12.dp),
                     verticalAlignment = Alignment.CenterVertically,
                     modifier = Modifier.fillMaxWidth().padding(16.dp)
                  )
                  {
                     Icon(imageVector = Icons.Default.AccountCircle, contentDescription = "Log Out", tint = Color.Gray)
                     Text("Log Out", color = Color.Gray)
                  }
               }

               // ...other drawer items
            }
         }
   ) {
      Column(modifier = Modifier, content = content)
   }

}
