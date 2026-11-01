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

package com.kingsrook.qqq.frontend.android.mobileapp.ui.widgets.blocks

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kingsrook.qqq.frontend.android.mobileapp.ui.LocalCustomWidgetRenderer

/*******************************************************************************
 ** Composable that takes a widget-block, switches based on the type, and
 ** then hands off to the block's composable.
 *******************************************************************************/
@Composable
fun WidgetBlockComposable(params: WidgetBlockParameters, modifier: Modifier = Modifier, disableControls: Boolean = false) {
   when (params.widgetBlock.blockType) {
      "AUDIO" -> AudioWidgetBlock(params, modifier)
      "BUTTON" -> ButtonWidgetBlock(params, modifier, disableControls)
      "COMPOSITE" -> CompositeWidgetBlock(params, modifier, disableControls)
      "DIVIDER" -> DividerWidgetBlock(params, modifier)
      "IMAGE" -> ImageWidgetBlock(params, modifier)
      "INPUT_FIELD" -> InputFieldWidgetBlock(params, modifier, disableControls)
      "TEXT" -> TextWidgetBlock(params, modifier)
      else -> if (!LocalCustomWidgetRenderer.current(params, modifier, disableControls)) {
         Text("Unsupported block type: ${params.widgetBlock.blockType}")
      }
   }
}
