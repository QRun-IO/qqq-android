/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo

internal const val HARDWARE_SCAN_SOURCE = "zebra-emdk"

/*******************************************************************************
 ** Never load optional vendor classes on other devices or trust an ordinary installed lookalike.
 *******************************************************************************/
internal fun createScannerDriver(context: Context): ScannerDriver? {
   val application = context.applicationContext
   val packages = application.packageManager
   return try {
      if (packages.systemSharedLibraryNames?.contains("com.symbol.emdk") != true) return null
      val service = packages.getServiceInfo(
         ComponentName(
            "com.symbol.emdk.emdkservice",
            "com.symbol.emdk.emdkservice.EMDKService"
         ), 0
      )
      val systemFlags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
      if (!service.enabled || !service.applicationInfo.enabled || service.applicationInfo.flags and systemFlags == 0) return null
      Class.forName("io.qrun.mobile.scanner.ZebraScannerDriver").getDeclaredConstructor(Context::class.java)
         .newInstance(application) as ScannerDriver
   } catch (exception: Exception) {
      null
   } catch (error: LinkageError) {
      null
   }
}
