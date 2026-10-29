/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.core.model

import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.BaseAuthenticationMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.QInstance
import com.kingsrook.qqq.frontend.android.core.model.metadata.QProcessMetaData
import com.kingsrook.qqq.frontend.android.core.model.widgets.WidgetBlock
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.TableBasedAuthenticationMetaData
import com.kingsrook.qqq.frontend.android.core.model.processes.ProcessStepResult
import com.kingsrook.qqq.frontend.android.core.model.processes.QJobComplete
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures cover wire differences observed against the current QQQ demo server. */
class MobileProtocolTest
{
   private val json = Json { ignoreUnknownKeys = true }

   @Test
   fun acceptsTableBasedAuthenticationMetadata()
   {
      val metadata = json.decodeFromString<BaseAuthenticationMetaData>("""{"name":"users","type":"TABLE_BASED","values":{}}""")
      assertTrue(metadata is TableBasedAuthenticationMetaData)
   }

   @Test
   fun terminalProcessCompletionMayOmitNextStep()
   {
      val result = json.decodeFromString<ProcessStepResult>("""{"type":"COMPLETE","processUUID":"test-process","values":{}}""")
      assertEquals("", (result as QJobComplete).nextStep)
   }

   @Test
   fun rendersContractCapturedFromTheLiveDemo()
   {
      val metadata = json.decodeFromString<QInstance>(fixture("metadata"))
      assertTrue(metadata.apps!!.containsKey("mobileScanApp"))
      val process = json.decodeFromString<QProcessMetaData>(fixture("process"))
      assertEquals("scanner", process.frontendSteps!!.first().format)
      val init = json.decodeFromString<ProcessStepResult>(fixture("init")) as QJobComplete
      assertEquals("scan", init.nextStep)
      val composite = WidgetBlock.fromMap(init.values["mobileScanner"])!!
      assertEquals("SCANNER", composite.subBlocks.single().blockType)
      assertEquals("Scan a barcode", composite.subBlocks.single().values["title"])
   }

   /** These sanitized fixtures contain demo metadata only, never sessions or device tokens. */
   private fun fixture(name: String): String =
      requireNotNull(javaClass.getResourceAsStream("/mobile-contract/$name.json")).bufferedReader().use { it.readText() }
}
