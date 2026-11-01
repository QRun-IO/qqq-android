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

package com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication

import android.util.Log
import com.auth0.android.Auth0
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.Auth0AuthenticationMetaData
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.LoadState
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel


/*******************************************************************************
 ** class to work with auth0
 *******************************************************************************/
object QAuth0Service {
   lateinit var scheme: String

   private const val TAG = "QAuth0Service"

   /*******************************************************************************
    *******************************************************************************/
   fun makeAuth0Instance(qViewModel: QViewModel): Auth0 {
      if (!::scheme.isInitialized) {
         throw (IllegalStateException("QAuth0Service.scheme was not initialized!"))
      }

      when (val loadState = qViewModel.authenticationMetaDataLoadState) {
         is LoadState.Success -> {
            val authenticationMetaData = loadState.value as Auth0AuthenticationMetaData
            val auth0Instance: Auth0 = Auth0.getInstance(authenticationMetaData.values.clientId, authenticationMetaData.values.baseUrl)
            Log.d(TAG, "clientId: ${auth0Instance.clientId}, domain: ${auth0Instance.domain}")
            return auth0Instance
         }

         else -> {
            throw (IllegalStateException("authenticationMetaData is not loaded."))
         }
      }

   }


}
