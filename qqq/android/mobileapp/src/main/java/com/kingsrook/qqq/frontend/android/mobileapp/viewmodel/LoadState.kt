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

package com.kingsrook.qqq.frontend.android.mobileapp.viewmodel

/*******************************************************************************
 *******************************************************************************/
sealed interface LoadState<T> {
   /*******************************************************************************
    ** Carry a completed value separately from loading and display-safe failure states.
    *******************************************************************************/
   data class Success<T>(val value: T) : LoadState<T>

   /*******************************************************************************
    ** Carry the UI failure message without requiring the presentation layer to inspect transport exceptions.
    *******************************************************************************/
   data class Error<T>(val message: String) : LoadState<T> {
      constructor(e: Exception, m: String) : this("${m}: ${e.message ?: "Error details not available"}")
   }

   /*******************************************************************************
    ** Mark an in-progress operation and retain its start time for loading presentation.
    *******************************************************************************/
   data class Loading<T>(val unit: Unit) : LoadState<T> {
      val start = System.currentTimeMillis()
   }

   /*******************************************************************************
    ** Share aggregate loading/error decisions across composed metadata and process screens.
    *******************************************************************************/
   companion object {
      /*******************************************************************************
       *******************************************************************************/
      fun areAnyErrors(loadStates: List<LoadState<*>>): Boolean {
         for (loadState in loadStates) {
            if (loadState is Error) {
               return (true);
            }
         }

         return (false);
      }

      /*******************************************************************************
       *******************************************************************************/
      fun getErrorMessages(loadStates: List<LoadState<*>>): List<String> {
         val rs: MutableList<String> = mutableListOf();

         for (loadState in loadStates) {
            if (loadState is Error) {
               rs.add(loadState.message)
            }
         }

         return (rs)
      }

      /*******************************************************************************
       *******************************************************************************/
      fun areAnyLoading(loadStates: List<LoadState<*>>): Boolean {
         for (loadState in loadStates) {
            if (loadState is Loading) {
               return (true);
            }
         }

         return (false);
      }
   }
}
