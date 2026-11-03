/*
 * SPDX-License-Identifier: Apache-2.0
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2024-2024.  Kingsrook, LLC
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
 */

package com.kingsrook.qqq.sampleandroidmobileapp.previews.apps

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kingsrook.qqq.frontend.android.core.model.metadata.QAppMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.QAppNodeType
import com.kingsrook.qqq.frontend.android.core.model.metadata.QAppSection
import com.kingsrook.qqq.frontend.android.core.model.metadata.QAppTreeNode
import com.kingsrook.qqq.frontend.android.core.model.metadata.QInstance
import com.kingsrook.qqq.frontend.android.core.model.metadata.QProcessMetaData
import com.kingsrook.qqq.frontend.android.mobileapp.ui.apps.QAppHome

/*******************************************************************************
 **
 *******************************************************************************/
@Preview(widthDp = 400, heightDp = 800)
@Composable
fun QAppHomeTopLevelPreview() {
   val qInstance = QInstance(
      appTree = listOf(
         QAppTreeNode("app1", "App 1", QAppNodeType.APP),
         QAppTreeNode("app1", "App 2", QAppNodeType.APP),
         QAppTreeNode("app1", "App 3 has a long name in here and stuff and it keeps going and wrapping to multiple lines", QAppNodeType.APP),
      )
   )

   QAppHome(qInstance, Modifier, null)
}

/*******************************************************************************
 **
 *******************************************************************************/
@Preview(widthDp = 400, heightDp = 800)
@Composable
fun QAppHomeAppLevelPreviewWithWidgets() {
   val qInstance = QInstance()

   val app = QAppMetaData(
      "app2", "App 2", children = listOf(
         QAppTreeNode("app1", "App 2.1", QAppNodeType.APP),
         QAppTreeNode("app1", "App 2.2", QAppNodeType.APP),
         QAppTreeNode("app1", "App 2.3", QAppNodeType.APP),
      ),
      widgets = listOf("someLineChart", "someBarChart", "someWidgetProcess")
   )

   QAppHome(qInstance, Modifier, app)
}

/*******************************************************************************
 **
 *******************************************************************************/
@Preview(widthDp = 380, heightDp = 800)
@Composable
fun QAppHomeAppLevelPreviewWithProcesses() {
   val qInstance = QInstance(
      processes = mapOf(
         "process1" to QProcessMetaData("process1", "Process 1"),
         "process2" to QProcessMetaData("process2", "Process 2 with the very long and why wont name that wraps nicely")
      )
   )

   val app = QAppMetaData(
      "app2", "App 2", children = listOf(
         QAppTreeNode("app1", "App 2.1", QAppNodeType.APP),
         QAppTreeNode("app1", "App 2.2 and it has a silly a long name that wraps nicely", QAppNodeType.APP),
         QAppTreeNode("app1", "App 2.3", QAppNodeType.APP),
      ),
      sections = listOf(
         QAppSection(
            "section1", "Section 1", processes = listOf(
               "process1", "process2"
            )
         )
      )
   )

   QAppHome(qInstance, Modifier, app)
}
