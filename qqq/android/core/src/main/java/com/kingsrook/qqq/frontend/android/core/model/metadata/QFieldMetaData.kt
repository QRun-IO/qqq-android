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

package com.kingsrook.qqq.frontend.android.core.model.metadata

import com.kingsrook.qqq.frontend.android.core.model.AnyValueSerializer
import com.kingsrook.qqq.frontend.android.core.model.QPossibleValue
import kotlinx.serialization.Serializable

/*******************************************************************************
 *******************************************************************************/
@Serializable
data class QFieldMetaData(
   val name: String,
   val label: String,
   @Serializable(with = AnyValueSerializer::class)
   val defaultValue: Any? = null,
   val type: QFieldType,
   val isRequired: Boolean = false,
   val isEditable: Boolean = true,
   val isHeavy: Boolean = false,
   val possibleValueSourceName: String? = null,
   val inlinePossibleValueSource: List<QPossibleValue>? = null,
   val displayFormat: String? = null,
   val adornments: List<FieldAdornment>? = null,
   val helpContents: List<QHelpContent>? = null,
   val isHidden: Boolean = false,
   val validation: FieldValidation? = null,
) {

}

/*******************************************************************************
 ** Carry semantic field constraints so native validation follows the server contract.
 *******************************************************************************/
@Serializable
data class FieldValidation(
   val required: Boolean? = null,
   val maxLength: Int? = null,
   val tooLongBehavior: String? = null,
   val minValue: String? = null,
   val maxValue: String? = null,
   val minInclusive: Boolean? = null,
   val maxInclusive: Boolean? = null,
   val pattern: String? = null,
   val patternMessage: String? = null,
)
