package com.termux.app.activities

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.R
import com.termux.app.file.TextFileDetector
import com.termux.app.ui.theme.TermuxTheme
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * A minimal text file viewer/editor backed by the sora-editor [CodeEditor] widget, rendered with
 * Compose. File loading, saving and the persisted wordwrap setting are unchanged from the original
 * implementation; only the toolbar and the host view are Compose.
 */
class FileEditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "com.termux.app.file_editor.EXTRA_FILE_PATH"
        private const val MAX_FILE_SIZE = 8L * 1024 * 1024
        private const val PREF_FILE_EDITOR_SETTINGS = "file_editor_settings"
        private const val KEY_WORDWRAP = "wordwrap"
        private const val DEFAULT_TEXT_SIZE = 14f
    }

    private var editor: CodeEditor? = null
    private var file: File? = null
    private var originalContent = ""
    private var loadedContent: String? = null

    private val editorPrefs: SharedPreferences
        get() = getSharedPreferences(PREF_FILE_EDITOR_SETTINGS, MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val path = intent?.getStringExtra(EXTRA_FILE_PATH)
        if (path == null) {
            Toast.makeText(this, R.string.file_editor_file_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val target = File(path)
        if (!loadFile(target)) return
        file = target

        val title = target.name
        val subtitle = TextFileDetector.detectMimeType(target)
            ?: getString(R.string.file_editor_unknown_type)
        val initialWordwrap = editorPrefs.getBoolean(KEY_WORDWRAP, false)

        setContent {
            TermuxTheme {
                FileEditorScreen(
                    title = title,
                    subtitle = subtitle,
                    initialWordwrap = initialWordwrap,
                    onBack = { onBackPressed() },
                    onSave = { saveFile(true) },
                    onWordwrapChange = { enabled ->
                        editor?.setWordwrap(enabled)
                        editorPrefs.edit().putBoolean(KEY_WORDWRAP, enabled).apply()
                    },
                    onEditorReady = { ed ->
                        editor = ed
                        configureEditor(ed)
                    },
                )
            }
        }
    }

    /** Read the file into memory. Returns false after showing feedback and finishing. */
    private fun loadFile(target: File): Boolean {
        if (!target.isFile) {
            Toast.makeText(this, R.string.file_editor_file_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return false
        }
        if (target.length() > MAX_FILE_SIZE) {
            Toast.makeText(this, R.string.file_editor_file_too_large, Toast.LENGTH_SHORT).show()
            finish()
            return false
        }
        val data = ByteArray(target.length().toInt())
        try {
            FileInputStream(target).use { input ->
                var offset = 0
                while (offset < data.size) {
                    val count = input.read(data, offset, data.size - offset)
                    if (count == -1) break
                    offset += count
                }
                loadedContent = String(data, 0, offset, StandardCharsets.UTF_8)
            }
            originalContent = loadedContent ?: ""
        } catch (e: IOException) {
            Toast.makeText(
                this,
                getString(R.string.file_editor_load_failed, e.message),
                Toast.LENGTH_LONG,
            ).show()
            finish()
            return false
        }
        return true
    }

    /** Apply persisted editor settings and load the file contents into a freshly created editor. */
    private fun configureEditor(ed: CodeEditor) {
        ed.setTypefaceText(Typeface.MONOSPACE)
        ed.setTextSize(DEFAULT_TEXT_SIZE)
        ed.setColorScheme(EditorColorScheme())
        ed.setWordwrap(editorPrefs.getBoolean(KEY_WORDWRAP, false))
        ed.setLineNumberEnabled(true)
        ed.setHighlightCurrentLine(true)
        ed.setHighlightBracketPair(true)
        loadedContent?.let { ed.setText(it) }
    }

    /** Write the editor contents back to disk when they changed. */
    private fun saveFile(showFeedback: Boolean) {
        val ed = editor ?: return
        val target = file ?: return
        val content = ed.text?.toString() ?: return
        if (content == originalContent) {
            if (showFeedback) {
                Toast.makeText(this, R.string.file_editor_no_changes, Toast.LENGTH_SHORT).show()
            }
            return
        }
        try {
            FileOutputStream(target).use { it.write(content.toByteArray(StandardCharsets.UTF_8)) }
            originalContent = content
            if (showFeedback) {
                Toast.makeText(this, R.string.file_editor_saved, Toast.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            Toast.makeText(
                this,
                getString(R.string.file_editor_save_failed, e.message),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    override fun onBackPressed() {
        saveFile(false)
        super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        editor?.release()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileEditorScreen(
    title: String,
    subtitle: String,
    initialWordwrap: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onWordwrapChange: (Boolean) -> Unit,
    onEditorReady: (CodeEditor) -> Unit,
) {
    var wordwrap by remember { mutableStateOf(initialWordwrap) }
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("‹", style = MaterialTheme.typography.headlineMedium)
                    }
                },
                actions = {
                    TextButton(onClick = onSave) {
                        Text(stringResource(R.string.file_editor_action_save))
                    }
                    Box {
                        TextButton(onClick = { menuExpanded = true }) {
                            Text("⋮", style = MaterialTheme.typography.titleLarge)
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.file_editor_settings_wordwrap)) },
                                trailingIcon = { if (wordwrap) Text("✓") },
                                onClick = {
                                    wordwrap = !wordwrap
                                    onWordwrapChange(wordwrap)
                                    menuExpanded = false
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        AndroidView(
            factory = { ctx -> CodeEditor(ctx).also(onEditorReady) },
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}
