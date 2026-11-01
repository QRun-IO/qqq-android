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

package com.kingsrook.qqq.sampleandroidmobileapp.previews.processes

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kingsrook.qqq.sampleandroidmobileapp.SampleAppMockQQQRepository
import com.kingsrook.qqq.frontend.android.mobileapp.ui.processes.ProcessStepLoading
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.ProcessViewModel

/*******************************************************************************
 **
 *******************************************************************************/
@Preview
@Composable
fun ProcessStepLoadingPreview() {
   val processViewModel = ProcessViewModel()
   processViewModel.qqqRepository = SampleAppMockQQQRepository()
   ProcessStepLoading(processViewModel)
}

/*******************************************************************************
 **
 *******************************************************************************/
@Preview
@Composable
fun ProcessStepLoadingPreviewWithMessageWithoutCurrentAndTotal() {
   val processViewModel = ProcessViewModel()
   processViewModel.qqqRepository = SampleAppMockQQQRepository()
   processViewModel.jobRunningMessage = "Working..."
   ProcessStepLoading(processViewModel)
}

/*******************************************************************************
 **
 *******************************************************************************/
@Preview
@Composable
fun ProcessStepLoadingPreviewSuperLongMessage() {
   val processViewModel = ProcessViewModel()
   processViewModel.qqqRepository = SampleAppMockQQQRepository()
   processViewModel.jobRunningMessage = "Doing a thing and stuff and words and more and and on and on."
   ProcessStepLoading(processViewModel)
}

/*******************************************************************************
 **
 *******************************************************************************/
@Preview
@Composable
fun ProcessStepLoadingPreviewWithMessageWithCurrentAndTotal() {
   val processViewModel = ProcessViewModel()
   processViewModel.qqqRepository = SampleAppMockQQQRepository()
   processViewModel.jobRunningMessage = "Processing Orders"
   processViewModel.currentOfTotalMessage = "3 of 2"
   ProcessStepLoading(processViewModel)
}

