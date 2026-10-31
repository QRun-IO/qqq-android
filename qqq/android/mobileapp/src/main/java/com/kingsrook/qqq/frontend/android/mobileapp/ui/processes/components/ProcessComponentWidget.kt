/*
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

package com.kingsrook.qqq.frontend.android.mobileapp.ui.processes.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kingsrook.qqq.frontend.android.core.model.metadata.QFrontendComponent
import com.kingsrook.qqq.frontend.android.core.model.metadata.QInstance
import com.kingsrook.qqq.frontend.android.core.model.widgets.WidgetBlock
import com.kingsrook.qqq.frontend.android.mobileapp.ui.utils.Colors
import com.kingsrook.qqq.frontend.android.mobileapp.ui.widgets.blocks.WidgetBlockComposable
import com.kingsrook.qqq.frontend.android.mobileapp.ui.widgets.blocks.WidgetBlockParameters
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.ProcessViewModel
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.SemanticProcess

/***************************************************************************
 ** process-component to render a widget
 ***************************************************************************/
@Composable
fun ProcessComponentWidget(processViewModel: ProcessViewModel, qInstance: QInstance, component: QFrontendComponent, disableControls: Boolean = false)
{
   val state = remember(processViewModel.stepInstanceCounter, component) { determineState(processViewModel, qInstance, component) }

   // The process owns vertical scrolling so widgets and forms share one viewport.
   Column(Modifier.fillMaxWidth())
   {
      (state.headerBlockList + state.bodyBlockList + state.footerBlockList).forEach()
      { widgetBlock ->
         WidgetBlockComposable(
            WidgetBlockParameters(widgetBlock, processViewModel::actionCallback, processViewModel.qViewModel, processViewModel.processValues),
            disableControls = disableControls,
            modifier = Modifier,
         )
      }
      state.warningStrings.forEach()
      { warning ->
         Text(warning, color = Colors.WARNING, modifier = Modifier.padding(8.dp))
      }
   }
}

/***************************************************************************
 ** container for the state within this widget
 ***************************************************************************/
data class ProcessComponentWidgetState(
   val warningStrings: List<String> = mutableListOf(),
   val headerBlockList: List<WidgetBlock> = mutableListOf(),
   val bodyBlockList: List<WidgetBlock> = mutableListOf(),
   val footerBlockList: List<WidgetBlock> = mutableListOf(),
   val needFullHeight: Boolean = false,
)

/***************************************************************************
 ** function to figure out the (initial, and probably only) state of this
 ** widget
 ***************************************************************************/
private fun determineState(processViewModel: ProcessViewModel, qInstance: QInstance, component: QFrontendComponent): ProcessComponentWidgetState
{
   val warningStrings = mutableListOf<String>()
   val headerBlockList = mutableListOf<WidgetBlock>()
   val bodyBlockList = mutableListOf<WidgetBlock>()
   val footerBlockList = mutableListOf<WidgetBlock>()
   val needFullHeight = false

   //////////////////////////////////////////////////////////////////////////////////////////////
   // if this component says it's a list of adhoc widgets, then do some pre-processing on them //
   //////////////////////////////////////////////////////////////////////////////////////////////
   val widgetBlockList = mutableListOf<WidgetBlock>()

   if(component.values["isAdHocWidget"] == true)
   {
      val blocks = component.values["blocks"]
      if(blocks is List<*>)
      {
         blocks.forEach()
         { block ->
            val widgetBlock = WidgetBlock.fromMap(block)
            if(widgetBlock != null)
            {
               widgetBlockList.add(widgetBlock)
            }
         }
      }
      else
      {
         warningStrings.add("Widget blocks was not an expected type.")
      }

   }
   else if(component.values["widgetName"] != null)
   {
      val widgetName = "${component.values["widgetName"]}"
      val widgetMetaData = qInstance.widgets?.get(widgetName)
      if(widgetMetaData == null)
      {
         warningStrings.add("Undefined widget: ${widgetName}")
      }
      else
      {
         if(widgetMetaData.type == "composite")
         {
            val widgetData = processViewModel.processValues[widgetName]
            var foundBlocks = false
            if(widgetData is Map<*, *>)
            {
               val blocks = widgetData["subBlocks"]
               if(blocks is List<*>)
               {
                  blocks.forEach()
                  { block ->
                     val widgetBlock = WidgetBlock.fromMap(block)
                     if(widgetBlock != null)
                     {
                        widgetBlockList.add(widgetBlock)
                        foundBlocks = true
                     }
                  }
               }
            }

            if(!foundBlocks)
            {
               warningStrings.add("Could not find widget block data in process.")
            }
         }
         else
         {
            warningStrings.add("Not-yet implemented widget type: ${widgetMetaData.type}")
         }
      }
   }
   else
   {
      warningStrings.add("Missing required values in a wiget component")
   }

   /////////////////////////////////////////////////////////////////////////////////////////
   // split into body & footer lists                                                      //
   // todo no no, this should be done based on some attribute in the widget/blocks/etc... //
   /////////////////////////////////////////////////////////////////////////////////////////
   evaluateWidgetBlocks(widgetBlockList, processViewModel.processValues)
   bodyBlockList.addAll(widgetBlockList)
   /*
   if(bodyBlockList.size > 2)
   {
      headerBlockList.add(bodyBlockList.removeAt(0))
      footerBlockList.add(bodyBlockList.removeAt(bodyBlockList.size - 1))
      needFullHeight = true
   }
   */

   return ProcessComponentWidgetState(warningStrings, headerBlockList, bodyBlockList, footerBlockList, needFullHeight)
}

/***************************************************************************
 ** applies "conditional" logic to blocks; interpolate values too.
 ***************************************************************************/
fun evaluateWidgetBlocks(widgetBlockList: MutableList<WidgetBlock>, processValues: MutableMap<String, Any?>)
{
   val iterator = widgetBlockList.iterator()
   while(iterator.hasNext())
   {
      val widgetBlock = iterator.next()

      ////////////////////////////////////////////////////////////////////
      // if the block has a conditional, evaluate, and remove if untrue //
      ////////////////////////////////////////////////////////////////////
      if(!SemanticProcess.widgetVisible(widgetBlock.conditional, processValues))
      {
         iterator.remove()
         continue
      }

      if(widgetBlock.blockType == "COMPOSITE")
      {
         /////////////////////////////////////////
         // make recursive calls for composites //
         /////////////////////////////////////////
         val mutableSubBlocks = widgetBlock.subBlocks.toMutableList()
         evaluateWidgetBlocks(mutableSubBlocks, processValues)
         widgetBlock.subBlocks = mutableSubBlocks
      }
      else if(widgetBlock.blockType == "INPUT_FIELD")
      {
         /////////////////////////////////////////////////////////////////////////////////////////////////////////////
         // for input fields, put the process's value for the field-name into the block's values object as '.value' //
         // todo QFMD was doing something here, but we didn't do it in qqq-android yet...                           //
         /////////////////////////////////////////////////////////////////////////////////////////////////////////////
         // val fieldMetaData = widgetBlock.values["fieldMetaData"]
         // if(fieldMetaData != null && fieldMetaData is Map<*, *>)
         // {
         //    val fieldName = fieldMetaData["name"]
         //    if(processValues.containsKey(fieldName))
         //    {
         //       widgetBlock.values["value"] = processValues[fieldName]
         //    }
         // }
      }
      else if(widgetBlock.blockType == "TEXT")
      {
         var text = widgetBlock.values["text"]
         if(text != null && text is String)
         {
            for(entry in processValues.entries)
            {
               text = (text as String).replace('$' + "{" + entry.key + "}", "${processValues[entry.key]}")
            }
         }
         widgetBlock.values["interpolatedText"] = text
      }
   }
}

