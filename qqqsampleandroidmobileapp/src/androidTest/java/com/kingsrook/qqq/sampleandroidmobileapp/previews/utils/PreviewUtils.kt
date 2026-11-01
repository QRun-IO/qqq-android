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

package com.kingsrook.qqq.sampleandroidmobileapp.previews.utils

import com.kingsrook.qqq.sampleandroidmobileapp.SampleAppMockQQQRepository
import com.kingsrook.qqq.frontend.android.core.model.Environment
import com.kingsrook.qqq.frontend.android.mobileapp.container.QAppContainer
import com.kingsrook.qqq.frontend.android.mobileapp.data.MockPreferencesDataStore
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel

/*******************************************************************************
 **
 *******************************************************************************/
object PreviewUtils {
   /*******************************************************************************
    **
    *******************************************************************************/
   fun createQViewModel(): QViewModel {
      QAppContainer.availableEnvironments = listOf(
         Environment("Production", "https://live.coldtrack.com/"),
         Environment("Staging", "https://live.coldtrack-staging.com/"),
         Environment("Production", "https://live.coldtrack-dev.com/"),
         Environment("Local Test", "http://192.168.4.67:8000/"),
      )

      val dataStore = MockPreferencesDataStore()
      val qViewModel = QViewModel(SampleAppMockQQQRepository(), dataStore)
      return (qViewModel)
   }
}
