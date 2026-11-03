/*
 * SPDX-License-Identifier: Apache-2.0
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2004-2026.  Kingsrook, LLC
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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*******************************************************************************
 **
 *******************************************************************************/
class NetworkPresenceTest {
   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun losingCellularDoesNotHideAnAvailableWifiNetwork() {
      val networks = NetworkPresence()
      assertTrue(networks.onAvailable(100))
      assertTrue(networks.onAvailable(101))
      assertTrue(networks.onLost(100))
      assertFalse(networks.onLost(101))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun duplicateAndStaleCallbacksDoNotInvalidateTheRemainingNetwork() {
      val networks = NetworkPresence()
      assertTrue(networks.onAvailable(100))
      assertTrue(networks.onAvailable(100))
      assertTrue(networks.onLost(999))
      assertFalse(networks.onLost(100))
      assertTrue(networks.onAvailable(101))
      assertTrue(networks.onLost(100))
   }
}
