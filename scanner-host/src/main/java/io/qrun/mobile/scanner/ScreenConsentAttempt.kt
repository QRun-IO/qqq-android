/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

/** Single-use, memory-only binding for the Android result and subsequent service connection. */
internal class ScreenConsentAttempt
{
   private var generation: String? = null
   private var pending = false

   fun begin(): String { val ticket = java.util.UUID.randomUUID().toString(); generation = ticket; pending = true; return ticket }
   fun invalidate() { generation = null; pending = false }
   fun current(ticket: String) = ticket == generation
   fun consume(ticket: String, granted: Boolean, authenticatedAndApproved: Boolean): Boolean
   {
      if(!current(ticket) || !pending) return false
      pending = false
      return granted && authenticatedAndApproved
   }
}
