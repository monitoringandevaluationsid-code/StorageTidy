package com.vsandoram.storagetidy.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.PhotoSizeSelectActual
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vsandoram.storagetidy.data.DuplicateGroup
import com.vsandoram.storagetidy.data.MediaItem
import com.vsandoram.storagetidy.viewmodel.ScanState
import com.vsandoram.storagetidy.util.Format
import com.vsandoram.storagetidy.viewmodel.TidyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TidyScreen(
    vm: TidyViewModel,
    onRequestDelete: (List<Uri>) -> Unit,
    onOpenStorageSettings: () -> Unit
) {
    val state by vm.state.collectAsState()
    val selected by vm.selected.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("StorageTidy") },
                actions = {
                    IconButton(onClick = onOpenStorageSettings) {
                        Icon(
                            Icons.Rounded.CleaningServices,
                            contentDescription = "Free up caches (opens Android storage settings)"
                        )
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            when (val s = state) {
                is ScanState.Idle -> IdleView(onScan = vm::scan)
                is ScanState.Scanning -> ScanningView(s)
                is ScanState.Failed -> FailedView(s.message, onRetry = vm::scan)
                is ScanState.Done -> ResultView(
                    vm = vm,
                    selected = selected,
                    onRequestDelete = onRequestDelete
                )
            }
        }
    }
}

@Composable
private fun IdleView(onScan: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Scan your Gallery and Downloads for duplicate, large and old files.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(32.dp)
            )
            Button(onClick = onScan) { Text("Start scan") }
        }
    }
}

@Composable
private fun ScanningView(s: ScanState.Scanning) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(s.phase, Modifier.padding(16.dp))
            if (s.total > 0) {
                LinearProgressIndicator(
                    progress = { s.done / s.total.toFloat() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp)
                )
                Text("${s.done} / ${s.total}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun FailedView(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Scan failed: $message", Modifier.padding(24.dp))
            Button(onClick = onRetry) { Text("Try again") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultView(
    vm: TidyViewModel,
    selected: Set<Uri>,
    onRequestDelete: (List<Uri>) -> Unit
) {
    val report = (vm.state.collectAsState().value as ScanState.Done).report
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Duplicates", "Screenshots", "Large", "Old")

    Column(Modifier.fillMaxSize()) {
        SummaryCard(report.totalBytes, report.duplicateCount, report.duplicateReclaimableBytes)

        TabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { i, title ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
            }
        }

        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> DuplicateList(report.duplicates, selected, vm::toggle, vm::selectAllExtras, vm::clearSelection)
                1 -> FlatList(report.screenshots, selected, vm::toggle, Icons.Rounded.Screenshot)
                2 -> FlatList(report.largeFiles, selected, vm::toggle, Icons.Rounded.PhotoSizeSelectActual)
                3 -> FlatList(report.oldFiles, selected, vm::toggle, Icons.Rounded.Schedule)
            }
        }

        if (selected.isNotEmpty()) {
            Button(
                onClick = { onRequestDelete(vm.selectedUris()) },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text("Delete ${selected.size} selected  ·  free ${Format.bytes(vm.selectedBytes())}")
            }
        }
    }
}

@Composable
private fun SummaryCard(total: Long, dupCount: Int, reclaimable: Long) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Scanned ${Format.bytes(total)} of media", fontWeight = FontWeight.Bold)
            Text("$dupCount duplicate copies found — ${Format.bytes(reclaimable)} reclaimable")
        }
    }
}

@Composable
private fun DuplicateList(
    groups: List<DuplicateGroup>,
    selected: Set<Uri>,
    onToggle: (Uri) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit
) {
    if (groups.isEmpty()) { EmptyState("No duplicates found. "); return }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSelectAll) { Text("Select all extras") }
                OutlinedButton(onClick = onClear) { Text("Clear") }
            }
        }
        groups.forEach { group ->
            item {
                Text(
                    "${group.items.size} identical copies · ${Format.bytes(group.keeper.sizeBytes)} each",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
                )
            }
            item {
                FileRow(group.keeper, checked = false, enabled = false, onToggle = {},
                    trailing = "keep")
            }
            items(group.extras, key = { it.uri.toString() }) { extra ->
                FileRow(extra, checked = extra.uri in selected, onToggle = { onToggle(extra.uri) })
            }
            item { Divider() }
        }
    }
}

@Composable
private fun FlatList(
    list: List<MediaItem>,
    selected: Set<Uri>,
    onToggle: (Uri) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    if (list.isEmpty()) { EmptyState("Nothing here. "); return }
    LazyColumn(Modifier.fillMaxSize()) {
        items(list, key = { it.uri.toString() }) { item ->
            FileRow(item, checked = item.uri in selected, onToggle = { onToggle(item.uri) }, leadingIcon = icon)
        }
    }
}

@Composable
private fun FileRow(
    item: MediaItem,
    checked: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean = true,
    trailing: String? = null,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (trailing == null) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled)
        } else {
            Icon(Icons.Rounded.ContentCopy, contentDescription = null,
                modifier = Modifier.padding(end = 12.dp))
        }

        // Thumbnail preview, with a generic-file glyph when the file has none.
        val bitmap = rememberThumbnail(item.uri)
        Box(
            modifier = Modifier
                .padding(end = 12.dp)
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(leadingIcon ?: Icons.Rounded.InsertDriveFile, contentDescription = null)
            }
        }

        Column(Modifier.weight(1f)) {
            Text(item.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(
                "${Format.bytes(item.sizeBytes)} · ${Format.date(item.dateAddedSeconds)}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        trailing?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun EmptyState(msg: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(msg, style = MaterialTheme.typography.bodyLarge)
    }
}
