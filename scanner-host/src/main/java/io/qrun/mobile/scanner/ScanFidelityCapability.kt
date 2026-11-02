/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import com.kingsrook.qqq.frontend.android.core.controllers.UiApiVersion

/*******************************************************************************
 ** The optional aimIdentifier/rawDataBase64 inputs exist from mobile-scanners 1.5.0; older servers
 ** must receive exactly the legacy keys. Derived only from already-verified discovery data.
 *******************************************************************************/
internal object ScanFidelityCapability {
   private val VERSION = Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)")

   /*******************************************************************************
    ** Require the single registered mobile-scanners extension at its fixed path with version >=1.5.0,<2.
    *******************************************************************************/
   fun supports(version: UiApiVersion?): Boolean {
      val extension = version?.extensions?.singleOrNull { it.name == "mobile-scanners" } ?: return false
      if (extension.pathPrefix != "/qbit/mobile-scanners") return false
      val parts = VERSION.matchEntire(extension.version)?.groupValues ?: return false
      return parts[1] == "1" && (parts[2].toIntOrNull() ?: return false) >= 5 && parts[3].toIntOrNull() != null
   }

   /*******************************************************************************
    ** Discovery data from another server never grants capability to this server's widgets.
    *******************************************************************************/
   fun available(server: String, discoveredServer: String, version: UiApiVersion?): Boolean =
      server == discoveredServer && supports(version)
}
