package com.vsandoram.storagetidy

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vsandoram.storagetidy.ui.PermissionGate
import com.vsandoram.storagetidy.ui.TidyScreen
import com.vsandoram.storagetidy.ui.theme.StorageTidyTheme
import com.vsandoram.storagetidy.viewmodel.TidyViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StorageTidyTheme {
                val vm: TidyViewModel = viewModel()

                var granted by remember { mutableStateOf(hasMediaPermission()) }

                // Pending delete so we can confirm it once the system dialog returns OK.
                var pendingDelete by remember { mutableStateOf<List<Uri>>(emptyList()) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { result ->
                    granted = result.values.any { it } || hasMediaPermission()
                }

                val deleteLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartIntentSenderForResult()
                ) { activityResult ->
                    if (activityResult.resultCode == Activity.RESULT_OK) {
                        vm.onDeleted(pendingDelete)
                    }
                    pendingDelete = emptyList()
                }

                // Launches Android's system delete sheet for media items.
                val requestMediaDelete: (List<Uri>) -> Unit = { uris ->
                    if (uris.isNotEmpty()) {
                        pendingDelete = uris
                        val sender = buildDeleteRequest(uris)
                        if (sender != null) {
                            deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                        } else {
                            pendingDelete = emptyList()
                        }
                    }
                }

                if (granted) {
                    TidyScreen(
                        vm = vm,
                        onOpenStorageSettings = ::openStorageSettings,
                        onEnableDocuments = ::requestAllFilesAccess,
                        onRequestDelete = { items ->
                            // Documents are plain files we delete directly (with a
                            // confirmation, since there's no system sheet for them);
                            // media goes through Android's own delete dialog.
                            val docItems = items.filter { it.filePath != null }
                            val mediaUris = items.filter { it.filePath == null }.map { it.uri }
                            when {
                                docItems.isNotEmpty() -> {
                                    AlertDialog.Builder(this@MainActivity)
                                        .setTitle("Delete ${docItems.size} document(s)?")
                                        .setMessage("This permanently deletes the selected documents from your phone.")
                                        .setPositiveButton("Delete") { _, _ ->
                                            val gone = docItems.filter {
                                                runCatching { File(it.filePath!!).delete() }.getOrDefault(false)
                                            }
                                            vm.onDeleted(gone.map { it.uri })
                                            requestMediaDelete(mediaUris)
                                        }
                                        .setNegativeButton("Cancel", null)
                                        .show()
                                }
                                mediaUris.isNotEmpty() -> requestMediaDelete(mediaUris)
                            }
                        }
                    )
                } else {
                    PermissionGate(onGrant = { permissionLauncher.launch(mediaPermissions()) })
                }
            }
        }
    }

    private fun mediaPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    private fun hasMediaPermission(): Boolean {
        val perms = mediaPermissions()
        return perms.any {
            checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * A normal app can't clear other apps' caches, so instead we send the user
     * to Android's own storage screen, where the system (Samsung Device Care on
     * the A56) does the clearing. We try the most specific intent first and fall
     * back to general Settings if a given ROM doesn't expose it.
     */
    private fun openStorageSettings() {
        val candidates = listOf(
            Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
            Intent("android.settings.STORAGE_MANAGER_SETTINGS"),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in candidates) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                return
            } catch (_: Exception) { /* try the next one */ }
        }
        Toast.makeText(this, "Open Settings → Device care → Storage", Toast.LENGTH_LONG).show()
    }

    /**
     * Opens the system screen where the user grants "All files access", which the
     * document scan needs to reach files outside Downloads. Optional — the app
     * works without it.
     */
    private fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Toast.makeText(this, "Document scanning needs Android 11 or newer", Toast.LENGTH_LONG).show()
            return
        }
        try {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.fromParts("package", packageName, null)
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            try {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
                Toast.makeText(
                    this,
                    "Enable 'All files access' for StorageTidy in Settings → Apps",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * MediaStore.createDeleteRequest shows the standard Android confirmation
     * sheet listing the files. The user — not the app — authorises every delete.
     * This is why the app needs no "All files access" and can't delete silently.
     */
    private fun buildDeleteRequest(uris: List<Uri>): IntentSender? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createDeleteRequest(contentResolver, uris).intentSender
        } else null
    }
}
