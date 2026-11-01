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

package com.kingsrook.qqq.frontend.android.core.model.metadata.authentication

import kotlinx.serialization.json.Json
import org.junit.Test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/*******************************************************************************
 **
 *******************************************************************************/
class AuthenticationSerializerTest {
   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun testDecodeAuth0() {
      val json = """
        {
           "name":"auth0",
           "type":"AUTH_0",
           "values":
           {
              "baseUrl":"https://auth.coldtrack-dev.com/",
              "clientId":"Q7hjPeDrfq11EgZyFxpoXQRTPhTn3KzN",
              "audience":"https://www.nutrifresh-one-dev.com"
           }
        }
        """

      val withUnknownKeys = Json { ignoreUnknownKeys = true }
      val parsedItem = withUnknownKeys.decodeFromString<BaseAuthenticationMetaData>(json)
      assertTrue(parsedItem is Auth0AuthenticationMetaData)
      assertEquals("Q7hjPeDrfq11EgZyFxpoXQRTPhTn3KzN", (parsedItem as Auth0AuthenticationMetaData).values.clientId)
   }
}
