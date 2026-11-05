package com.vsandoram.storagetidy.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vsandoram.storagetidy.data.Analyzer
import com.vsandoram.storagetidy.data.DocumentScanner
import com.vsandoram.storagetidy.data.DuplicateFinder
import com.vsandoram.storagetidy.data.DuplicateGroup
import com.vsandoram.storagetidy.data.MediaItem
import com.vsandoram.storagetidy.data.MediaScanner
import com.vsandoram.storagetidy.data.ScanReport
import com.vsandoram.storagetidy.data.SimilarFinder
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
    private val documentScanner = DocumentScanner()
    private val duplicateFinder = DuplicateFinder(app)
    private val similarFinder = SimilarFinder(app)

    /** Whether the app currently holds all-files access (needed to scan
     *  documents outside Downloads). Checked fresh each scan. */
    private fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

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
                val docsAccess = hasAllFilesAccess()
                val media = scanner.scanAll()
                val docs = if (docsAccess) {
                    _state.value = ScanState.Scanning("Scanning documents…")
                    documentScanner.scan(Environment.getExternalStorageDirectory())
                } else emptyList()
                val items = media + docs

                _state.value = ScanState.Scanning("Comparing for duplicates…", 0, items.size)
                val dupes = duplicateFinder.find(items) { done, total ->
                    _state.value = ScanState.Scanning("Comparing for duplicates…", done, total)
                }

                // Visual-similarity pass over photos (catches look-alike, not just
                // byte-identical, images). Exclude exact-duplicate copies so an
                // image isn't offered in both tabs.
                val exactExtraUris = dupes.flatMap { g -> g.extras.map { it.uri } }.toSet()
                val forSimilar = items.filterNot { it.uri in exactExtraUris }
                _state.value = ScanState.Scanning("Finding similar photos…", 0, forSimilar.size)
                val similar = similarFinder.find(forSimilar) { done, total ->
                    _state.value = ScanState.Scanning("Finding similar photos…", done, total)
                }

                val report = Analyzer.build(items, dupes, similar, docsAccess)
                _state.value = ScanState.Done(report)
            } catch (e: Exception) {
                _state.value = ScanState.Failed(e.message ?: "Scan failed")
            }
        }
    }

    fun toggle(uri: Uri) = _selected.update {
        if (uri in it) it - uri else it + uri
    }

    /** Tick every reclaimable extra in the given groups (used by the "Select all
     *  extras" button on the Duplicates and Similar tabs). */
    fun selectExtrasOf(groups: List<DuplicateGroup>) {
        _selected.value = groups.flatMap { g -> g.extras.map { it.uri } }.toSet()
    }

    fun clearSelection() { _selected.value = emptySet() }

    fun selectedUris(): List<Uri> = _selected.value.toList()

    /** The full selected items (needed to tell documents from media at delete). */
    fun selectedItems(): List<MediaItem> {
        val report = (_state.value as? ScanState.Done)?.report ?: return emptyList()
        val sel = _selected.value
        return report.allItems.filter { it.uri in sel }
    }

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
        fun prune(groups: List<DuplicateGroup>) = groups
            .map { g -> g.copy(items = g.items.filterNot { it.uri in goneSet }) }
            .filter { it.items.size > 1 }
        _state.value = ScanState.Done(
            Analyzer.build(
                survivors,
                prune(current.duplicates),
                prune(current.similar),
                current.documentsAccessGranted
            )
        )
        _selected.value = _selected.value - goneSet
    }
}
