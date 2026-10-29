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

package com.kingsrook.qqq.sampleandroidmobileapp

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.navigation.NavHostController
import com.kingsrook.qqq.frontend.android.mobileapp.data.ENVIRONMENT_JSON_KEY
import com.kingsrook.qqq.frontend.android.mobileapp.data.MockPreferencesDataStore
import com.kingsrook.qqq.frontend.android.mobileapp.data.SESSION_USER_FULL_NAME_KEY
import com.kingsrook.qqq.frontend.android.mobileapp.data.SESSION_UUID_KEY
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel
import com.kingsrook.qqq.sampleandroidmobileapp.utils.TestUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

/***************************************************************************
 **
 ***************************************************************************/
class SessionTests : BaseTest()
{
   @get:Rule
   val composeTestRule = createComposeRule()

   private val knownUUID = "some-known-uuid"
   private val fullName = "John Doe Test"
   private val envLabel = "Some Stored Env"

   /***************************************************************************
    **
    ***************************************************************************/
   @Test
   fun testLogin()
   {
      TestUtils.startStandardTest(composeTestRule)
      TestUtils.login(composeTestRule)

      assertHomePageIsDisplayed()

      atEndLocalDebugSleep()
   }

   /***************************************************************************
    **
    ***************************************************************************/
   @Test
   fun testValuesFromDataStore()
   {
      val dataStore = makeMockPreferencesDataStoreWithValues()
      val qViewModel = QViewModel(SampleAppMockQQQRepository(), dataStore)
      qViewModel.setNavController(null)

      TestUtils.startStandardTest(composeTestRule, qViewModel)

      //////////////////////////////////////////////////////////////
      // note - no login needed - should go straight to home page //
      // with known values in viewModel                           //
      //////////////////////////////////////////////////////////////
      assertHomePageIsDisplayed()
      assertEquals(fullName, qViewModel.sessionUserFullName)
      assertEquals(knownUUID, qViewModel.sessionUUID)

      openUserDialog()
      composeTestRule
         .onNodeWithText("Current User:  ${fullName}")
         .assertIsDisplayed()

      composeTestRule
         .onNodeWithText(envLabel)
         .assertIsDisplayed()
      composeTestRule
         .onNodeWithText("Dismiss")
         .performClick()

      atEndLocalDebugSleep()
   }

   /***************************************************************************
    **
    ***************************************************************************/
   @Test
   fun testExpiredSessionFromDataStore()
   {
      val failureUUID = "please-fail"
      val dataStore = makeMockPreferencesDataStoreWithValues()
      (dataStore.preferences as MutablePreferences)[SESSION_UUID_KEY] = failureUUID
      val qViewModel = QViewModel(SampleAppMockQQQRepository(), dataStore)

      TestUtils.startStandardTest(composeTestRule, qViewModel)

      ///////////////////////////////////////////////////////////////////////////
      // instead of logging in automatically, we should land on a login screen //
      ///////////////////////////////////////////////////////////////////////////
      composeTestRule
         .onNodeWithText("Click to log in Anonymously:")
         .assertIsDisplayed()

      /////////////////////////////////////
      // and we should be able to log in //
      /////////////////////////////////////
      TestUtils.login(composeTestRule)
      assertHomePageIsDisplayed()

      assertNotEquals(failureUUID, qViewModel.sessionUUID)

      assertNotEquals(failureUUID, dataStore.preferences[SESSION_UUID_KEY])
      // in case the above ever fails, it probably means race condition, so this instead
      // composeTestRule.waitUntil(condition =
      // {
      //    failureUUID != dataStore.preferences[SESSION_UUID_KEY]
      // })

      atEndLocalDebugSleep()
   }

   /***************************************************************************
    **
    ***************************************************************************/
   @Test
   fun testUserInfoDialogAndLogout()
   {
      TestUtils.startStandardTest(composeTestRule)

      openUserDialog()

      composeTestRule
         .onNodeWithText("Not currently logged in")
         .assertIsDisplayed()

      composeTestRule
         .onNode(hasAnyAncestor(isDialog()) and hasText("Log Out"))
         .assertIsDisplayed()
         .performClick()
         .assertIsNotEnabled()

      composeTestRule
         .onNode(hasAnyAncestor(isDialog()) and hasText("Dismiss"))
         .assertIsDisplayed()
         .performClick()

      TestUtils.login(composeTestRule)

      openUserDialog()

      composeTestRule
         .onNodeWithText("Current User:  Anonymous")
         .assertIsDisplayed()

      composeTestRule
         .onNode(hasAnyAncestor(isDialog()) and hasText("Log Out"))
         .assertIsEnabled()
         .performClick()

      composeTestRule
         .onNodeWithText("Signed out.")
         .assertIsDisplayed()

      composeTestRule
         .onNodeWithText("Log back in")
         .assertIsDisplayed()
         .performClick()

      TestUtils.login(composeTestRule)
      assertHomePageIsDisplayed()

      atEndLocalDebugSleep()
   }

   /***************************************************************************
    **
    ***************************************************************************/
   @Test
   fun testEnvironmentDialogAndSwitch()
   {
      TestUtils.startStandardTest(composeTestRule)
      TestUtils.login(composeTestRule)

      openUserDialog()

      composeTestRule
         .onNodeWithText("Test Env 1")
         .assertIsDisplayed()
         .performClick()

      composeTestRule
         .onNodeWithText("Test Env 2")
         .assertIsDisplayed()
         .performClick()

      composeTestRule
         .onNodeWithText("Switch")
         .assertIsDisplayed()
         .performClick()

      TestUtils.login(composeTestRule)

      ////////////////////////////////////////////////////////
      // Test Env 2 has different apps than the default env //
      ////////////////////////////////////////////////////////
      assertHomePageIsDisplayed(anAppLabelOnPage = "App 3")

      atEndLocalDebugSleep()
   }

   /***************************************************************************
    **
    ***************************************************************************/
   private fun makeMockPreferencesDataStoreWithValues(): MockPreferencesDataStore
   {
      val dataStore = MockPreferencesDataStore()
      dataStore.preferences = preferencesOf(
         SESSION_UUID_KEY to knownUUID,
         SESSION_USER_FULL_NAME_KEY to fullName,
         ENVIRONMENT_JSON_KEY to """
               {"label": "${envLabel}", "baseUrl": "http://was-stored"}
            """.trimIndent()
      ).toMutablePreferences()
      return dataStore
   }

   /***************************************************************************
    **
    ***************************************************************************/
   private fun openUserDialog()
   {
      composeTestRule
         .onNodeWithTag("topBar.menuIcon", true)
         .assertIsDisplayed()
         .assertHasClickAction()
         .performClick()

      composeTestRule
         .onNodeWithTag("menuDialog.title")
         .assertIsDisplayed()
   }

   /***************************************************************************
    **
    ***************************************************************************/
   private fun assertHomePageIsDisplayed(pageTitle: String = "Test App", anAppLabelOnPage: String = "App 1")
   {
      composeTestRule
         .onNodeWithTag("topAppBar.title")
         .assertTextContains(pageTitle)
         .assertIsDisplayed()

      composeTestRule
         .onNodeWithText(anAppLabelOnPage)
         .assertIsDisplayed()
   }

   /***************************************************************************
    **
    ***************************************************************************/
   private fun atEndLocalDebugSleep()
   {
      TestUtils.localDebugSleep(composeTestRule, 0)
   }

}
