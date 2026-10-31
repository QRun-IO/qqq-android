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

package com.kingsrook.qqq.frontend.android.mobileapp.viewmodel

import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/***************************************************************************
 **
 ***************************************************************************/
class TopLevelAppState(
   private val coroutineScope: CoroutineScope,
   private val qViewModel: QViewModel
)
{
   var navDrawerState: DrawerState by mutableStateOf(DrawerState(DrawerValue.Closed))
      private set

   /***************************************************************************
    **
    ***************************************************************************/
   fun openNavDrawer()
   {
      coroutineScope.launch()
      {
         navDrawerState.open()
      }
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun closeNavDrawer()
   {
      coroutineScope.launch()
      {
         navDrawerState.close()
      }
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun logOut()
   {
      this.qViewModel.requestLogout()
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun isLoggedIn(): Boolean
   {
      return this.qViewModel.isAuthenticated
   }

}