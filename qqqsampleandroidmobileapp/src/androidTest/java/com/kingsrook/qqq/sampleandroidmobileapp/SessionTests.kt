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

/*******************************************************************************
 **
 *******************************************************************************/
class SessionTests : BaseTest() {
   @get:Rule
   val composeTestRule = createComposeRule()

   private val knownUUID = "some-known-uuid"
   private val fullName = "John Doe Test"
   private val envLabel = "Some Stored Env"

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun testLogin() {
      TestUtils.startStandardTest(composeTestRule)
      TestUtils.login(composeTestRule)

      assertHomePageIsDisplayed()

      atEndLocalDebugSleep()
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun testValuesFromDataStore() {
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

      assertEquals(envLabel, qViewModel.environment.label)
      composeTestRule
         .onNodeWithText("Close")
         .performClick()
      composeTestRule.onNodeWithTag("topBar.menuIcon").performClick()
      composeTestRule.onNodeWithTag("drawer.settings").performClick()
      composeTestRule.onNodeWithText("Server: ${qViewModel.environment.baseUrl}").assertIsDisplayed()
      composeTestRule.onNodeWithText("Close").performClick()

      atEndLocalDebugSleep()
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun testExpiredSessionFromDataStore() {
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

      atEndLocalDebugSleep()
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun testUserInfoDialogAndLogout() {
      TestUtils.startStandardTest(composeTestRule)

      openUserDialog()

      composeTestRule
         .onNodeWithText("Not currently logged in")
         .assertIsDisplayed()

      composeTestRule
         .onNode(hasAnyAncestor(isDialog()) and hasText("Sign out"))
         .assertIsDisplayed()
         .performClick()
         .assertIsNotEnabled()

      composeTestRule
         .onNode(hasAnyAncestor(isDialog()) and hasText("Close"))
         .assertIsDisplayed()
         .performClick()

      TestUtils.login(composeTestRule)

      openUserDialog()

      composeTestRule
         .onNodeWithText("Current User:  Anonymous")
         .assertIsDisplayed()

      composeTestRule
         .onNode(hasAnyAncestor(isDialog()) and hasText("Sign out"))
         .assertIsEnabled()
         .performClick()

      composeTestRule.onNodeWithText("Stay").performClick()
      assertEquals(true, TestUtils.qViewModel.isAuthenticated)
      openUserDialog()
      composeTestRule.onNode(hasAnyAncestor(isDialog()) and hasText("Sign out")).performClick()
      composeTestRule.onNode(hasAnyAncestor(isDialog()) and hasText("Sign out")).performClick()

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

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun testEnvironmentDialogAndSwitch() {
      TestUtils.startStandardTest(composeTestRule)
      TestUtils.login(composeTestRule)

      composeTestRule.onNodeWithTag("topBar.menuIcon").performClick()
      composeTestRule.onNodeWithTag("drawer.settings").performClick()

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

      composeTestRule.onNodeWithText("Change server", substring = false).performClick()

      TestUtils.login(composeTestRule)

      ////////////////////////////////////////////////////////
      // Test Env 2 has different apps than the default env //
      ////////////////////////////////////////////////////////
      assertHomePageIsDisplayed(anAppLabelOnPage = "App 3")

      atEndLocalDebugSleep()
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   private fun makeMockPreferencesDataStoreWithValues(): MockPreferencesDataStore {
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

   /*******************************************************************************
    **
    *******************************************************************************/
   private fun openUserDialog() {
      composeTestRule
         .onNodeWithTag("topBar.accountIcon", true)
         .assertIsDisplayed()
         .assertHasClickAction()
         .performClick()

      composeTestRule
         .onNodeWithTag("accountDialog.title")
         .assertIsDisplayed()
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   private fun assertHomePageIsDisplayed(pageTitle: String = "Apps", anAppLabelOnPage: String = "App 1") {
      composeTestRule
         .onNodeWithTag("topAppBar.title")
         .assertTextContains(pageTitle)
         .assertIsDisplayed()

      composeTestRule
         .onNodeWithTag("appHome.rowForApp:" + if (anAppLabelOnPage == "App 3") "app3" else "app1")
         .assertIsDisplayed()
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   private fun atEndLocalDebugSleep() {
      TestUtils.localDebugSleep(composeTestRule, 0)
   }

}
