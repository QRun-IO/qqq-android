/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import android.content.Context
import android.content.RestrictionsManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** Android managed-configuration keys; they must match res/xml/app_restrictions.xml and the company profile generator. */
internal const val MANAGED_SERVER_URL = "qqq_server_url"
internal const val MANAGED_ORIENTATION = "qqq_orientation"

/*******************************************************************************
 ** Validated administrator bootstrap values. Absent or rejected keys are null, which leaves unmanaged behaviour unchanged.
 ** Only key names, never values, are recorded for rejected input.
 *******************************************************************************/
internal data class ManagedValues(val serverUrl: String? = null, val orientation: OrientationPolicy? = null, val rejectedKeys: List<String> = emptyList())

/*******************************************************************************
 ** What a managed server URL means for the current installation.
 *******************************************************************************/
internal enum class ManagedServerAction { NONE, PREFILL, OFFER_CHANGE }

/*******************************************************************************
 ** Pure validation and reconciliation rules for managed configuration.
 *******************************************************************************/
internal object ManagedConfiguration {
   private const val MAX_URL = 2048

   /*******************************************************************************
    ** Accept only an HTTPS origin without credentials, path, query or fragment (managed values never allow HTTP, even
    ** in debug builds), and only an exact existing orientation name. Anything else is rejected, never defaulted.
    *******************************************************************************/
   fun parse(serverUrl: String?, orientation: String?): ManagedValues {
      val rejected = mutableListOf<String>()
      val url = serverUrl?.takeIf { it.isNotBlank() }?.let { raw ->
         try {
            require(raw.length <= MAX_URL) { "URL too long" }
            normalizeServerUrl(raw, allowHttp = false)
         } catch (exception: Exception) {
            rejected += MANAGED_SERVER_URL
            null
         }
      }
      val policy = orientation?.takeIf { it.isNotBlank() }?.let { raw ->
         OrientationPolicy.entries.firstOrNull { it.name == raw } ?: run { rejected += MANAGED_ORIENTATION; null }
      }
      return ManagedValues(url, policy, rejected)
   }

   /*******************************************************************************
    ** No saved server: offer the managed URL in the connect form. A different saved server is never switched silently;
    ** the person is offered the existing change-server path (confirm, end session, sign out).
    *******************************************************************************/
   fun serverAction(savedServer: String?, managedServer: String?): ManagedServerAction = when {
      managedServer == null -> ManagedServerAction.NONE
      savedServer == null -> ManagedServerAction.PREFILL
      savedServer == managedServer -> ManagedServerAction.NONE
      else -> ManagedServerAction.OFFER_CHANGE
   }

   /*******************************************************************************
    ** Managed orientation is a bootstrap default applied only when the administrator's value changes. A later local
    ** choice stands until the managed value changes again. Null means nothing to apply.
    *******************************************************************************/
   fun orientationToApply(managed: OrientationPolicy?, lastApplied: String?): OrientationPolicy? =
      managed?.takeIf { it.name != lastApplied }
}

/*******************************************************************************
 ** Read this application's current restrictions; a missing service or no restrictions yields no managed values.
 *******************************************************************************/
internal fun readManagedValues(context: Context): ManagedValues {
   val manager = context.getSystemService(Context.RESTRICTIONS_SERVICE) as? RestrictionsManager ?: return ManagedValues()
   val restrictions = manager.applicationRestrictions
   return ManagedConfiguration.parse(restrictions.getString(MANAGED_SERVER_URL), restrictions.getString(MANAGED_ORIENTATION))
}

/*******************************************************************************
 ** Settings notice shown only when the managed server differs from the connected one. The button runs the caller's
 ** existing confirmed change-server flow; this composable changes nothing itself.
 *******************************************************************************/
@Composable
internal fun ManagedServerNotice(connectedServer: String, managedServer: String?, onChange: () -> Unit) {
   if (ManagedConfiguration.serverAction(connectedServer, managedServer) != ManagedServerAction.OFFER_CHANGE) return
   Text("Your administrator configured a different server: $managedServer", style = MaterialTheme.typography.bodySmall)
   TextButton(onClick = onChange) { Text("Use administrator's server") }
}
