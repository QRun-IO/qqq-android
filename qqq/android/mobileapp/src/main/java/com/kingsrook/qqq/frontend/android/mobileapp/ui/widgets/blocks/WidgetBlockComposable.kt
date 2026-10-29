/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2004-2024.  Kingsrook, LLC
 * 651 N Broad St Ste 205 # 6917 | Middletown DE 19709 | United States
 * contact@kingsrook.com
 * https://github.com/Kingsrook/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.kingsrook.qqq.frontend.android.mobileapp.ui.widgets.blocks

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kingsrook.qqq.frontend.android.mobileapp.ui.LocalCustomWidgetRenderer

/***************************************************************************
 ** Composable that takes a widget-block, switches based on the type, and
 ** then hands off to the block's composable.
 ***************************************************************************/
@Composable
fun WidgetBlockComposable(params: WidgetBlockParameters, modifier: Modifier = Modifier, disableControls: Boolean = false)
{
   when (params.widgetBlock.blockType)
   {
      "AUDIO" -> AudioWidgetBlock(params, modifier)
      "BUTTON" -> ButtonWidgetBlock(params, modifier, disableControls)
      "COMPOSITE" -> CompositeWidgetBlock(params, modifier, disableControls)
      "DIVIDER" -> DividerWidgetBlock(params, modifier)
      "IMAGE" -> ImageWidgetBlock(params, modifier)
      "INPUT_FIELD" -> InputFieldWidgetBlock(params, modifier, disableControls)
      "TEXT" -> TextWidgetBlock(params, modifier)
      else -> if(!LocalCustomWidgetRenderer.current(params, modifier, disableControls))
      {
         Text("Unsupported block type: ${params.widgetBlock.blockType}")
      }
   }
}
