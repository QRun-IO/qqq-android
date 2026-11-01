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

import com.kingsrook.qqq.frontend.android.core.model.MutableMapOfStringAny
import kotlinx.serialization.Serializable

/*******************************************************************************
 ** Represent server widget structure without selecting client classes from arbitrary payload values.
 *******************************************************************************/
@Serializable
data class WidgetBlock(
   val blockId: String,
   val blockType: String,
   val conditional: String? = null,
   val layout: String? = null,
   var modalMode: String? = null,
   val styles: MutableMapOfStringAny = mutableMapOf(),
   val values: MutableMapOfStringAny = mutableMapOf(),
   var subBlocks: List<WidgetBlock> = emptyList(),
) {
   /*******************************************************************************
    ** Centralize conversion of legacy map-shaped widget blocks into the semantic block model.
    *******************************************************************************/
   companion object {
      /*******************************************************************************
       ** Rebuild nested widget structure only from map/list input, retaining unknown block types for explicit UI handling.
       *******************************************************************************/
      fun fromMap(param: Any?): WidgetBlock? {
         if (param is Map<*, *>) {
            /*******************************************************************************
             ** Preserve absent optional string values while adapting the legacy generic widget map.
             *******************************************************************************/
            fun mapGetOrElse(map: Map<*, *>, key: String, elseValue: String? = null): String? {
               if (map[key] != null) {
                  return map[key].toString()
               }

               return elseValue
            }

            val map = param
            val blockId = if (map["blockId"] != null) "${map["blockId"]}" else ""
            val blockType = if (map["blockType"] != null) "${map["blockType"]}" else ""
            val conditional = if (map["conditional"] != null) "${map["conditional"]}" else null
            val layout = if (map["layout"] != null) "${map["layout"]}" else null
            val modalMode = mapGetOrElse(map, "modalMode")

            /*******************************************************************************
             ** Copy validated JSON property names into owned mutable state; input maps
             ** may be immutable or shared by another widget's decoded metadata.
             *******************************************************************************/
            fun copyStringMap(value: Any?): MutableMapOfStringAny {
               val source = value as? Map<*, *> ?: return mutableMapOf()
               val result = mutableMapOf<String, Any?>()
               source.forEach { (key, itemValue) ->
                  require(key is String) { "Widget property names must be strings" }
                  result[key] = itemValue
               }
               return result
            }

            val styles = copyStringMap(map["styles"])
            val values = copyStringMap(map["values"])

            val mapSubBlocks = map["subBlocks"]

            var subBlocks = emptyList<WidgetBlock>()
            if (mapSubBlocks is List<*>) {
               subBlocks = mutableListOf()
               mapSubBlocks.forEach()
               { subBlock ->
                  val subWidgetBlock = fromMap(subBlock)
                  if (subWidgetBlock != null) {
                     subBlocks.add(subWidgetBlock)
                  }
               }
            }

            return WidgetBlock(blockId, blockType, conditional, layout, modalMode, styles, values, subBlocks)
         }

         return (null)
      }
   }
}
