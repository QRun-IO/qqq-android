/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2004-2026. Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * https://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.kingsrook.qqq.sampleandroidmobileapp

import android.content.pm.ApplicationInfo
import android.security.NetworkSecurityPolicy
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kingsrook.qqq.frontend.android.mobileapp.data.SettingsRepository
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SampleHostSecurityTests
{
   @Test
   fun sessionStorageIsExcludedFromBackup() = runBlocking()
   {
      val context = InstrumentationRegistry.getInstrumentation().targetContext
      assertFalse(context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
      assertEquals(BuildConfig.DEBUG, NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted)
      val settings = SettingsRepository(context.dataStore)
      try
      {
         settings.setSessionUUID("synthetic-backup-exclusion-test")
         assertEquals("synthetic-backup-exclusion-test", settings.getSessionUUID())
         assertTrue(File(context.noBackupFilesDir, "settings.preferences_pb").isFile)
      }
      finally
      {
         settings.setSessionUUID("")
      }
   }
}
