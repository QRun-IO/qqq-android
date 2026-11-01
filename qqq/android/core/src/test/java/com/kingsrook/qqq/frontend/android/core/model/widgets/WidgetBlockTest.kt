/*
 * SPDX-License-Identifier: Apache-2.0
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2004-2024.  Kingsrook, LLC
 * 651 N Broad St Ste 205 # 6917 | Middletown DE 19709 | United States
 * contact@kingsrook.com
 * https://github.com/Kingsrook/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.kingsrook.qqq.frontend.android.core.model.widgets

import org.junit.Test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull

/*******************************************************************************
 **
 *******************************************************************************/
class WidgetBlockTest {
   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun testFromMap() {
      val widgetBlock = WidgetBlock.fromMap(
         mapOf(
            "blockType" to "TEXT",
            "styles" to mapOf(
               "foo" to "bar"
            ),
            "values" to mapOf(
               "text" to "this is my text"
            ),
         )
      )

      assertNotNull(widgetBlock)
      if (widgetBlock != null) {
         assertEquals("", widgetBlock.blockId)
         assertEquals("TEXT", widgetBlock.blockType)
         assertNull(widgetBlock.conditional)
         assertNotNull(widgetBlock.styles)
         assertEquals("bar", widgetBlock.styles["foo"])
         assertEquals("this is my text", widgetBlock.values["text"])
         assertEquals(emptyList<WidgetBlock>(), widgetBlock.subBlocks)
      }
   }

   /*******************************************************************************
    ** Widget edits must not mutate immutable or shared decoded server metadata.
    *******************************************************************************/
   @Test
   fun inputMapsBecomeOwnedMutableWidgetState() {
      val styles = mapOf("foo" to "bar")
      val values = mapOf("text" to "original")
      val block = requireNotNull(WidgetBlock.fromMap(mapOf("styles" to styles, "values" to values)))
      block.styles["foo"] = "changed"
      block.values["text"] = "edited"
      assertEquals("bar", styles["foo"])
      assertEquals("original", values["text"])
      assertEquals("changed", block.styles["foo"])
      assertEquals("edited", block.values["text"])
   }


   /*******************************************************************************
    ** JSON property names are strings; malformed legacy maps must fail explicitly.
    *******************************************************************************/
   @Test(expected = IllegalArgumentException::class)
   fun nonStringPropertyNamesAreRejected() {
      WidgetBlock.fromMap(mapOf("values" to mapOf(42 to "invalid")))
   }
}
