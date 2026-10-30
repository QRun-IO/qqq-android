/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2004-2024.  Kingsrook, LLC
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
 *
 */

package com.kingsrook.qqq.sampleandroidmobileapp.utils

import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToLog
import androidx.navigation.compose.rememberNavController
import com.kingsrook.qqq.frontend.android.core.model.metadata.QAppNodeType
import com.kingsrook.qqq.frontend.android.mobileapp.QMobileAppMain
import com.kingsrook.qqq.sampleandroidmobileapp.SampleAppMockQQQRepository
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel
import com.kingsrook.qqq.frontend.android.mobileapp.data.MockPreferencesDataStore

/***************************************************************************
 **
 ***************************************************************************/
object TestUtils
{
   lateinit var qViewModel: QViewModel
   lateinit var toast: Toast

   /***************************************************************************
    **
    ***************************************************************************/
   fun startStandardTest(composeTestRule: ComposeContentTestRule, qViewModelParam: QViewModel? = null)
   {
      if(qViewModelParam == null)
      {
         this.qViewModel = createQViewModel()
      }
      else
      {
         this.qViewModel = qViewModelParam
      }

      composeTestRule.setContent()
      {
         toast = Toast.makeText(LocalContext.current, "This is a test", 3)

         val navController = rememberNavController()
         qViewModel.setNavController(navController)

         QMobileAppMain(this.qViewModel, "Test App")
      }
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun login(composeTestRule: ComposeContentTestRule)
   {
      composeTestRule.onNodeWithText("Log In").performClick()
      composeTestRule.waitUntil("Should become authed", timeoutMillis = 5000, condition =
      {
         qViewModel.isAuthenticated
      })
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun createQViewModel(): QViewModel
   {
      val dataStore = MockPreferencesDataStore()
      val qViewModel = QViewModel(SampleAppMockQQQRepository(), dataStore)
      return (qViewModel)
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun sleep(composeTestRule: ComposeContentTestRule, seconds: Int)
   {
      composeTestRule.waitUntil("Intentional Sleep", timeoutMillis = (seconds + 1) * 1000L, condition =
      {
         try
         {
            Thread.sleep(seconds * 1000L)
            return@waitUntil true
         }
         catch(e: Exception)
         {
            // noop, just return
            return@waitUntil true
         }
      })
   }

   /***************************************************************************
    ** Sleep, but, only when running locally, e.g., to see the screen and debug.
    ** will this cause tests to behave differently in CI?  We shall see!
    ***************************************************************************/
   fun localDebugSleep(composeTestRule: ComposeContentTestRule, seconds: Int = 300)
   {
      if(seconds == 0)
      {
         return
      }

      if(System.getenv("CIRCLECI") != null)
      {
         println("In CIRCLECI - so - not doing a localDebugSleep")
         return
      }

      showToast("Going into a localDebugSleep sleep \uD83D\uDECC now for ${seconds} second${if(seconds == 1) "" else "s"} ⏰")
      sleep(composeTestRule, seconds)
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun logTree(composeTestRule: ComposeContentTestRule, useUnmergedTree: Boolean = false)
   {
      composeTestRule.onRoot(useUnmergedTree = useUnmergedTree)
         .printToLog("currentLabelExists")
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun showToast(text: String)
   {
      toast.setText(text)
      toast.show()
   }
}

/***************************************************************************
 **
 ***************************************************************************/
@OptIn(ExperimentalTestApi::class) // for waitUntilAtLeastOneExists
fun ComposeContentTestRule.clickAppScreenOption(type: QAppNodeType, childName: String)
{
   val testTag = "appHome.rowFor" + when(type)
   {
      QAppNodeType.APP -> "App:"
      QAppNodeType.TABLE -> "Table:"
      QAppNodeType.PROCESS -> "Process:"
      QAppNodeType.REPORT -> "Report:"
      QAppNodeType.UNKNOWN -> error("Unknown app node types cannot be selected")
   } + childName

   this.waitUntilAtLeastOneExists(hasTestTag(testTag))

   this.onNode(hasTestTag(testTag))
      .assertIsDisplayed()
      .assertIsEnabled()
      .performClick()
}

