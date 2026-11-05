package com.vsandoram.storagetidy.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vsandoram.storagetidy.data.Analyzer
import com.vsandoram.storagetidy.data.DuplicateFinder
import com.vsandoram.storagetidy.data.MediaItem
import com.vsandoram.storagetidy.data.MediaScanner
import com.vsandoram.storagetidy.data.ScanReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ScanState {
    data object Idle : ScanState
    data class Scanning(val phase: String, val done: Int = 0, val total: Int = 0) : ScanState
    data class Done(val report: ScanReport) : ScanState
    data class Failed(val message: String) : ScanState
}

class TidyViewModel(app: Application) : AndroidViewModel(app) {

    private val scanner = MediaScanner(app)
    private val duplicateFinder = DuplicateFinder(app)

    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    /** URIs the user has ticked for deletion. */
    private val _selected = MutableStateFlow<Set<Uri>>(emptySet())
    val selected: StateFlow<Set<Uri>> = _selected.asStateFlow()

    fun scan() {
        viewModelScope.launch {
            _selected.value = emptySet()
            _state.value = ScanState.Scanning("Reading your files…")
            try {
                val items = scanner.scanAll()
                _state.value = ScanState.Scanning("Comparing for duplicates…", 0, items.size)
                val dupes = duplicateFinder.find(items) { done, total ->
                    _state.value = ScanState.Scanning("Comparing for duplicates…", done, total)
                }
                val report = Analyzer.build(items, dupes)
                _state.value = ScanState.Done(report)
            } catch (e: Exception) {
                _state.value = ScanState.Failed(e.message ?: "Scan failed")
            }
        }
    }

    fun toggle(uri: Uri) = _selected.update {
        if (uri in it) it - uri else it + uri
    }

    fun selectAllExtras() {
        val report = (_state.value as? ScanState.Done)?.report ?: return
        _selected.value = report.duplicates.flatMap { g -> g.extras.map { it.uri } }.toSet()
    }

    fun clearSelection() { _selected.value = emptySet() }

    fun selectedUris(): List<Uri> = _selected.value.toList()

    /** Selected size, for the "reclaim X" button label. */
    fun selectedBytes(): Long {
        val report = (_state.value as? ScanState.Done)?.report ?: return 0
        val sel = _selected.value
        return report.allItems.filter { it.uri in sel }.sumOf { it.sizeBytes }
    }

    /** Called after the system delete dialog confirms. Removes the gone URIs
     *  from the report so the UI updates without a full re-scan. */
    fun onDeleted(deleted: Collection<Uri>) {
        val current = (_state.value as? ScanState.Done)?.report ?: return
        val goneSet = deleted.toSet()
        val survivors = current.allItems.filterNot { it.uri in goneSet }
        val newDupes = current.duplicates
            .map { g -> g.copy(items = g.items.filterNot { it.uri in goneSet }) }
            .filter { it.items.size > 1 }
        _state.value = ScanState.Done(Analyzer.build(survivors, newDupes))
        _selected.value = _selected.value - goneSet
    }
}
