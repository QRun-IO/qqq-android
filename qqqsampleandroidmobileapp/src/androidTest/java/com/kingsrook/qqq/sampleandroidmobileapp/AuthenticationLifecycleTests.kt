/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.sampleandroidmobileapp

import android.content.Context
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.auth0.android.Auth0
import com.auth0.android.authentication.AuthenticationException
import com.auth0.android.callback.Callback
import com.auth0.android.result.Credentials
import com.kingsrook.qqq.frontend.android.core.controllers.QQQRepository
import com.kingsrook.qqq.frontend.android.core.model.Environment
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.Auth0AuthenticationMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.Auth0AuthenticationMetaDataValues
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.ManageSessionRequest
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.ManageSessionResponse
import com.kingsrook.qqq.frontend.android.mobileapp.container.QAppContainer
import com.kingsrook.qqq.frontend.android.mobileapp.data.MockPreferencesDataStore
import com.kingsrook.qqq.frontend.android.mobileapp.data.SESSION_UUID_KEY
import com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication.Auth0Browser
import com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication.LocalAuth0Browser
import com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication.QAuth0Login
import com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication.QAuth0Logout
import com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication.QAuth0Service
import com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication.QAuthenticationWrapper
import com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication.OAuth2LoginBinding
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.LoadState
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.After
import org.junit.Rule
import org.junit.Test

/*******************************************************************************
 ** Deliberately delayed, cancellation-resistant responses model a server finishing after local sign-out.
 *******************************************************************************/
class AuthenticationLifecycleTests : BaseTest() {
   @get:Rule
   val ui = createAndroidComposeRule<ComponentActivity>()
   private val first = Environment("First", "https://first.example.test/")
   private val second = Environment("Second", "https://second.example.test/")
   private val fixtures = mutableListOf<Fixture>()

   /*******************************************************************************
    **
    *******************************************************************************/
   @After
   fun releaseHeldResponses() {
      fixtures.forEach { it.release.complete(Unit); it.releaseLogout.complete(Unit); it.store.releaseWrite.complete(Unit) }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   private class HeldStore : DataStore<Preferences> {
      private val delegate = MockPreferencesDataStore()
      val preferences get() = delegate.preferences
      override val data get() = delegate.data
      val holdNextWrite = AtomicBoolean()
      val writeStarted = CompletableDeferred<Unit>()
      val releaseWrite = CompletableDeferred<Unit>()

      /*******************************************************************************
       **
       *******************************************************************************/
      override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = delegate.updateData {
         val next = transform(it)
         if (holdNextWrite.compareAndSet(true, false)) {
            writeStarted.complete(Unit)
            releaseWrite.await()
         }
         next
      }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   private inner class Fixture(holdFirst: Boolean, holdLogout: Boolean, holdFinalWrite: Boolean) {
      val store = HeldStore()
      val calls = AtomicInteger()
      val cookie = AtomicReference("")
      val discarded = CopyOnWriteArrayList<Pair<String, String>>()
      val release = CompletableDeferred<Unit>()
      val releaseLogout = CompletableDeferred<Unit>()
      val logoutCalls = AtomicInteger()
      val browserCalls = AtomicInteger()
      val providerLogoutCalls = AtomicInteger()
      val callback = AtomicReference<Callback<Credentials, AuthenticationException>?>()
      val logoutCallback = AtomicReference<Callback<Void?, AuthenticationException>?>()
      val metadata = Auth0AuthenticationMetaData(
         "Auth0", Auth0AuthenticationMetaDataValues(
            "https://identity.example.test", "public-client", "https://api.example.test"
         )
      )
      val repository = object : QQQRepository by SampleAppMockQQQRepository() {
         /*******************************************************************************
          **
          *******************************************************************************/
         override suspend fun getAuthenticationMetaData() = metadata

         /*******************************************************************************
          **
          *******************************************************************************/
         override fun setSessionUUID(sessionUUID: String) {
            cookie.set(sessionUUID)
         }

         /*******************************************************************************
          **
          *******************************************************************************/
         override suspend fun logout() {
            logoutCalls.incrementAndGet()
            if (holdLogout) releaseLogout.await()
            cookie.set("")
            if (holdFinalWrite) store.holdNextWrite.set(true)
         }

         /*******************************************************************************
          **
          *******************************************************************************/
         private suspend fun exchange(): ManageSessionResponse {
            val number = calls.incrementAndGet()
            if (holdFirst && number == 1) withContext(NonCancellable) { release.await() }
            return ManageSessionResponse("synthetic-session-$number")
         }

         /*******************************************************************************
          **
          *******************************************************************************/
         override suspend fun loginWithPassword(username: String, password: String) = exchange()

         /*******************************************************************************
          **
          *******************************************************************************/
         override suspend fun manageSession(body: ManageSessionRequest) = exchange()

         /*******************************************************************************
          **
          *******************************************************************************/
         override suspend fun discardNativeSession(server: String, session: String) {
            discarded.add(server to session)
         }
      }
      lateinit var model: QViewModel
      val browser = object : Auth0Browser {
         /*******************************************************************************
          **
          *******************************************************************************/
         override fun login(
            context: Context, account: Auth0, audience: String, scheme: String,
            callback: Callback<Credentials, AuthenticationException>
         ) {
            browserCalls.incrementAndGet(); this@Fixture.callback.set(callback)
         }

         /*******************************************************************************
          **
          *******************************************************************************/
         override fun logout(context: Context, account: Auth0, scheme: String, callback: Callback<Void?, AuthenticationException>) {
            providerLogoutCalls.incrementAndGet(); logoutCallback.set(callback)
         }
      }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   private fun start(holdFirst: Boolean = true, holdLogout: Boolean = false, holdFinalWrite: Boolean = false): Fixture {
      QAppContainer.availableEnvironments = listOf(first, second)
      QAuth0Service.scheme = "qqq-test"
      val fixture = Fixture(holdFirst, holdLogout, holdFinalWrite).also { fixtures.add(it) }
      ui.runOnIdle { fixture.model = QViewModel(fixture.repository, fixture.store) }
      ui.waitUntil(5000) { fixture.model.initialLoadFromDataStoreLoadState is LoadState.Success }
      return fixture
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   private fun assertCleared(fixture: Fixture) {
      ui.runOnIdle {
         assertFalse(fixture.model.isAuthenticated)
         assertEquals("", fixture.model.sessionUUID)
         assertEquals("", fixture.cookie.get())
         assertEquals("", fixture.store.preferences[SESSION_UUID_KEY])
      }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun delayedPasswordAfterLogoutCannotRestoreRetainedModel() {
      val fixture = start()
      ui.runOnIdle { fixture.model.loginWithPassword("operator", "synthetic-password") }
      ui.waitUntil(5000) { fixture.calls.get() == 1 }
      ui.runOnIdle { fixture.model.logOut() }
      ui.waitUntil(5000) { !fixture.model.logoutInProgress }
      fixture.release.complete(Unit)
      ui.waitUntil(5000) { fixture.discarded.size == 1 }
      assertCleared(fixture)
      assertEquals(first.baseUrl to "synthetic-session-1", fixture.discarded.single())
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun delayedPasswordFromPreviousServerCannotReplaceNewOperator() {
      val fixture = start()
      ui.runOnIdle { fixture.model.loginWithPassword("old", "synthetic-password") }
      ui.waitUntil(5000) { fixture.calls.get() == 1 }
      ui.runOnIdle { fixture.model.switchEnvironment(second) {} }
      ui.waitUntil(5000) { fixture.model.environment == second && fixture.model.authenticationMetaDataLoadState is LoadState.Success }
      ui.runOnIdle { fixture.model.loginWithPassword("new", "synthetic-password") }
      ui.waitUntil(5000) { fixture.model.isAuthenticated && fixture.store.preferences[SESSION_UUID_KEY] == "synthetic-session-2" }
      fixture.release.complete(Unit)
      ui.waitUntil(5000) { fixture.discarded.size == 1 }
      ui.runOnIdle {
         assertEquals("synthetic-session-2", fixture.model.sessionUUID)
         assertEquals("synthetic-session-2", fixture.cookie.get())
         assertEquals("new", fixture.model.sessionUserFullName)
         assertEquals("synthetic-session-2", fixture.store.preferences[SESSION_UUID_KEY])
      }
      assertEquals(first.baseUrl to "synthetic-session-1", fixture.discarded.single())
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   private fun showAuth0(fixture: Fixture) {
      ui.setContent { CompositionLocalProvider(LocalAuth0Browser provides fixture.browser) { QAuth0Login(fixture.model) } }
      ui.waitUntil(5000) { fixture.callback.get() != null }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   private fun credentials(): Credentials {
      /*******************************************************************************
       **
       *******************************************************************************/
      fun encoded(value: String) = Base64.encodeToString(value.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
      val token = encoded("""{"alg":"none"}""") + "." + encoded("""{"sub":"synthetic","nickname":"Operator"}""") + ".synthetic"
      return Credentials(token, "synthetic-provider-token", "Bearer", null, Date(System.currentTimeMillis() + 60000), "openid")
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun lateAuth0BrowserCallbackAfterLogoutSendsNoToken() {
      val fixture = start()
      showAuth0(fixture)
      val callback = requireNotNull(fixture.callback.get())
      ui.runOnIdle { fixture.model.logOut() }
      ui.waitUntil(5000) { !fixture.model.logoutInProgress }
      callback.onSuccess(credentials())
      ui.waitForIdle()
      assertEquals(0, fixture.calls.get())
      assertCleared(fixture)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun lateAuth0BrowserCallbackAfterServerSwitchSendsNoToken() {
      val fixture = start()
      showAuth0(fixture)
      val callback = requireNotNull(fixture.callback.get())
      ui.runOnIdle { fixture.model.switchEnvironment(second) {} }
      ui.waitUntil(5000) { fixture.model.environment == second }
      callback.onSuccess(credentials())
      ui.waitForIdle()
      assertEquals(0, fixture.calls.get())
      assertCleared(fixture)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun delayedAuth0ExchangeAfterLogoutIsRevokedAtOriginalServer() {
      val fixture = start()
      showAuth0(fixture)
      requireNotNull(fixture.callback.get()).onSuccess(credentials())
      ui.waitUntil(5000) { fixture.calls.get() == 1 }
      ui.runOnIdle { fixture.model.logOut() }
      ui.waitUntil(5000) { !fixture.model.logoutInProgress }
      fixture.release.complete(Unit)
      ui.waitUntil(5000) { fixture.discarded.size == 1 }
      assertCleared(fixture)
      assertEquals(first.baseUrl to "synthetic-session-1", fixture.discarded.single())
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun duplicateAuth0SuccessCallbackStartsOnlyOneExchange() {
      val fixture = start()
      showAuth0(fixture)
      val callback = requireNotNull(fixture.callback.get())
      callback.onSuccess(credentials())
      ui.waitUntil(5000) { fixture.calls.get() == 1 }
      callback.onSuccess(credentials())
      ui.waitForIdle()
      assertEquals(1, fixture.calls.get())
      fixture.release.complete(Unit)
      ui.waitUntil(5000) { fixture.model.isAuthenticated && fixture.store.preferences[SESSION_UUID_KEY] == "synthetic-session-1" }
      assertEquals(1, fixture.calls.get())
      assertEquals("synthetic-session-1", fixture.cookie.get())
      assertTrue(fixture.discarded.isEmpty())
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun failedProviderLogoutStillClearsLocalSessionAndCannotRestoreOnRestart() {
      val fixture = start(holdFirst = false)
      ui.runOnIdle { fixture.model.loginWithPassword("operator", "synthetic-password") }
      ui.waitUntil(5000) { fixture.model.isAuthenticated && !fixture.store.preferences[SESSION_UUID_KEY].isNullOrBlank() }
      ui.runOnIdle { fixture.model.requestLogout() }
      ui.setContent { CompositionLocalProvider(LocalAuth0Browser provides fixture.browser) { QAuth0Logout(fixture.model) } }
      ui.waitUntil(5000) { fixture.logoutCallback.get() != null }
      requireNotNull(fixture.logoutCallback.get()).onFailure(AuthenticationException("synthetic_failure", "Synthetic provider cancellation"))
      ui.waitUntil(5000) { !fixture.model.logoutInProgress }
      assertCleared(fixture)
      lateinit var recreated: QViewModel
      ui.runOnIdle { recreated = QViewModel(fixture.repository, fixture.store) }
      ui.waitUntil(5000) { recreated.initialLoadFromDataStoreLoadState is LoadState.Success }
      ui.runOnIdle { assertFalse(recreated.isAuthenticated); assertEquals("", recreated.sessionUUID) }
      ui.runOnIdle {
         fixture.model.requestLogin()
         fixture.model.loginWithPassword("next", "synthetic-password")
      }
      ui.waitUntil(5000) { fixture.model.isAuthenticated && fixture.store.preferences[SESSION_UUID_KEY] == "synthetic-session-2" }
      requireNotNull(fixture.logoutCallback.get()).onSuccess(null)
      ui.waitForIdle()
      ui.runOnIdle {
         assertTrue(fixture.model.isAuthenticated)
         assertEquals("synthetic-session-2", fixture.model.sessionUUID)
         assertEquals("synthetic-session-2", fixture.cookie.get())
      }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun auth0EffectLaunchesOnceAndCurrentResponseIsAdopted() {
      val fixture = start(holdFirst = false)
      val redraw = mutableStateOf(0)
      ui.setContent {
         CompositionLocalProvider(LocalAuth0Browser provides fixture.browser) {
            Text("Redraw ${redraw.value}")
            QAuth0Login(fixture.model)
         }
      }
      ui.waitUntil(5000) { fixture.callback.get() != null }
      ui.runOnIdle { redraw.value++ }
      assertEquals(1, fixture.browserCalls.get())
      requireNotNull(fixture.callback.get()).onSuccess(credentials())
      ui.waitUntil(5000) { fixture.model.isAuthenticated && fixture.store.preferences[SESSION_UUID_KEY] == "synthetic-session-1" }
      assertEquals(1, fixture.calls.get())
      assertEquals("synthetic-session-1", fixture.cookie.get())
      assertTrue(fixture.discarded.isEmpty())
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun repeatedLogoutSharesOneRevocationAndCannotClearTheNextLogin() {
      val fixture = start(holdFirst = false, holdLogout = true)
      ui.runOnIdle { fixture.model.loginWithPassword("first", "synthetic-password") }
      ui.waitUntil(5000) { fixture.model.isAuthenticated }
      ui.runOnIdle {
         val firstLogout = fixture.model.logOut()
         val repeatedLogout = fixture.model.logOut()
         assertSame(firstLogout, repeatedLogout)
         fixture.model.loginWithPassword("blocked", "synthetic-password")
         assertTrue(fixture.model.logoutInProgress)
         assertNull(
            fixture.model.prepareOAuth2Login(
               OAuth2LoginBinding(
                  first.baseUrl, "provider",
                  "https://identity.example.test/", "https://identity.example.test/discovery", "public-client",
                  "openid", "qqq-test://callback", "login", "REQUIRED"
               )
            )
         )
      }
      ui.waitUntil(5000) { fixture.logoutCalls.get() == 1 }
      assertEquals(1, fixture.calls.get())
      fixture.releaseLogout.complete(Unit)
      ui.waitUntil(5000) { !fixture.model.logoutInProgress }
      assertCleared(fixture)
      ui.runOnIdle { fixture.model.loginWithPassword("next", "synthetic-password") }
      ui.waitUntil(5000) { fixture.model.isAuthenticated && fixture.store.preferences[SESSION_UUID_KEY] == "synthetic-session-2" }
      ui.runOnIdle {
         assertEquals(1, fixture.logoutCalls.get())
         assertEquals("synthetic-session-2", fixture.cookie.get())
         assertEquals("next", fixture.model.sessionUserFullName)
      }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun fullAuthenticationWrapperKeepsLogoutStableWhileStorageFinishes() {
      val fixture = start(holdFirst = false, holdFinalWrite = true)
      val visible = mutableStateOf(true)
      ui.runOnIdle { fixture.model.loginWithPassword("operator", "synthetic-password") }
      ui.waitUntil(5000) { fixture.model.isAuthenticated && !fixture.store.preferences[SESSION_UUID_KEY].isNullOrBlank() }
      ui.runOnIdle { fixture.model.requestLogout() }
      ui.setContent {
         CompositionLocalProvider(LocalAuth0Browser provides fixture.browser) {
            if (visible.value) QAuthenticationWrapper(fixture.model)
         }
      }
      ui.waitUntil(5000) { fixture.store.writeStarted.isCompleted }
      ui.runOnIdle {
         assertTrue(fixture.model.isLogoutRequested)
         assertTrue(fixture.model.authenticationMetaDataLoadState is LoadState.Success)
         assertTrue(fixture.model.logoutInProgress)
      }
      fixture.store.releaseWrite.complete(Unit)
      ui.waitUntil(5000) { !fixture.model.logoutInProgress }
      assertEquals(1, fixture.logoutCalls.get())
      assertEquals(1, fixture.providerLogoutCalls.get())
      // The retained model's completed sign-out is still idempotent when the logout form remounts.
      ui.runOnIdle { visible.value = false }
      ui.runOnIdle { visible.value = true }
      ui.waitForIdle()
      assertEquals(1, fixture.logoutCalls.get())
      assertEquals(1, fixture.providerLogoutCalls.get())
      assertCleared(fixture)
   }
}
