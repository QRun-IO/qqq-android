/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.mobileapp.ui.records

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kingsrook.qqq.frontend.android.core.model.RecordData
import com.kingsrook.qqq.frontend.android.core.model.RecordSearch
import com.kingsrook.qqq.frontend.android.core.model.formatted
import com.kingsrook.qqq.frontend.android.core.model.parentMatches
import com.kingsrook.qqq.frontend.android.core.model.recordGroups
import com.kingsrook.qqq.frontend.android.core.model.recordSearchFields
import com.kingsrook.qqq.frontend.android.core.model.summary
import com.kingsrook.qqq.frontend.android.core.model.textFields
import com.kingsrook.qqq.frontend.android.core.model.metadata.QRecordAssociation
import com.kingsrook.qqq.frontend.android.core.model.metadata.QTableMetaData
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel
import com.kingsrook.qqq.frontend.android.mobileapp.ui.LocalNavigationBackAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonPrimitive

/** Attachment state stays mounted even when another tab is selected. */
data class RecordAttachmentUi(val photosVisible: Boolean, val captureVisible: Boolean, val closeCapture: () -> Unit, val available: (Boolean) -> Unit, val recordLabel: String)
val LocalRecordAttachments = compositionLocalOf<@Composable (QViewModel, String, String, RecordAttachmentUi) -> Unit> { { _, _, _, _ -> } }

/** Save page, search, filter and scroll state in this navigation entry. */
@Composable
fun TableScreen(host: QViewModel, tableName: String) {
   var offset by rememberSaveable(tableName, host.sessionUUID) { mutableStateOf(0) }
   var search by rememberSaveable(tableName, host.sessionUUID) { mutableStateOf("") }
   var filterField by rememberSaveable(tableName, host.sessionUUID) { mutableStateOf("") }
   var filterValue by rememberSaveable(tableName, host.sessionUUID) { mutableStateOf("") }
   var filterOpen by remember { mutableStateOf(false) }
   var reload by remember { mutableStateOf(0) }
   var table by remember(tableName, host.sessionUUID) { mutableStateOf<QTableMetaData?>(null) }
   var rows by remember { mutableStateOf<List<RecordData>>(emptyList()) }
   var count by remember { mutableStateOf(0L) }
   var loading by remember { mutableStateOf(true) }
   var failed by remember { mutableStateOf(false) }
   val scroll = rememberLazyListState()
   LaunchedEffect(tableName, offset, search, filterField, filterValue, reload, host.sessionUUID) {
      loading = true; failed = false
      try {
         if (search.isNotEmpty()) delay(300)
         val metadata = table ?: host.qqqRepository.getTableMetaData(tableName).also { table = it }
         val predicate = RecordSearch(search, metadata.recordSearchFields(), if (filterField.isNotEmpty() && filterValue.isNotEmpty()) mapOf(filterField to JsonPrimitive(filterValue)) else emptyMap())
         count = host.qqqRepository.countRecords(tableName, predicate)
         if (offset > 0 && offset >= count) { offset = 0; return@LaunchedEffect }
         rows = host.qqqRepository.searchRecords(tableName, requireNotNull(metadata.primaryKeyField), predicate, offset)
      } catch (cancelled: CancellationException) { throw cancelled }
      catch (exception: Exception) { failed = true; rows = emptyList() }
      finally { loading = false }
   }
   Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
      OutlinedTextField(search, { search = it; offset = 0 }, label = { Text("Search records") }, singleLine = true,
         modifier = Modifier.fillMaxWidth().testTag("records.search"))
      Row(verticalAlignment = Alignment.CenterVertically) {
         TextButton(onClick = { filterOpen = true }, enabled = table?.textFields()?.isNotEmpty() == true) { Text(if (filterValue.isEmpty()) "Filter" else "Filter applied") }
         if (search.isNotEmpty() || filterValue.isNotEmpty()) TextButton(onClick = { search = ""; filterField = ""; filterValue = ""; offset = 0 }) { Text("Clear") }
         TextButton(onClick = { reload++ }) { Text("Refresh") }
      }
      if (loading) Text("Loading records…")
      if (failed) Text("Records could not be loaded. Check your connection and access.")
      if (!loading && !failed && rows.isEmpty()) Text("No matching records.")
      if (loading || failed) Spacer(Modifier.weight(1f)) else LazyColumn(state = scroll, modifier = Modifier.weight(1f)) {
         if (!loading && !failed) items(rows, key = { requireNotNull(table?.primaryKeyField?.let(it::id)) }) { row ->
            val id = requireNotNull(table?.primaryKeyField?.let(row::id))
            RecordRow(row, table!!, "records.row:$id") { host.qNavigator.navigateToRecord(tableName, id) }
         }
      }
      if (!failed) Paging(offset, rows.size, count, 25, loading, { offset = it })
   }
   if (filterOpen && table != null) RecordFilterDialog(table!!, filterField, filterValue, { filterOpen = false }) { field, value ->
      filterField = field; filterValue = value; offset = 0; filterOpen = false
   }
}

@Composable
private fun RecordFilterDialog(table: QTableMetaData, initialField: String, initialValue: String, close: () -> Unit, apply: (String, String) -> Unit) {
   val fields = table.textFields()
   var field by remember { mutableStateOf(fields.firstOrNull { it.name == initialField } ?: fields.first()) }
   var value by remember { mutableStateOf(initialValue) }
   var choose by remember { mutableStateOf(false) }
   AlertDialog(onDismissRequest = close, title = { Text("Filter records") }, text = {
      Column {
         Box {
            TextButton(onClick = { choose = true }) { Text("Field: ${field.label}") }
            DropdownMenu(choose, { choose = false }) { fields.forEach { item -> DropdownMenuItem(text = { Text(item.label) }, onClick = { field = item; choose = false }) } }
         }
         OutlinedTextField(value, { value = it }, label = { Text("Equals") }, singleLine = true, modifier = Modifier.testTag("records.filter.value"))
      }
   }, confirmButton = { TextButton(onClick = { apply(field.name, value) }) { Text("Apply filter") } }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

@Composable
private fun Paging(offset: Int, size: Int, count: Long, pageSize: Int, loading: Boolean, change: (Int) -> Unit) {
   Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
      Text(if (loading) "Loading…" else if (count == 0L) "0 records" else "${offset + 1}–${offset + size} of $count", style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("records.count"))
      Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
         TextButton(enabled = !loading && offset > 0, onClick = { change((offset - pageSize).coerceAtLeast(0)) }) { Text("Previous") }
         TextButton(enabled = !loading && offset + size < count, onClick = { change(offset + pageSize) }) { Text("Next") }
      }
   }
}

@Composable
private fun RecordRow(record: RecordData, table: QTableMetaData, tag: String, open: () -> Unit) {
   Row(Modifier.fillMaxWidth().testTag(tag).clickable(onClickLabel = "Open record", onClick = open).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
         Text(record.recordLabel ?: table.primaryKeyField?.let(record::id).orEmpty(), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
         Text(record.summary(table), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
      }
      Text("›", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleLarge)
   }
   HorizontalDivider()
}

/** Identity and Actions remain fixed above content. Record values never enter navigation telemetry. */
@Composable
fun RecordScreen(host: QViewModel, tableName: String, recordId: String, hasParent: Boolean = false) {
   // Register inside the destination, after NavHost's own callback, as process screens do.
   val hostBack = LocalNavigationBackAction.current
   BackHandler { if (hostBack != null) hostBack() else host.qNavigator.popStack() }
   var record by remember(tableName, recordId, host.sessionUUID) { mutableStateOf<RecordData?>(null) }
   var table by remember(tableName, recordId, host.sessionUUID) { mutableStateOf<QTableMetaData?>(null) }
   var failed by remember { mutableStateOf(false) }
   var reload by remember { mutableStateOf(0) }
   var tab by rememberSaveable(tableName, recordId, host.sessionUUID) { mutableStateOf(0) }
   var group by rememberSaveable(tableName, recordId, host.sessionUUID) { mutableStateOf(0) }
   var actions by remember { mutableStateOf(false) }
   var sections by remember { mutableStateOf(false) }
   var capture by rememberSaveable(tableName, recordId, host.sessionUUID) { mutableStateOf(false) }
   var photoAvailable by remember(tableName, recordId, host.sessionUUID) { mutableStateOf(false) }
   val attachments = LocalRecordAttachments.current
   val tabState = rememberSaveableStateHolder()
   LaunchedEffect(tableName, recordId, host.sessionUUID, reload) {
      failed = false
      try {
         val metadata = host.qqqRepository.getTableMetaData(tableName)
         val loaded = host.qqqRepository.getRecord(tableName, recordId)
         check(metadata.primaryKeyField?.let(loaded::id) == recordId)
         check(loaded.tableName == null || loaded.tableName == tableName)
         table = metadata; record = loaded
      } catch (cancelled: CancellationException) { throw cancelled }
      catch (exception: Exception) { failed = true }
   }
   val current = record
   if (failed) Column(Modifier.padding(16.dp)) {
      Text("This record could not be loaded. It may be unavailable or access may have changed.")
      Button(onClick = { reload++ }) { Text("Retry") }
   } else if (current == null) Text("Loading record…", Modifier.padding(16.dp))
   else {
      val metadata = table!!
      val related = metadata.associations.orEmpty()
      val groups = metadata.recordGroups()
      val photoTab = related.size + 1
      Column(Modifier.fillMaxSize().testTag("record.details")) {
         Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
               Text(current.recordLabel ?: recordId, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
               Text(metadata.label, style = MaterialTheme.typography.labelMedium)
            }
            Box {
               TextButton(onClick = { actions = true }) { Text("Actions") }
               DropdownMenu(actions, { actions = false }) {
                  DropdownMenuItem(text = { Text("Add photo") }, enabled = photoAvailable, onClick = { actions = false; capture = true })
                  DropdownMenuItem(text = { Text("Refresh record") }, onClick = { actions = false; reload++ })
               }
            }
         }
         if (hasParent) TextButton(onClick = { host.qNavigator.requestLeave("Back to parent?") { host.qNavigator.navController?.popBackStack() } }) { Text("Back to parent record") }
         ScrollableTabRow(tab.coerceAtMost(photoTab), edgePadding = 0.dp) {
            (listOf("Details") + related.map { it.name } + "Photos").forEachIndexed { index, label ->
               Tab(tab == index, onClick = { tab = index }, text = { Text(label) })
            }
         }
         Box(Modifier.weight(1f)) {
            if (tab == 0) Column(Modifier.fillMaxSize()) {
               Box {
                  TextButton(onClick = { sections = true }) { Text("Sections (${groups.size})") }
                  DropdownMenu(sections, { sections = false }) { groups.forEachIndexed { index, value ->
                     DropdownMenuItem(text = { Text(value.label) }, onClick = { group = index; sections = false })
                  } }
               }
               Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                  groups.forEachIndexed { index, value ->
                     TextButton(onClick = { group = if (group == index) -1 else index }, modifier = Modifier.fillMaxWidth().testTag("record.section:${value.label}")) {
                        Text("${if (group == index) "−" else "+"} ${value.label} (${value.fields.size})", Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium)
                     }
                     if (group == index) value.fields.forEach { field ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                           Text(field.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                           Text(current.formatted(field))
                        }
                     }
                     HorizontalDivider()
                  }
               }
            }
            if (tab in 1..related.size) tabState.SaveableStateProvider("related:$tab") { RelatedRecords(host, tableName, recordId, current, related[tab - 1]) }
            attachments(host, tableName, recordId, RecordAttachmentUi(tab == photoTab, capture, { capture = false }, { photoAvailable = it }, current.recordLabel ?: recordId))
         }
      }
   }
}

/** Child queries and counts always use the declared parent join. */
@Composable
private fun RelatedRecords(host: QViewModel, parentTable: String, parentId: String, parent: RecordData, association: QRecordAssociation) {
   var offset by rememberSaveable(parentTable, parentId, association.name, host.sessionUUID) { mutableStateOf(0) }
   var reload by remember { mutableStateOf(0) }
   var rows by remember { mutableStateOf<List<RecordData>>(emptyList()) }
   var table by remember { mutableStateOf<QTableMetaData?>(null) }
   var count by remember { mutableStateOf(0L) }
   var loading by remember { mutableStateOf(true) }
   var failed by remember { mutableStateOf(false) }
   val scroll = rememberLazyListState()
   LaunchedEffect(parentTable, parentId, association, offset, reload, host.sessionUUID) {
      loading = true; failed = false
      try {
         val matches = requireNotNull(association.parentMatches(parentTable, parent))
         check(matches.isNotEmpty())
         val child = host.qqqRepository.getTableMetaData(association.associatedTableName)
         check(matches.keys.all { it in child.fields })
         table = child
         val predicate = RecordSearch(matches = matches)
         count = host.qqqRepository.countRecords(child.name, predicate)
         rows = host.qqqRepository.searchRecords(child.name, requireNotNull(child.primaryKeyField), predicate, offset, 10)
      } catch (cancelled: CancellationException) { throw cancelled }
      catch (exception: Exception) { failed = true; rows = emptyList() }
      finally { loading = false }
   }
   Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("record.related:${association.associatedTableName}")) {
      if (loading) Text("Loading related records…")
      if (failed) { Text("Related records are unavailable."); TextButton(onClick = { reload++ }) { Text("Retry") } }
      if (!loading && !failed && rows.isEmpty()) Text("No related records.")
      LazyColumn(state = scroll, modifier = Modifier.weight(1f)) {
         if (!loading && !failed) items(rows) { row ->
            val id = table?.primaryKeyField?.let(row::id)
            if (id != null) RecordRow(row, table!!, "record.related.row:${association.associatedTableName}:$id") { host.qNavigator.navigateToRecord(association.associatedTableName, id, true) }
         }
      }
      if (!failed) Paging(offset, rows.size, count, 10, loading, { offset = it })
   }
}
