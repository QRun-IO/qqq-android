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

package com.kingsrook.qqq.sampleandroidmobileapp.previews.processes.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kingsrook.qqq.sampleandroidmobileapp.SampleAppMockQQQRepository
import com.kingsrook.qqq.frontend.android.core.model.metadata.QComponentType
import com.kingsrook.qqq.frontend.android.core.model.metadata.QFrontendComponent
import com.kingsrook.qqq.frontend.android.core.model.metadata.QInstance
import com.kingsrook.qqq.frontend.android.mobileapp.ui.processes.components.ProcessComponentWidget
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.ProcessViewModel

/***************************************************************************
 **
 ***************************************************************************/
@Preview
@Composable
fun ProcessComponentWidgetPreview()
{
   val processViewModel = ProcessViewModel()
   processViewModel.qqqRepository = SampleAppMockQQQRepository()
   val component = QFrontendComponent(type = QComponentType.WIDGET, values = mapOf(
      "isAdHocWidget" to true,
      "blocks" to listOf(
         mapOf(
            "blockType" to "TEXT",
            "styles" to mapOf(
               "foo" to "bar"
            ),
            "values" to mapOf(
               "text" to "this is my text, what do you want from me?"
            ),
         )
      )
   ))
   ProcessComponentWidget(processViewModel, QInstance(),  component)
}

/***************************************************************************
 **
 ***************************************************************************/
@Preview
@Composable
fun ProcessComponentWidgetPreviewMultipleAdHoc()
{
   val processViewModel = ProcessViewModel()
   processViewModel.qqqRepository = SampleAppMockQQQRepository()
   val component = QFrontendComponent(type = QComponentType.WIDGET, values = mapOf(
      "isAdHocWidget" to true,
      "blocks" to listOf(
         mapOf(
            "blockType" to "TEXT",
            "styles" to mapOf(
               "standardColor" to "SUCCESS"
            ),
            "values" to mapOf(
               "text" to "this is my text, what do you want from me?"
            ),
         ),
         mapOf(
            "blockType" to "TEXT",
            "values" to mapOf(
               "text" to "and this is mine."
            ),
         )
      )
   ))
   ProcessComponentWidget(processViewModel, QInstance(), component)
}


/***************************************************************************
 **
 ***************************************************************************/
@Preview
@Composable
fun ProcessComponentWidgetAdHocUnknownType()
{
   val processViewModel = ProcessViewModel()
   processViewModel.qqqRepository = SampleAppMockQQQRepository()
   val component = QFrontendComponent(type = QComponentType.WIDGET, values = mapOf(
      "isAdHocWidget" to true,
      "blocks" to listOf(
         mapOf(
            "blockType" to "FOOBAR"
         )
      )
   ))
   ProcessComponentWidget(processViewModel, QInstance(), component)
}

/***************************************************************************
 **
 ***************************************************************************/
@Preview
@Composable
fun ProcessComponentWidgetPreviewNonAdHoc()
{
   val processViewModel = ProcessViewModel()
   processViewModel.qqqRepository = SampleAppMockQQQRepository()
   val component = QFrontendComponent(type = QComponentType.WIDGET, values = mapOf(
      "widgetName" to "testWidget"
   ))
   ProcessComponentWidget(processViewModel, QInstance(), component)
}

