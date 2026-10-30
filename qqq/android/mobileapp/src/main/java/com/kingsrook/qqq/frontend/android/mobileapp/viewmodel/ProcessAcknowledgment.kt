/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.mobileapp.viewmodel

/** An extension action explicitly opts into acknowledged retries; ordinary form fields do not. */
data class ProcessAcknowledgment(val field: String, val expectedValue: Any)
{
   fun matches(responseValues: Map<String, Any?>): Boolean = responseValues[field] == expectedValue
}
