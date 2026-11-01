/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

/** The reporting preference and Android permission are independent controls. */
internal object LocationPolicy
{
   fun enabled(saved: Boolean?) = saved ?: true

   fun shouldRequestPermission(active: Boolean, enabled: Boolean, permitted: Boolean, previouslyRequested: Boolean) =
      active && enabled && !permitted && !previouslyRequested

   fun status(enabled: Boolean, permitted: Boolean, providersEnabled: Boolean, active: Boolean, report: String): String = when
   {
      !enabled -> "Location reporting is off"
      !permitted -> "Location reporting is on; Android location permission is not granted"
      !providersEnabled -> "Location reporting is on; device location providers are off"
      !active -> "Location reporting is on; waiting for an approved signed-in foreground session"
      report == "AVAILABLE" -> "Location reporting is on; foreground location is available"
      else -> "Location reporting is on; waiting for a foreground location fix"
   }
}
