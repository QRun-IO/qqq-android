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

package com.kingsrook.qqq.frontend.android.core.model.metadata


import org.junit.Assert.*
import org.junit.Test

/***************************************************************************
 **
 ***************************************************************************/
class QAppSectionTest
{
   /***************************************************************************
    **
    ***************************************************************************/
   @Test
   fun testHasContent()
   {
      assertFalse(QAppSection("test", "Test").hasContent())
      assertFalse(QAppSection("test", "Test", tables = null, processes = null, reports = null).hasContent())

      assertTrue(QAppSection("test", "Test", apps = listOf("childApp")).hasContent())
      assertTrue(QAppSection("test", "Test", tables = listOf("myTable")).hasContent())
      assertTrue(QAppSection("test", "Test", processes = listOf("myProcess")).hasContent())
      assertTrue(QAppSection("test", "Test", reports = listOf("myReport")).hasContent())
      assertTrue(QAppSection("test", "Test", tables = listOf("myTable"), processes = listOf("myProcess"), reports = listOf("myReport")).hasContent())
   }
}