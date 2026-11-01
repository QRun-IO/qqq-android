/*
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

package com.kingsrook.qqq.frontend.android.mobileapp.viewmodel

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.kingsrook.qqq.frontend.android.core.controllers.QQQRepository
import com.kingsrook.qqq.frontend.android.core.model.Environment
import com.kingsrook.qqq.frontend.android.core.model.metadata.QInstance
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.BaseAuthenticationMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.ManageSessionRequest
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.ManageSessionResponse
import com.kingsrook.qqq.frontend.android.core.utils.firstN
import com.kingsrook.qqq.frontend.android.mobileapp.QMobileApplication
import com.kingsrook.qqq.frontend.android.mobileapp.container.QAppContainer
import com.kingsrook.qqq.frontend.android.mobileapp.data.SettingsRepository
import com.kingsrook.qqq.frontend.android.mobileapp.ui.QNavigator
import com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication.OAuth2LoginAttempt
import com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication.OAuth2LoginBinding
import com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication.OAuth2LoginPhase
import com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication.OAuth2LogoutAttempt
import com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication.OAuth2LogoutBinding
import com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication.OAuth2LogoutPhase
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.NativeLogoutData
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.OAuth2AuthenticationMetaData
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


private const val TAG = "QViewModel"

/***************************************************************************
 ** Main view-model for the QQQ Mobile app:
 ** - high-level data, e.g., what environment, its auth meta-data and instance
 ** - user/session level status/data
 ***************************************************************************/
open class QViewModel(
   val qqqRepository: QQQRepository,
   private val dataStore: DataStore<Preferences>
) : ViewModel()
{
   private var settingsRepository: SettingsRepository = SettingsRepository(dataStore)

   ////////////////////////////////////////////////////////////////////////////////////////////////////////////
   // this should not get reset ever - it's added to avoid re-running splash animation on orientation change //
   ////////////////////////////////////////////////////////////////////////////////////////////////////////////
   var didRunSplashAnimation = false

   var initialLoadFromDataStoreLoadState: LoadState<Unit> by mutableStateOf(LoadState.Loading(Unit))
      private set

   var isAuthenticated: Boolean by mutableStateOf(false)
      private set

   var isLogoutRequested: Boolean by mutableStateOf(false)
      private set

   var logoutInProgress: Boolean by mutableStateOf(false)
      private set

   var logoutMessage: String by mutableStateOf("")
      private set

   var sessionUUID: String by mutableStateOf("")
      private set

   private var storedSessionUUIDLoadState: LoadState<String> by mutableStateOf(LoadState.Loading(Unit))
      private set

   var sessionUserFullName: String by mutableStateOf("")

   private var storedSessionUserFullName: LoadState<String> by mutableStateOf(LoadState.Loading(Unit))

   private var storedEnvironmentLoadState: LoadState<Environment> by mutableStateOf(LoadState.Loading(Unit))

   private var defaultEnvironment = QAppContainer.availableEnvironments[0]

   var environment: Environment by mutableStateOf(defaultEnvironment)
      private set

   var authenticationMetaDataLoadState: LoadState<BaseAuthenticationMetaData> by mutableStateOf(LoadState.Loading(Unit))
      private set

   var manageSessionLoadState: LoadState<ManageSessionResponse> by mutableStateOf(LoadState.Loading(Unit))
      private set

   var qInstanceLoadState: LoadState<QInstance> by mutableStateOf(LoadState.Loading(Unit))
      private set

   var topBarStatusText: String? by mutableStateOf("")

   var qNavigator: QNavigator = QNavigator(null, this)
      private set

   private var initedQNavigator = false

   var supportedCustomWidgetTypes: Set<String> = emptySet()
   var activeProcessViewModel: ProcessViewModel? by mutableStateOf(null)

   /** A host may end its own session resources before the ordinary QQQ logout. */
   var beforeLogout: suspend () -> Unit = {}

   /** Hosts refresh this action after Activity recreation; a retained leave dialog must not call an obsolete UI scope. */
   var changeServerAction: (() -> Unit)? = null

   var oauth2LoginPhase: OAuth2LoginPhase by mutableStateOf(OAuth2LoginPhase.READY)
      private set
   var oauth2LoginMessage: String? by mutableStateOf(null)
      private set
   private val oauth2Attempt = OAuth2LoginAttempt()
   private var oauth2Generation = 0L
   private var oauth2Binding: OAuth2LoginBinding? = null
   private var oauth2Metadata: BaseAuthenticationMetaData? = null
   private var oauth2ExchangeJob: Job? = null

   var companyLogoutBinding: OAuth2LogoutBinding? by mutableStateOf(null)
      private set
   var companyLogoutPhase: OAuth2LogoutPhase by mutableStateOf(OAuth2LogoutPhase.IDLE)
      private set
   var companyLogoutGeneration = 0L
      private set
   private val companyLogoutAttempt = OAuth2LogoutAttempt()

   /** Opaque login identity: callbacks and responses belong to their initiating server and generation. */
   class AuthenticationAttempt internal constructor(internal val generation: Long, internal val server: String,
      internal val metadata: BaseAuthenticationMetaData?)
   {
      internal var exchangeStarted = false
   }
   private var authenticationGeneration = 0L
   private var authenticationAttempt: AuthenticationAttempt? = null
   private var authenticationJob: Job? = null
   private var logoutJob: Job? = null
   private var lastLogoutServerConfirmed = false
   private var auth0ProviderLogoutJob: Job? = null
   private val settingsMutex = Mutex()

   fun prepareAuthentication(): AuthenticationAttempt?
   {
      if(isAuthenticated || isLogoutRequested || logoutInProgress) return null
      val metadata = (authenticationMetaDataLoadState as? LoadState.Success)?.value ?: return null
      cancelAuthentication()
      cancelOAuth2Login()
      return AuthenticationAttempt(authenticationGeneration, environment.baseUrl, metadata).also { authenticationAttempt = it }
   }

   fun isCurrentAuthentication(attempt: AuthenticationAttempt): Boolean =
      authenticationAttempt === attempt && attempt.generation == authenticationGeneration &&
         attempt.server == environment.baseUrl && attempt.metadata == (authenticationMetaDataLoadState as? LoadState.Success)?.value &&
         !isAuthenticated && !isLogoutRequested && !logoutInProgress

   fun cancelAuthentication(attempt: AuthenticationAttempt? = null)
   {
      if(attempt != null && authenticationAttempt !== attempt) return
      authenticationGeneration++
      authenticationAttempt = null
      authenticationJob?.cancel()
      authenticationJob = null
   }

   fun completeAuth0Login(attempt: AuthenticationAttempt, accessToken: String, userName: String)
   {
      viewModelScope.launch {
         if(isCurrentAuthentication(attempt)) exchangeAuthentication(attempt, userName) {
            qqqRepository.manageSession(ManageSessionRequest(accessToken = accessToken, uuid = null))
         }
      }
   }

   private fun exchangeAuthentication(attempt: AuthenticationAttempt, userName: String?, exchange: suspend () -> ManageSessionResponse)
   {
      if(!isCurrentAuthentication(attempt) || attempt.exchangeStarted) return
      attempt.exchangeStarted = true
      manageSessionLoadState = LoadState.Loading(Unit)
      authenticationJob = viewModelScope.launch {
         var newSession: String? = null
         var adopted = false
         try
         {
            val response = exchange()
            newSession = response.uuid?.takeIf { it.isNotBlank() }
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if(!isCurrentAuthentication(attempt)) return@launch
            sessionUUID = requireNotNull(newSession)
            qqqRepository.setSessionUUID(sessionUUID)
            if(userName != null) doSetSessionUserFullName(userName)
            manageSessionLoadState = LoadState.Success(response)
            logInSuccessful()
            adopted = true
            authenticationAttempt = null
            loadQInstance()
         }
         catch(exception: kotlinx.coroutines.CancellationException) { throw exception }
         catch(exception: Exception)
         {
            if(isCurrentAuthentication(attempt)) manageSessionLoadState = LoadState.Error("Sign-in failed. Check your credentials and connection.")
         }
         finally
         {
            if(!adopted && newSession != null) kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
               try { kotlinx.coroutines.withTimeout(3000) { qqqRepository.discardNativeSession(attempt.server, newSession!!) } }
               catch(exception: Exception) { /* Never adopt an abandoned credential. */ }
            }
         }
      }
   }

   ////////////////////////////////////////////////////////////////////
   // note - new properties - do you want to add them to clearAll()? //
   ////////////////////////////////////////////////////////////////////

   init
   {
      val generation = authenticationGeneration
      viewModelScope.launch()
      {
         doInitialLoadFromDataStore().join()
         if(generation != authenticationGeneration)
         {
            initialLoadFromDataStoreLoadState = LoadState.Success(Unit)
            return@launch
         }
         loadAuthenticationMetaData().join()
         if(generation != authenticationGeneration)
         {
            initialLoadFromDataStoreLoadState = LoadState.Success(Unit)
            return@launch
         }
         tryLoginFromDataStore().join()
      }
   }

   /***************************************************************************
    ** as part of not re-creating all the things upon orientation change -
    ** own the qNavigator & navController here
    ***************************************************************************/
   fun setNavController(navController: NavHostController?)
   {
      if(!initedQNavigator)
      {
         qNavigator = QNavigator(navController, this)
         initedQNavigator = true
      }
      else
      {
         qNavigator.navController = navController
      }
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun reInitAfterFailure()
   {
      clearAll()
      loadAuthenticationMetaData()
   }


   /***************************************************************************
    **
    ***************************************************************************/
   fun resetApp()
   {
      clearAll()
      loadAuthenticationMetaData()
   }

   /***************************************************************************
    **
    ***************************************************************************/
   private fun clearAll(preserveLogoutScreen: Boolean = false)
   {
      cancelAuthentication()
      cancelOAuth2Login()
      if(!preserveLogoutScreen) cancelCompanyLogout()
      ////////////////////////////////////////////
      // reset all the things to initial values //
      ////////////////////////////////////////////
      // storedSessionDance = LoadState.Loading(Unit) // well, not this one - it's only for our very first load.
      isAuthenticated = false
      if(!preserveLogoutScreen) isLogoutRequested = false
      sessionUUID = ""
      storedSessionUUIDLoadState = LoadState.Loading(Unit)
      sessionUserFullName = ""
      storedSessionUserFullName = LoadState.Loading(Unit)
      storedEnvironmentLoadState = LoadState.Loading(Unit)
      if(!preserveLogoutScreen) authenticationMetaDataLoadState = LoadState.Loading(Unit)
      manageSessionLoadState = LoadState.Loading(Unit)
      qInstanceLoadState = LoadState.Loading(Unit)
      topBarStatusText = ""
      activeProcessViewModel?.reset()
      activeProcessViewModel = null
   }

   /***************************************************************************
    **
    ***************************************************************************/
   private fun loadAuthenticationMetaData(): Job
   {
      val generation = authenticationGeneration
      val server = environment.baseUrl
      return viewModelScope.launch()
      {
         val result: LoadState<BaseAuthenticationMetaData> = try
         {
            LoadState.Success(qqqRepository.getAuthenticationMetaData())
         }
         catch(e: Exception)
         {
            Log.w(TAG, "Error loading Authentication MetaData")
            LoadState.Error(e, "Error loading Authentication MetaData")
         }
         kotlinx.coroutines.currentCoroutineContext().ensureActive()
         if(generation == authenticationGeneration && server == environment.baseUrl) authenticationMetaDataLoadState = result
      }
   }

   /***************************************************************************
    **
    ***************************************************************************/
   open fun manageSession(accessToken: String)
   {
      val attempt = prepareAuthentication() ?: return
      exchangeAuthentication(attempt, sessionUserFullName.takeIf { it.isNotBlank() }) {
         qqqRepository.manageSession(ManageSessionRequest(accessToken = accessToken, uuid = null))
      }
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun logInFailed()
   {
      isAuthenticated = false

      ///////////////////////////////////////////////////
      // make sure we don't have a sessionUUID stored. //
      ///////////////////////////////////////////////////
      storeSessionUUID("")
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun logInSuccessful()
   {
      isAuthenticated = true
      storeSessionUUID(sessionUUID)
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun requestLogout()
   {
      cancelCompanyLogout()
      cancelAuthentication()
      cancelOAuth2Login()
      isLogoutRequested = true
      qNavigator.goHome(cancelActiveProcess = false)
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun requestLogin()
   {
      if(companyLogoutPhase == OAuth2LogoutPhase.PREPARING || companyLogoutPhase == OAuth2LogoutPhase.BROWSER || logoutInProgress) return
      cancelCompanyLogout()
      isLogoutRequested = false
   }

   /***************************************************************************
    **
    ***************************************************************************/
   private fun doInitialLoadFromDataStore(): Job
   {
      return viewModelScope.launch()
      {
         ////////////////////////////////////////
         // load the session UUID from storage //
         ////////////////////////////////////////
         Log.d(TAG, "starting initialLoadFromDataStore;")

         val job1 = loadStoredSessionUUID()
         val job2 = loadStoredSessionUserFullName()
         val job3 = loadStoredEnvironmentOrDefault()

         job1.join()
         job2.join()
         job3.join()

         Log.d(TAG, "past initialLoadFromDataStore;")
      }
   }

   /***************************************************************************
    **
    ***************************************************************************/
   private fun tryLoginFromDataStore(): Job
   {
      val generation = authenticationGeneration
      return viewModelScope.launch()
      {
         when (storedSessionUUIDLoadState)
         {
            is LoadState.Success<String> ->
            {
               Log.d(TAG, "loadStoredSessionUUID was success")
               sessionUUID = (storedSessionUUIDLoadState as LoadState.Success<String>).value

               if(sessionUUID == "")
               {
                  Log.d(TAG, "loadStoredSessionUUID was empty - no more dance")
                  logInFailed()
               }
               else
               {
                  Log.d(TAG, "loadStoredSessionUUID was set - trying to load instance now")
                  qqqRepository.setSessionUUID(sessionUUID)
                  loadQInstance().join()
                  if(generation != authenticationGeneration || isLogoutRequested) return@launch

                  when (qInstanceLoadState)
                  {
                     is LoadState.Success<QInstance> ->
                     {
                        loadStoredSessionUserFullName().join()
                        if(generation != authenticationGeneration || isLogoutRequested) return@launch

                        sessionUserFullName = when (val it = storedSessionUserFullName)
                        {
                           is LoadState.Success<String> -> it.value
                           else -> "Unknown"
                        }

                        Log.d(TAG, "Stored session accepted")
                        logInSuccessful()
                     }

                     else ->
                     {
                        qInstanceLoadState = LoadState.Loading(Unit)
                        Log.d(TAG, "Stored session rejected")
                        logInFailed()
                     }
                  }
               }
            }

            else ->
            {
               ///////////
               // noop. //
               ///////////
               Log.d(TAG, "No stored session available")
            }
         }

         initialLoadFromDataStoreLoadState = LoadState.Success(Unit)
      }
   }

   /***************************************************************************
    **
    ***************************************************************************/
   private fun loadStoredSessionUUID(): Job
   {
      return viewModelScope.launch()
      {
         storedSessionUUIDLoadState = try
         {
            LoadState.Success(settingsRepository.getSessionUUID())
         }
         catch(e: Exception)
         {
            Log.w(TAG, "Error loading stored session UUID")
            LoadState.Error(e, "Error loading stored session UUID")
         }
      }
   }

   /***************************************************************************
    **
    ***************************************************************************/
   private fun loadStoredEnvironmentOrDefault(): Job
   {
      return viewModelScope.launch()
      {
         try
         {
            var env: Environment? = settingsRepository.getEnvironment()

            if(env == null)
            {
               Log.d(TAG, "got null environment from settings repo.  Using default.")
               env = defaultEnvironment
            }
            else
            {
               Log.d(TAG, "Loaded environment from settings.")
            }

            activateEnvironment(env).join()
         }
         catch(e: Exception)
         {
            Log.w(TAG, "Error loading stored environment")
            storedEnvironmentLoadState = LoadState.Error(e, "Error loading stored environment")
         }
      }
   }

   /***************************************************************************
    **
    ***************************************************************************/
   private fun activateEnvironment(environment: Environment): Job
   {
      return viewModelScope.launch()
      {
         if(this@QViewModel.environment.baseUrl != environment.baseUrl)
         {
            authenticationMetaDataLoadState = LoadState.Loading(Unit)
            manageSessionLoadState = LoadState.Loading(Unit)
         }
         this@QViewModel.environment = environment
         qqqRepository.setEnvironment(environment)
         settingsRepository.setEnvironment(environment)
         storedEnvironmentLoadState = LoadState.Success(environment)
      }
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun switchEnvironment(environment: Environment, postSwitchCallback: () -> Unit)
   {
      cancelCompanyLogout()
      cancelAuthentication()
      cancelOAuth2Login()
      qNavigator.goHome(cancelActiveProcess = false)
      viewModelScope.launch()
      {
         logOut().join()
         activateEnvironment(environment).join()
         loadAuthenticationMetaData().join()
         postSwitchCallback.invoke()
      }
   }

   /***************************************************************************
    **
    ***************************************************************************/
   private fun loadStoredSessionUserFullName(): Job
   {
      return viewModelScope.launch()
      {
         storedSessionUserFullName = try
         {
            LoadState.Success(settingsRepository.getSessionUserFullName())
         }
         catch(e: Exception)
         {
            Log.w(TAG, "Error loading stored session user full name")
            LoadState.Error(e, "Error loading stored session user full name")
         }
      }
   }

   /***************************************************************************
    **
    ***************************************************************************/
   private fun storeSessionUUID(value: String)
   {
      val generation = authenticationGeneration
      viewModelScope.launch()
      {
         settingsMutex.withLock {
            if(generation == authenticationGeneration) settingsRepository.setSessionUUID(value)
         }
      }
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun doSetSessionUserFullName(value: String)
   {
      sessionUserFullName = value
      val generation = authenticationGeneration
      viewModelScope.launch()
      {
         settingsMutex.withLock {
            if(generation == authenticationGeneration) settingsRepository.setSessionUserFullName(value)
         }
      }
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun logOut(): Job = performLogout()

   /** Optional provider material is captured only by the initiating coroutine, never by ViewModel state. */
   private fun performLogout(providerLogout: (suspend () -> Unit)? = null): Job
   {
      logoutJob?.takeIf { it.isActive || (!isAuthenticated && sessionUUID.isBlank() &&
         authenticationAttempt == null && oauth2LoginPhase == OAuth2LoginPhase.READY) }?.let { return it }
      cancelAuthentication()
      cancelOAuth2Login()
      isAuthenticated = false
      logoutInProgress = true
      lastLogoutServerConfirmed = false
      val logoutGeneration = authenticationGeneration
      val logoutServer = environment.baseUrl
      return viewModelScope.launch()
      {
         val capturedIsLogoutRequested = isLogoutRequested
         val capturedAuthLoadState = authenticationMetaDataLoadState
         activeProcessViewModel?.reset()
         logoutMessage = "Signing out…"
         var serverConfirmed = false
         try
         {
            settingsMutex.withLock { settingsRepository.setSessionUUID(""); settingsRepository.setSessionUserFullName("") }
            try { kotlinx.coroutines.withTimeoutOrNull(3000) { beforeLogout() } }
            catch(exception: kotlinx.coroutines.CancellationException) { throw exception }
            catch(exception: Exception) { /* Core logout and local clearing still run. */ }
            serverConfirmed = kotlinx.coroutines.withTimeoutOrNull(3000) {
               if(providerLogout == null) qqqRepository.logout() else providerLogout()
               true
            } ?: false
         }
         catch(exception: kotlinx.coroutines.CancellationException) { throw exception }
         catch(exception: Exception) { /* Local sign-out still completes when the network is unavailable. */ }
         finally
         {
            val current = logoutGeneration == authenticationGeneration && logoutServer == environment.baseUrl
            if(current)
            {
               clearAll(preserveLogoutScreen = true)
               qqqRepository.setSessionUUID("")
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
               settingsMutex.withLock { settingsRepository.setSessionUUID(""); settingsRepository.setSessionUserFullName("") }
            }
            if(current)
            {
               isLogoutRequested = capturedIsLogoutRequested
               authenticationMetaDataLoadState = capturedAuthLoadState
            }
            logoutInProgress = false
            lastLogoutServerConfirmed = current && serverConfirmed
            logoutMessage = if(serverConfirmed) "Signed out."
               else "Signed out on this device. Server sign-out could not be confirmed."
            if(companyLogoutPhase == OAuth2LogoutPhase.FINISHED)
               logoutMessage += " Company sign-out did not complete. Your organization may still be signed in in the browser."
         }
      }.also { logoutJob = it }
   }

   /** Invoked only by an explicit host opt-in; ordinary requestLogout remains local-only. */
   fun requestCompanyLogout(binding: OAuth2LogoutBinding)
   {
      if(!isAuthenticated || logoutInProgress || binding.login.server != environment.baseUrl) return
      val metadata = (authenticationMetaDataLoadState as? LoadState.Success)?.value as? OAuth2AuthenticationMetaData ?: return
      val logout = metadata.values.nativeSessionLogout ?: return
      val login = metadata.values.nativeSessionLogin ?: return
      if(binding.login != OAuth2LoginBinding(environment.baseUrl, metadata.name, login.issuer, login.discoveryUrl,
            metadata.values.clientId, metadata.values.scopes, binding.login.redirectUri, login.prompt, login.nonceValidation) ||
         login.flow != OAuth2AuthenticationMetaData.SESSION_FLOW || login.codeChallengeMethod != "S256" || login.prompt != "login" ||
         login.nonceValidation != "REQUIRED" ||
         binding.login.redirectUri !in login.redirectUris || binding.postLogoutRedirectUri != binding.login.redirectUri ||
         logout.flow != OAuth2AuthenticationMetaData.LOGOUT_FLOW || binding.postLogoutRedirectUri !in logout.postLogoutRedirectUris) return
      requestLogout()
      companyLogoutBinding = binding
      companyLogoutPhase = OAuth2LogoutPhase.PREPARING
   }

   fun isCurrentCompanyLogout(generation: Long, binding: OAuth2LogoutBinding): Boolean =
      generation == companyLogoutGeneration && companyLogoutBinding == binding &&
         binding.login.server == environment.baseUrl && isLogoutRequested

   /** Local invalidation finishes before the browser sees the optional transient hint. */
   suspend fun logOutWithProvider(generation: Long, binding: OAuth2LogoutBinding): NativeLogoutData?
   {
      if(!isCurrentCompanyLogout(generation, binding) || companyLogoutPhase != OAuth2LogoutPhase.PREPARING) return null
      var material: NativeLogoutData? = null
      try
      {
         performLogout { material = qqqRepository.logoutWithProvider(binding.postLogoutRedirectUri) }.join()
         return material.takeIf { isCurrentCompanyLogout(generation, binding) && companyLogoutPhase == OAuth2LogoutPhase.PREPARING &&
            lastLogoutServerConfirmed && !isAuthenticated && !logoutInProgress }
      }
      finally { material = null }
   }

   fun beginCompanyLogoutBrowser(generation: Long, binding: OAuth2LogoutBinding, endpoint: String, state: String): Boolean
   {
      if(!isCurrentCompanyLogout(generation, binding) || companyLogoutPhase != OAuth2LogoutPhase.PREPARING ||
         isAuthenticated || logoutInProgress) return false
      companyLogoutAttempt.begin(binding, endpoint, state)
      companyLogoutPhase = OAuth2LogoutPhase.BROWSER
      return true
   }

   /** Dedicated end-session responses never establish QQQ identity or restore cleared private screens. */
   fun completeCompanyLogout(generation: Long, binding: OAuth2LogoutBinding, state: String?, requestState: String?,
      requestEndpoint: String?, requestRedirect: String?)
   {
      if(!isCurrentCompanyLogout(generation, binding) || companyLogoutPhase != OAuth2LogoutPhase.BROWSER) return
      val verified = companyLogoutAttempt.consume(binding, state, requestState, requestEndpoint, requestRedirect)
      setCompanyLogoutResult(generation, binding, verified)
   }

   /** Cancellation/unavailable paths cannot claim successful provider logout. */
   fun finishCompanyLogout(generation: Long, binding: OAuth2LogoutBinding) = setCompanyLogoutResult(generation, binding, false)

   private fun setCompanyLogoutResult(generation: Long, binding: OAuth2LogoutBinding, completed: Boolean)
   {
      if(!isCurrentCompanyLogout(generation, binding)) return
      if(companyLogoutPhase == OAuth2LogoutPhase.FINISHED) return
      companyLogoutAttempt.cancel()
      companyLogoutPhase = OAuth2LogoutPhase.FINISHED
      logoutMessage += if(completed) " Company sign-out completed."
         else " Company sign-out did not complete. Your organization may still be signed in in the browser."
   }

   private fun cancelCompanyLogout()
   {
      companyLogoutGeneration++
      companyLogoutAttempt.cancel()
      companyLogoutBinding = null
      companyLogoutPhase = OAuth2LogoutPhase.IDLE
   }

   /** Activity recreation may render logout again; each logical sign-out opens the provider only once. */
   fun claimAuth0ProviderLogout(): Boolean
   {
      val current = logoutJob ?: return false
      if(auth0ProviderLogoutJob === current) return false
      auth0ProviderLogoutJob = current
      return true
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun loadQInstance(): Job
   {
      val generation = authenticationGeneration
      val server = environment.baseUrl
      return viewModelScope.launch()
      {
         val result: LoadState<QInstance> = try
         {
            // todo - get frontend name & version from ... somewhere!
            val value = qqqRepository.getMetaData("qqq-android", "1.0", applicationName, applicationVersion)
            LoadState.Success(value)
         }
         catch(e: Exception)
         {
            Log.w(TAG, "Error loading QInstance")
            LoadState.Error(e,"Error loading QInstance")
         }
         kotlinx.coroutines.currentCoroutineContext().ensureActive()
         if(generation == authenticationGeneration && server == environment.baseUrl) qInstanceLoadState = result
      }
   }

   fun prepareOAuth2Login(binding: OAuth2LoginBinding): Long?
   {
      if(oauth2LoginPhase != OAuth2LoginPhase.READY || isAuthenticated || isLogoutRequested || logoutInProgress ||
         binding.server != environment.baseUrl || binding.nonceValidation != "REQUIRED") return null
      cancelAuthentication()
      oauth2Generation++
      oauth2Binding = binding
      oauth2Metadata = (authenticationMetaDataLoadState as? LoadState.Success)?.value
      oauth2LoginMessage = null
      oauth2LoginPhase = OAuth2LoginPhase.PREPARING
      return oauth2Generation
   }

   fun isCurrentOAuth2Login(generation: Long, binding: OAuth2LoginBinding): Boolean =
      generation == oauth2Generation && binding == oauth2Binding && binding.server == environment.baseUrl &&
         oauth2Metadata == (authenticationMetaDataLoadState as? LoadState.Success)?.value && !isAuthenticated && !isLogoutRequested && !logoutInProgress

   fun beginOAuth2Browser(generation: Long, binding: OAuth2LoginBinding, state: String, nonce: String, verifier: String): Boolean
   {
      if(!isCurrentOAuth2Login(generation, binding) || oauth2LoginPhase != OAuth2LoginPhase.PREPARING) return false
      oauth2Attempt.begin(binding, state, nonce, verifier)
      oauth2LoginPhase = OAuth2LoginPhase.BROWSER
      return true
   }

   fun cancelOAuth2Login(message: String? = null)
   {
      oauth2Generation++
      oauth2Attempt.cancel()
      oauth2Binding = null
      oauth2Metadata = null
      oauth2LoginPhase = OAuth2LoginPhase.READY
      oauth2LoginMessage = message
      oauth2ExchangeJob?.cancel()
      oauth2ExchangeJob = null
   }

   fun completeOAuth2Login(binding: OAuth2LoginBinding, state: String?, requestState: String?, requestNonce: String?,
      requestClientId: String, requestRedirect: String, requestPrompt: String?, code: String?)
   {
      val generation = oauth2Generation
      if(oauth2LoginPhase != OAuth2LoginPhase.BROWSER || !isCurrentOAuth2Login(generation, binding)) return
      val exchange = oauth2Attempt.consume(binding, state, requestState, requestNonce, requestClientId, requestRedirect, requestPrompt, code)
      if(exchange == null)
      {
         cancelOAuth2Login("Sign-in could not be verified. Please start again.")
         return
      }
      oauth2LoginPhase = OAuth2LoginPhase.EXCHANGING
      oauth2ExchangeJob = viewModelScope.launch {
         var newSession: String? = null
         var adopted = false
         try
         {
            val response = qqqRepository.exchangeNativeSession(ManageSessionRequest(code = exchange.code,
               codeVerifier = exchange.verifier, redirectUri = exchange.redirectUri,
               authenticationFlow = OAuth2AuthenticationMetaData.SESSION_FLOW, expectedNonce = exchange.expectedNonce))
            newSession = response.uuid?.takeIf { it.isNotBlank() }
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if(!isCurrentOAuth2Login(generation, binding)) return@launch
            sessionUUID = requireNotNull(newSession)
            qqqRepository.setSessionUUID(sessionUUID)
            manageSessionLoadState = LoadState.Success(response)
            logInSuccessful()
            adopted = true
            oauth2LoginPhase = OAuth2LoginPhase.READY
            oauth2Binding = null
            oauth2Metadata = null
            loadQInstance()
         }
         catch(exception: kotlinx.coroutines.CancellationException) { throw exception }
         catch(exception: Exception)
         {
            if(generation == oauth2Generation)
            {
               oauth2LoginPhase = OAuth2LoginPhase.READY
               oauth2Binding = null
               oauth2Metadata = null
               oauth2LoginMessage = "Sign-in failed. Check your connection or contact your administrator."
            }
         }
         finally
         {
            if(!adopted && newSession != null) kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
               try { kotlinx.coroutines.withTimeout(3000) { qqqRepository.discardNativeSession(binding.server, newSession!!) } }
               catch(exception: Exception) { /* Never activate an abandoned session, even when revocation cannot be confirmed. */ }
            }
         }
      }
   }

   /** Password remains in this request only; the returned session drives the existing host. */
   fun loginWithPassword(username: String, password: String)
   {
      val attempt = prepareAuthentication() ?: return
      exchangeAuthentication(attempt, username) { qqqRepository.loginWithPassword(username, password) }
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun getURI(path: String): String
   {
      val uri = qqqRepository.getURI(path)
      return uri
   }

   /***************************************************************************
    **
    ***************************************************************************/
   companion object
   {
      var applicationName: String = "anonymousApp"
      var applicationVersion: String = "0"
      var applicationBuildTimestamp: String? = null

      lateinit var dataStore: DataStore<Preferences>

      val factory: ViewModelProvider.Factory = viewModelFactory()
      {
         initializer()
         {
            val application = (this[APPLICATION_KEY] as QMobileApplication)
            QViewModel(qqqRepository = application.container.qqqRepository, dataStore = dataStore)
         }
      }
   }
}
