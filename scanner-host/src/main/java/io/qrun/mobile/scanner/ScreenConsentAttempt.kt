/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

/*******************************************************************************
 ** Single-use, memory-only binding for the Android result and subsequent service connection.
 *******************************************************************************/
internal class ScreenConsentAttempt {
   private var generation: String? = null
   private var pending = false

   /*******************************************************************************
    ** Issue a fresh memory-only ticket so a result from an earlier consent attempt cannot activate sharing.
    *******************************************************************************/
   fun begin(): String {
      val ticket = java.util.UUID.randomUUID().toString(); generation = ticket; pending = true; return ticket
   }

   /*******************************************************************************
    ** Revoke pending consent before host shutdown or a new sharing attempt.
    *******************************************************************************/
   fun invalidate() {
      generation = null; pending = false
   }

   /*******************************************************************************
    ** Check ticket identity independently of consumption for subsequent asynchronous service callbacks.
    *******************************************************************************/
   fun current(ticket: String) = ticket == generation

   /*******************************************************************************
    ** Accept a consent result at most once and only while its host remains authenticated and approved.
    *******************************************************************************/
   fun consume(ticket: String, granted: Boolean, authenticatedAndApproved: Boolean): Boolean {
      if (!current(ticket) || !pending) return false
      pending = false
      return granted && authenticatedAndApproved
   }
}
