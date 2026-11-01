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

package com.kingsrook.qqq.sampleandroidmobileapp.previews.widgets.blocks

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kingsrook.qqq.frontend.android.core.model.widgets.WidgetBlock
import com.kingsrook.qqq.frontend.android.mobileapp.ui.widgets.blocks.CompositeWidgetBlock
import com.kingsrook.qqq.frontend.android.mobileapp.ui.widgets.blocks.WidgetBlockParameters

/*******************************************************************************
 **
 *******************************************************************************/
fun makeTextBlock(text: String): WidgetBlock {
   return WidgetBlock("", "TEXT", values = mutableMapOf("text" to text))
}

/*******************************************************************************
 **
 *******************************************************************************/
fun makeCompositeBlock(layout: String, subBlocks: List<WidgetBlock>): WidgetBlock {
   return WidgetBlock("", "COMPOSITE", layout = layout, subBlocks = subBlocks)
}

val twoSubBlocks = listOf(
   makeTextBlock("one"),
   makeTextBlock("two")
)

/*******************************************************************************
 **
 *******************************************************************************/
@Composable
fun Test(layout: String, subBlocks: List<WidgetBlock>) {
   val params = WidgetBlockParameters(WidgetBlock("", "COMPOSITE", layout = layout, subBlocks = subBlocks), null)
   CompositeWidgetBlock(params)
}

/*******************************************************************************
 **
 *******************************************************************************/
@Preview(widthDp = 200)
@Composable
fun CompositeWidgetBlockFlexRowCenter() {
   Test("FLEX_ROW_CENTER", twoSubBlocks)
}

/*******************************************************************************
 **
 *******************************************************************************/
@Preview(widthDp = 200)
@Composable
fun CompositeWidgetBlockFlexRowSpaceBetween() {
   Test("FLEX_ROW_SPACE_BETWEEN", twoSubBlocks)
}

/*******************************************************************************
 **
 *******************************************************************************/
@Preview(widthDp = 200)
@Composable
fun CompositeWidgetBlockFlexRowWrapped() {
   Test(
      "FLEX_ROW_WRAPPED", listOf(
         makeTextBlock("something"), makeTextBlock("something else"), makeTextBlock("a third thing"), makeTextBlock("and ...")
      )
   )
}

/*******************************************************************************
 **
 *******************************************************************************/
@Preview(widthDp = 200)
@Composable
fun CompositeWidgetBlockFlexColumn() {
   Test("FLEX_COLUMN", twoSubBlocks)
}

/*******************************************************************************
 **
 *******************************************************************************/
@Preview(widthDp = 200)
@Composable
fun CompositeWidgetBlockThingForScanner() {
   Test(
      "FLEX_ROW_WRAPPED", listOf(
         makeTextBlock("Icon"),
         makeCompositeBlock(
            "FLEX_COLUMN", listOf(
               makeTextBlock("line 1"),
               makeTextBlock("line 2"),
               makeTextBlock("line 3"),
            )
         )
      )
   )
}

