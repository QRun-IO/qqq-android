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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


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
   var activeProcessViewModel: ProcessViewModel? = null

   ////////////////////////////////////////////////////////////////////
   // note - new properties - do you want to add them to clearAll()? //
   ////////////////////////////////////////////////////////////////////

   init
   {
      viewModelScope.launch()
      {
         doInitialLoadFromDataStore().join()
         loadAuthenticationMetaData().join()
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
   private fun clearAll()
   {
      ////////////////////////////////////////////
      // reset all the things to initial values //
      ////////////////////////////////////////////
      // storedSessionDance = LoadState.Loading(Unit) // well, not this one - it's only for our very first load.
      isAuthenticated = false
      isLogoutRequested = false
      sessionUUID = ""
      storedSessionUUIDLoadState = LoadState.Loading(Unit)
      sessionUserFullName = ""
      storedSessionUserFullName = LoadState.Loading(Unit)
      storedEnvironmentLoadState = LoadState.Loading(Unit)
      authenticationMetaDataLoadState = LoadState.Loading(Unit)
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
      return viewModelScope.launch()
      {
         authenticationMetaDataLoadState = try
         {
            LoadState.Success(qqqRepository.getAuthenticationMetaData())
         }
         catch(e: Exception)
         {
            Log.w(TAG, "Error loading Authentication MetaData")
            LoadState.Error(e, "Error loading Authentication MetaData")
         }
      }
   }

   /***************************************************************************
    **
    ***************************************************************************/
   open fun manageSession(accessToken: String)
   {
      val outerThis = this
      viewModelScope.launch()
      {
         try
         {
            val manageSessionLoadState = LoadState.Success(qqqRepository.manageSession(ManageSessionRequest(accessToken = accessToken, uuid = null)))
            sessionUUID = requireNotNull(manageSessionLoadState.value.uuid)
            qqqRepository.setSessionUUID(sessionUUID)
            loadQInstance()
            logInSuccessful()

            outerThis.manageSessionLoadState = manageSessionLoadState
         }
         catch(e: Exception)
         {
            Log.w(TAG, "Error managing session")
            outerThis.manageSessionLoadState = LoadState.Error(e, "Error managing session")
         }
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
      isLogoutRequested = true
      qNavigator.goHome(cancelActiveProcess = false)
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun requestLogin()
   {
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

                  when (qInstanceLoadState)
                  {
                     is LoadState.Success<QInstance> ->
                     {
                        loadStoredSessionUserFullName().join()

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
      viewModelScope.launch()
      {
         settingsRepository.setSessionUUID(value)
      }
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun doSetSessionUserFullName(value: String)
   {
      sessionUserFullName = value
      viewModelScope.launch()
      {
         settingsRepository.setSessionUserFullName(value)
      }
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun logOut(): Job
   {
      return viewModelScope.launch()
      {
         val capturedIsLogoutRequested = isLogoutRequested
         val capturedAuthLoadState = authenticationMetaDataLoadState
         activeProcessViewModel?.reset()
         logoutInProgress = true
         logoutMessage = "Signing out…"
         var serverConfirmed = false
         try
         {
            qqqRepository.logout()
            serverConfirmed = true
         }
         catch(exception: kotlinx.coroutines.CancellationException) { throw exception }
         catch(exception: Exception) { /* Local sign-out still completes when the network is unavailable. */ }
         finally
         {
            clearAll()
            qqqRepository.setSessionUUID("")
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { settingsRepository.setSessionUUID("") }
            isLogoutRequested = capturedIsLogoutRequested
            authenticationMetaDataLoadState = capturedAuthLoadState
            logoutInProgress = false
            logoutMessage = if(serverConfirmed) "Signed out."
               else "Signed out on this device. Server sign-out could not be confirmed."
         }
      }
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun loadQInstance(): Job
   {
      return viewModelScope.launch()
      {
         qInstanceLoadState = try
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
      }
   }

   /** Password remains in this request only; the returned session drives the existing host. */
   fun loginWithPassword(username: String, password: String)
   {
      manageSessionLoadState = LoadState.Loading(Unit)
      viewModelScope.launch()
      {
         try
         {
            val response = qqqRepository.loginWithPassword(username, password)
            sessionUUID = requireNotNull(response.uuid)
            qqqRepository.setSessionUUID(sessionUUID)
            doSetSessionUserFullName(username)
            manageSessionLoadState = LoadState.Success(response)
            logInSuccessful()
            loadQInstance()
         }
         catch(e: kotlinx.coroutines.CancellationException) { throw e }
         catch(e: Exception)
         {
            manageSessionLoadState = LoadState.Error("Sign-in failed. Check your credentials and connection.")
         }
      }
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
