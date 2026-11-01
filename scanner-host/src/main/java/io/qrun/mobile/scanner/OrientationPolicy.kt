/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/*******************************************************************************
 ** Device-local display policy; never changes Android's global rotation setting.
 *******************************************************************************/
enum class OrientationPolicy(val label: String, val requested: Int) {
   SYSTEM("System default", ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED),
   PORTRAIT("Portrait", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
   LANDSCAPE("Landscape", ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE),
   REVERSE_PORTRAIT("Reverse portrait", ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT),
   REVERSE_LANDSCAPE("Reverse landscape", ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);

   /*******************************************************************************
    ** Share preference parsing between startup restoration and the operator's display controls.
    *******************************************************************************/
   companion object {
      /*******************************************************************************
       ** Fall back to portrait for missing or invalid preferences while preserving every explicit supported choice.
       *******************************************************************************/
      fun parse(value: String?) = entries.firstOrNull { it.name == value } ?: PORTRAIT

      /*******************************************************************************
       ** Read only this application's local display preference, leaving Android's global rotation setting untouched.
       *******************************************************************************/
      fun read(context: Context) = parse(context.getSharedPreferences("host", Context.MODE_PRIVATE).getString("orientation", null))
   }

   /*******************************************************************************
    ** Persist the choice before changing orientation because Android may immediately recreate the activity.
    *******************************************************************************/
   fun select(activity: Activity) {
      // Persist before the platform potentially recreates the Activity. Its ViewModels survive rotation.
      activity.getSharedPreferences("host", Context.MODE_PRIVATE).edit().putString("orientation", name).apply()
      apply(activity)
   }

   /*******************************************************************************
    ** Avoid requesting redundant orientation changes that could recreate the host unnecessarily.
    *******************************************************************************/
   fun apply(activity: Activity) {
      if (activity.requestedOrientation != requested) activity.requestedOrientation = requested
   }
}

/*******************************************************************************
 ** Offer device-local orientation choices through the same persisted policy used at activity startup.
 *******************************************************************************/
@Composable
fun OrientationSetting(activity: Activity) {
   var selection by remember(activity) { mutableStateOf(OrientationPolicy.read(activity)) }
   var expanded by remember { mutableStateOf(false) }
   Box()
   {
      TextButton(onClick = { expanded = true }) { Text("Orientation: ${selection.label}") }
      DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false })
      {
         OrientationPolicy.entries.forEach { policy ->
            DropdownMenuItem(text = { Text(policy.label) }, onClick = {
               expanded = false
               selection = policy
               policy.select(activity)
            })
         }
      }
   }
}
