package com.termux.app.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Menu;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.termux.R;
import com.termux.app.file.TextFileDetector;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;

/**
 * A minimal text file viewer/editor backed by the sora-editor {@link CodeEditor} widget.
 *
 * <p>Launch with {@link #EXTRA_FILE_PATH} set to the absolute path of the file to open.
 * The toolbar shows the file name and its MIME type detected from the real content
 * (see {@link TextFileDetector}). The file is saved back when the save action is used or
 * when the activity is closed with modifications. The only user setting is wordwrap,
 * toggled from the toolbar menu and persisted in a private {@link SharedPreferences};
 * all other editor options use defaults.</p>
 */
public class FileEditorActivity extends AppCompatActivity {

    public static final String EXTRA_FILE_PATH = "com.termux.app.file_editor.EXTRA_FILE_PATH";

    /** Refuse to load files larger than this to avoid OOM in the editor. */
    private static final long MAX_FILE_SIZE = 8L * 1024 * 1024;

    private static final String PREF_FILE_EDITOR_SETTINGS = "file_editor_settings";
    private static final String KEY_WORDWRAP = "wordwrap";

    private static final float DEFAULT_TEXT_SIZE = 14f;

    private File mFile;
    private CodeEditor mEditor;
    private String mOriginalContent = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_editor);

        Intent intent = getIntent();
        String path = intent == null ? null : intent.getStringExtra(EXTRA_FILE_PATH);
        if (path == null) {
            Toast.makeText(this, R.string.file_editor_file_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        mFile = new File(path);

        Toolbar toolbar = findViewById(R.id.file_editor_toolbar);
        toolbar.setTitle(mFile.getName());
        toolbar.setSubtitle(getFileTypeLabel());
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.file_editor_menu);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_save) {
                saveFile(true);
                return true;
            } else if (item.getItemId() == R.id.action_wordwrap) {
                boolean checked = !item.isChecked();
                item.setChecked(checked);
                mEditor.setWordwrap(checked);
                getSharedPreferences(PREF_FILE_EDITOR_SETTINGS, MODE_PRIVATE)
                    .edit().putBoolean(KEY_WORDWRAP, checked).apply();
                return true;
            }
            return false;
        });

        // Reflect the persisted wordwrap setting in the menu item.
        Menu menu = toolbar.getMenu();
        menu.findItem(R.id.action_wordwrap).setChecked(
            getSharedPreferences(PREF_FILE_EDITOR_SETTINGS, MODE_PRIVATE).getBoolean(KEY_WORDWRAP, false));

        mEditor = findViewById(R.id.file_editor);
        mEditor.setTypefaceText(Typeface.MONOSPACE);
        applyEditorSettings();

        loadFile();
    }

    /**
     * Label for the toolbar subtitle: the MIME type detected from the real content of the file.
     */
    private String getFileTypeLabel() {
        String mime = TextFileDetector.detectMimeType(mFile);
        return mime != null ? mime : getString(R.string.file_editor_unknown_type);
    }

    /**
     * Load the file content into the editor, preserving the exact bytes decoded as UTF-8.
     */
    private void loadFile() {
        if (!mFile.isFile()) {
            Toast.makeText(this, R.string.file_editor_file_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (mFile.length() > MAX_FILE_SIZE) {
            Toast.makeText(this, R.string.file_editor_file_too_large, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        long length = mFile.length();
        byte[] data = new byte[(int) length];
        try (FileInputStream in = new FileInputStream(mFile)) {
            int offset = 0;
            while (offset < data.length) {
                int count = in.read(data, offset, data.length - offset);
                if (count == -1) break;
                offset += count;
            }
            mOriginalContent = new String(data, 0, offset, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Toast.makeText(this, getString(R.string.file_editor_load_failed, e.getMessage()), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        mEditor.setText(mOriginalContent);
    }

    /**
     * Write the current editor content back to the file.
     *
     * @param showFeedback Whether to show a toast about the save result.
     */
    private void saveFile(boolean showFeedback) {
        String content = mEditor.getText().toString();
        if (content.equals(mOriginalContent)) {
            if (showFeedback) Toast.makeText(this, R.string.file_editor_no_changes, Toast.LENGTH_SHORT).show();
            return;
        }
        try (FileOutputStream out = new FileOutputStream(mFile)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
            mOriginalContent = content;
            if (showFeedback) Toast.makeText(this, R.string.file_editor_saved, Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, getString(R.string.file_editor_save_failed, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Apply the persisted editor settings to the {@link CodeEditor} widget. Wordwrap is the
     * only user setting; all other options use their default values.
     */
    private void applyEditorSettings() {
        SharedPreferences prefs = getSharedPreferences(PREF_FILE_EDITOR_SETTINGS, MODE_PRIVATE);
        mEditor.setTextSize(DEFAULT_TEXT_SIZE);
        mEditor.setColorScheme(new EditorColorScheme());
        mEditor.setWordwrap(prefs.getBoolean(KEY_WORDWRAP, false));
        mEditor.setLineNumberEnabled(true);
        mEditor.setHighlightCurrentLine(true);
        mEditor.setHighlightBracketPair(true);
    }

    @Override
    public void onBackPressed() {
        saveFile(false);
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mEditor != null) {
            mEditor.release();
        }
    }
}
