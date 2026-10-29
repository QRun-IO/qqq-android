/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.core.model.metadata.authentication

import kotlinx.serialization.Serializable

/** Credentials are exchanged for a QQQ session; passwords are never persisted. */
@Serializable
data class TableBasedAuthenticationMetaData(override val name: String = "tableBased") : BaseAuthenticationMetaData
{
   override val type: String get() = TYPE

   companion object
   {
      const val TYPE = "TABLE_BASED"
   }
}
