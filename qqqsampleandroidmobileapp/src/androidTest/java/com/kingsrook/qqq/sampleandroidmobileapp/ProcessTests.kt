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

package com.kingsrook.qqq.sampleandroidmobileapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.kingsrook.qqq.frontend.android.core.model.metadata.QAppNodeType
import com.kingsrook.qqq.sampleandroidmobileapp.utils.TestUtils
import com.kingsrook.qqq.sampleandroidmobileapp.utils.clickAppScreenOption
import org.junit.Rule
import org.junit.Test

/*******************************************************************************
 **
 *******************************************************************************/
class ProcessTests : BaseTest() {
   @get:Rule
   val composeTestRule = createComposeRule()

   /*******************************************************************************
    **
    *******************************************************************************/
   private fun atEndLocalDebugSleep() {
      TestUtils.localDebugSleep(composeTestRule, 5)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun testLaunchProcess() {
      TestUtils.startStandardTest(composeTestRule)
      TestUtils.login(composeTestRule)

      composeTestRule.clickAppScreenOption(QAppNodeType.APP, "app1")

      composeTestRule
         .onNodeWithTag("topAppBar.title")
         .assertTextContains("App 1")
         .assertIsDisplayed()
         .performClick()

      composeTestRule.clickAppScreenOption(QAppNodeType.PROCESS, "process1")

      composeTestRule
         .onNodeWithTag("topAppBar.title")
         .assertTextContains("Process 1")
         .assertIsDisplayed()
         .performClick()

      atEndLocalDebugSleep()
   }

}
