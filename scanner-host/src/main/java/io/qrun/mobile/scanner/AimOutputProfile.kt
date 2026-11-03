/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

/*******************************************************************************
 ** What a configuration read-back proves after the AIM output profile was requested.
 *******************************************************************************/
internal enum class AimReadback { CONFIRMED, UNCHANGED, INDETERMINATE }

/*******************************************************************************
 ** Classify the value the scanner reports (null when it could not be read) against the value it held before the
 ** request and the AIM value requested. AIM wins even when the original was already AIM; only exactly the original
 ** counts as unchanged; an unreadable or any other value is indeterminate and must be restored or failed, never
 ** trusted. Generic so the rule is independent of the vendor SDK's enum.
 *******************************************************************************/
internal fun <T : Any> classifyAimReadback(original: T, aim: T, effective: T?): AimReadback = when {
   effective == null -> AimReadback.INDETERMINATE
   effective == aim -> AimReadback.CONFIRMED
   effective == original -> AimReadback.UNCHANGED
   else -> AimReadback.INDETERMINATE
}

/*******************************************************************************
 ** Strict reading of the application-owned EMDK "AIM code identifier" output profile. Only a driver that
 ** applied ScannerConfig.scanParams.codeIdType=AIM and read the same value back may call [split]; without
 ** that confirmation the SDK text is user payload and must never be stripped or interpreted.
 *******************************************************************************/
internal object AimOutputProfile {
   private val AIM = Regex("\\][A-Za-z][0-9A-Za-z]")

   // AIM symbology character each known EMDK label must report; the modifier character is not checked here.
   private val FAMILY = mapOf(
      "CODE128" to 'C', "CODE39" to 'A', "CODE93" to 'G', "I2OF5" to 'I', "QRCODE" to 'Q', "DATAMATRIX" to 'd',
      "PDF417" to 'L', "AZTEC" to 'z', "EAN13" to 'E', "EAN8" to 'E', "UPCA" to 'E', "UPCE0" to 'E', "UPCE1" to 'E'
   )

   /*******************************************************************************
    ** The configured identifier and the user payload that followed it, taken exactly as the SDK delivered them.
    *******************************************************************************/
   internal data class Split(val aimIdentifier: String, val payload: String)

   /*******************************************************************************
    ** Remove exactly the first three characters when they form an AIM identifier consistent with the label.
    ** A missing, malformed or label-inconsistent prefix returns null: the scan is not trusted, never guessed.
    ** An empty payload is refused, and a payload that itself begins with an identifier is left intact.
    *******************************************************************************/
   fun split(text: String, label: String): Split? {
      if (text.length < 4 || !AIM.matches(text.substring(0, 3))) return null
      val family = FAMILY[label.removePrefix("LABEL-TYPE-")]
      if (family != null && text[1] != family) return null
      return Split(text.substring(0, 3), text.substring(3))
   }
}
