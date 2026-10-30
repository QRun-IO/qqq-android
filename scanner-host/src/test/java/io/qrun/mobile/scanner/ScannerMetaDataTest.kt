package io.qrun.mobile.scanner

import org.junit.Assert.*
import org.junit.Test

class ScannerMetaDataTest
{
   @Test fun serverDefinesLabelsAndLengthWithContractDefaults()
   {
      assertEquals(ScannerMetaData(), ScannerMetaData.parse(emptyMap()))
      assertEquals(ScannerMetaData("Item barcode", "Save item", 30), ScannerMetaData.parse(mapOf("manualLabel" to "Item barcode", "submitLabel" to "Save item", "maxLength" to 30L, "future" to true)))
      for(value in listOf(-1, 0, 4097, "10")) assertEquals(4096, ScannerMetaData.parse(mapOf("maxLength" to value)).maxLength)
   }
}
