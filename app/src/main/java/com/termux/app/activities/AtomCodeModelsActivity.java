package com.termux.app.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;
import com.termux.app.atomcode.AtomCodeAccount;
import com.termux.app.atomcode.AtomCodeConfig;
import com.termux.app.atomcode.AtomCodeModel;
import com.termux.app.atomcode.AtomCodeModelFetcher;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.theme.NightMode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Manage the models of atomcode in a single grouped screen.
 *
 * <p>The list is grouped by provider account: each account is a header row (name, protocol, key
 * state, model count, with a manage action) followed by the models that belong to it, so a
 * model is always shown under the account that serves it instead of in a flat list. Models whose
 * account is missing from the config are collected under a trailing group.</p>
 *
 * <p>Adding an account immediately pulls that provider's model list, so a new account turns into
 * usable models in one step. Everything is written straight back through {@link AtomCodeConfig},
 * which only regenerates the tables this screen owns.</p>
 */
public class AtomCodeModelsActivity extends AppCompatActivity {

    private static final String LOG_TAG = "AtomCodeModelsActivity";

    /** One row of the grouped list: either an account header or a model. */
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_MODEL = 1;

    private AtomCodeConfig mConfig;
    private GroupedAdapter mAdapter;
    private RecyclerView mListView;
    private TextView mEmptyView;

    /** Flattened rows rebuilt from the config on every refresh. */
    private final List<Row> mRows = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);
        setContentView(R.layout.activity_atomcode_models);

        AppCompatActivityUtils.setToolbar(this, com.termux.shared.R.id.toolbar);
        AppCompatActivityUtils.setShowBackButtonInActionBar(this, true);
        Toolbar toolbar = findViewById(com.termux.shared.R.id.toolbar);
        toolbar.setTitle(R.string.title_activity_atomcode_models);

        mListView = findViewById(R.id.atomcode_list);
        mEmptyView = findViewById(R.id.atomcode_empty);
        mListView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new GroupedAdapter();
        mListView.setAdapter(mAdapter);

        // The actions are page buttons rather than toolbar menu items: this screen already uses
        // its Toolbar as the support action bar, which takes over toolbar menus.
        findViewById(R.id.atomcode_add_model_button)
            .setOnClickListener(v -> showModelEditDialog(null));
        findViewById(R.id.atomcode_add_account_button)
            .setOnClickListener(v -> showAccountEditDialog(null));

        View copyButton = findViewById(R.id.atomcode_notice_copy_button);
        copyButton.setOnClickListener(v -> copyInstallCommand());
    }

    @Override
    public boolean onSupportNavigateUp() {
        // The toolbar home arrow only navigates once this is handled; otherwise tapping it does
        // nothing at all (setShowBackButtonInActionBar merely shows the arrow).
        onBackPressed();
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload on every resume so changes made in the terminal show up without a restart.
        loadConfig();
    }

    /** Load the config and refresh the screen, showing the install/config notice when needed. */
    private void loadConfig() {
        if (!AtomCodeConfig.configExists()) {
            mConfig = null;
            showNotice(AtomCodeConfig.atomcodeInstalled()
                ? getString(R.string.atomcode_config_missing)
                : getString(R.string.atomcode_not_installed), true);
            refreshList();
            return;
        }
        try {
            mConfig = AtomCodeConfig.load();
        } catch (IOException | RuntimeException e) {
            Logger.logError(LOG_TAG, "Failed to read atomcode config: " + e.getMessage());
            mConfig = null;
            showNotice(getString(R.string.msg_atomcode_config_read_failed, e.getMessage()), false);
            refreshList();
            return;
        }
        showNotice(null, false);
        refreshList();
    }

    /**
     * Show (or hide) the notice card above the list.
     *
     * @param message The message to show; null hides the card.
     * @param showCommand Whether to show the install command and its copy button.
     */
    private void showNotice(@Nullable String message, boolean showCommand) {
        View card = findViewById(R.id.atomcode_notice_card);
        if (message == null) {
            card.setVisibility(View.GONE);
            return;
        }
        card.setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.atomcode_notice_text)).setText(message);

        TextView commandView = findViewById(R.id.atomcode_notice_command);
        View copyButton = findViewById(R.id.atomcode_notice_copy_button);
        commandView.setText(AtomCodeConfig.INSTALL_COMMAND);
        commandView.setVisibility(showCommand ? View.VISIBLE : View.GONE);
        copyButton.setVisibility(showCommand ? View.VISIBLE : View.GONE);
    }

    /** Copy the install command so the user can paste it into a terminal. */
    private void copyInstallCommand() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("atomcode install", AtomCodeConfig.INSTALL_COMMAND));
        Toast.makeText(this, R.string.msg_atomcode_install_command_copied, Toast.LENGTH_SHORT).show();
    }

    /** Rebuild the grouped rows from the loaded config. */
    private void refreshList() {
        mRows.clear();
        if (mConfig != null) {
            for (AtomCodeAccount account : mConfig.accounts) {
                mRows.add(Row.header(account));
                for (AtomCodeModel model : mConfig.models) {
                    if (account.name.equals(model.account)) mRows.add(Row.model(model));
                }
            }

            // Models pointing at an account that is not in the config stay visible, otherwise they
            // would silently disappear from the screen while still being written to the file.
            List<AtomCodeModel> orphans = new ArrayList<>();
            for (AtomCodeModel model : mConfig.models) {
                if (mConfig.findAccount(model.account) == null) orphans.add(model);
            }
            if (!orphans.isEmpty()) {
                mRows.add(Row.header(null));
                for (AtomCodeModel model : orphans) mRows.add(Row.model(model));
            }
        }

        boolean hasRows = !mRows.isEmpty();
        mListView.setVisibility(hasRows ? View.VISIBLE : View.GONE);
        // Without a readable config the notice card already explains the situation.
        mEmptyView.setVisibility(hasRows || mConfig == null ? View.GONE : View.VISIBLE);
        mAdapter.notifyDataSetChanged();
    }

    /** Persist the config, reporting any failure instead of silently losing the edit. */
    private boolean saveConfig() {
        if (mConfig == null) return false;
        try {
            mConfig.save();
            return true;
        } catch (IOException | RuntimeException e) {
            Logger.logError(LOG_TAG, "Failed to write atomcode config: " + e.getMessage());
            Toast.makeText(this, getString(R.string.msg_atomcode_model_save_failed, e.getMessage()),
                Toast.LENGTH_LONG).show();
            return false;
        }
    }

    /** Make {@code model} the default model of the config. */
    private void setDefaultModel(@NonNull AtomCodeModel model) {
        if (mConfig == null) return;
        mConfig.defaultModel = model.id;
        mConfig.defaultProvider = model.id;
        if (!saveConfig()) return;
        Toast.makeText(this, getString(R.string.msg_atomcode_default_set, model.id), Toast.LENGTH_SHORT).show();
        mAdapter.notifyDataSetChanged();
    }

    /** Ask for confirmation, then delete {@code model} from the config. */
    private void confirmDeleteModel(@NonNull AtomCodeModel model) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.action_delete_atomcode_model)
            .setMessage(getString(R.string.msg_confirm_delete_atomcode_model, model.id))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_delete_atomcode_model, (dialog, which) -> {
                if (mConfig == null) return;
                mConfig.models.remove(model);
                // Drop the default pointing at a model that no longer exists.
                if (mConfig.isDefaultModel(model)) {
                    mConfig.defaultModel = "";
                    mConfig.defaultProvider = "";
                }
                if (!saveConfig()) return;
                Toast.makeText(this, R.string.msg_atomcode_model_deleted, Toast.LENGTH_SHORT).show();
                refreshList();
            })
            .show();
    }

    /** Show the per-model actions: set default, edit or delete. */
    private void showModelActions(@NonNull AtomCodeModel model) {
        List<String> actions = new ArrayList<>();
        actions.add(getString(R.string.action_set_default_model));
        actions.add(getString(R.string.action_edit_atomcode_model));
        actions.add(getString(R.string.action_delete_atomcode_model));

        new AlertDialog.Builder(this)
            .setTitle(model.id)
            .setItems(actions.toArray(new String[0]), (dialog, which) -> {
                if (which == 0) setDefaultModel(model);
                else if (which == 1) showModelEditDialog(model);
                else confirmDeleteModel(model);
            })
            .show();
    }

    /** Show the actions of an account header: pull models, edit or delete the account. */
    private void showAccountActions(@NonNull AtomCodeAccount account) {
        List<String> actions = new ArrayList<>();
        actions.add(getString(R.string.action_fetch_atomcode_models));
        actions.add(getString(R.string.action_edit_atomcode_account));
        actions.add(getString(R.string.action_delete_atomcode_account));

        new AlertDialog.Builder(this)
            .setTitle(account.name)
            .setItems(actions.toArray(new String[0]), (dialog, which) -> {
                if (which == 0) fetchModelsForAccount(account);
                else if (which == 1) showAccountEditDialog(account);
                else confirmDeleteAccount(account);
            })
            .show();
    }

    /** Ask for confirmation, then delete {@code account}; refuse while models still use it. */
    private void confirmDeleteAccount(@NonNull AtomCodeAccount account) {
        if (mConfig == null) return;

        int usedBy = mConfig.countModelsUsingAccount(account.name);
        if (usedBy > 0) {
            Toast.makeText(this, getString(R.string.msg_atomcode_account_in_use, account.name, usedBy),
                Toast.LENGTH_LONG).show();
            return;
        }

        new AlertDialog.Builder(this)
            .setTitle(R.string.action_delete_atomcode_account)
            .setMessage(getString(R.string.msg_confirm_delete_atomcode_account, account.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_delete_atomcode_account, (dialog, which) -> {
                mConfig.removeAccount(account);
                if (!saveConfig()) return;
                Toast.makeText(this, R.string.msg_atomcode_account_deleted, Toast.LENGTH_SHORT).show();
                refreshList();
            })
            .show();
    }

    /**
     * Pull the model list of {@code account} on a background thread, then let the user pick which
     * ones to add.
     *
     * <p>The account's API key is read for this one request only and is never logged.</p>
     */
    private void fetchModelsForAccount(@NonNull AtomCodeAccount account) {
        if (mConfig == null) return;
        final String accountName = account.name;
        final String baseUrl = account.baseUrl;
        final String apiKey = mConfig.readAccountApiKey(accountName);
        final String provider = account.provider;

        Toast.makeText(this, getString(R.string.msg_fetch_atomcode_running, accountName),
            Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            List<String> ids = null;
            String error = null;
            try {
                // Rebuild a minimal account for the request so no config state crosses threads.
                AtomCodeAccount request = new AtomCodeAccount(accountName);
                request.baseUrl = baseUrl;
                request.provider = provider;
                ids = AtomCodeModelFetcher.fetchModelIds(request, apiKey);
            } catch (IOException | RuntimeException e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }

            final List<String> fetched = ids;
            final String failure = error;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (failure != null) {
                    Toast.makeText(this, getString(R.string.msg_fetch_atomcode_failed, failure),
                        Toast.LENGTH_LONG).show();
                    return;
                }
                showFetchModelsSelectionDialog(accountName, fetched);
            });
        }, "atomcode-fetch-models").start();
    }

    /**
     * Show the fetched models as a multi-select list; models already in the config are skipped so
     * the same entry is never added twice.
     */
    private void showFetchModelsSelectionDialog(@NonNull String accountName, @Nullable List<String> fetchedIds) {
        if (mConfig == null || fetchedIds == null) return;

        final List<String> addable = new ArrayList<>();
        for (String id : fetchedIds) {
            if (mConfig.findModel(id) != null) continue;
            addable.add(id);
        }
        if (addable.isEmpty()) {
            Toast.makeText(this, getString(R.string.msg_fetch_atomcode_empty_new, accountName),
                Toast.LENGTH_LONG).show();
            return;
        }

        CharSequence[] labels = addable.toArray(new CharSequence[0]);
        boolean[] checked = new boolean[addable.size()];

        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.title_fetch_atomcode_pick, addable.size()))
            .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_fetch_atomcode_import, (dialog, which) -> {
                int added = 0;
                for (int i = 0; i < addable.size(); i++) {
                    if (!checked[i]) continue;
                    AtomCodeModel model = new AtomCodeModel(addable.get(i));
                    model.account = accountName;
                    model.model = addable.get(i);
                    mConfig.addModel(model);
                    added++;
                }
                if (added == 0) return;
                if (!saveConfig()) {
                    // Roll back so the list keeps matching the file on disk.
                    mConfig.models.removeIf(model -> model.isNew);
                    return;
                }
                Toast.makeText(this, getString(R.string.msg_fetch_atomcode_imported, added),
                    Toast.LENGTH_SHORT).show();
                refreshList();
            })
            .show();
    }

    /**
     * Show the add/edit dialog for a provider account.
     *
     * @param account The account to edit, or null to add a new one.
     */
    private void showAccountEditDialog(@Nullable AtomCodeAccount account) {
        if (mConfig == null) {
            Toast.makeText(this, R.string.atomcode_config_missing, Toast.LENGTH_SHORT).show();
            return;
        }

        View content = LayoutInflater.from(this).inflate(R.layout.dialog_atomcode_account_edit, null, false);
        EditText nameInput = content.findViewById(R.id.atomcode_account_edit_name);
        EditText providerInput = content.findViewById(R.id.atomcode_account_edit_provider);
        EditText baseUrlInput = content.findViewById(R.id.atomcode_account_edit_base_url);
        EditText apiKeyInput = content.findViewById(R.id.atomcode_account_edit_api_key);
        CheckBox clearKeyCheckBox = content.findViewById(R.id.atomcode_account_edit_clear_key);

        final boolean isNew = account == null;
        if (isNew) {
            apiKeyInput.setHint(R.string.atomcode_account_api_key_hint);
        } else {
            nameInput.setText(account.name);
            providerInput.setText(account.provider);
            baseUrlInput.setText(account.baseUrl);
            // The stored key is never shown; leaving the field empty keeps it.
            apiKeyInput.setHint(R.string.atomcode_account_api_key_new_hint);
            clearKeyCheckBox.setVisibility(account.hasApiKey ? View.VISIBLE : View.GONE);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(isNew ? R.string.title_add_atomcode_account : R.string.title_edit_atomcode_account)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
            .create();
        // Keep the dialog open when validation fails, so the input can be fixed in place.
        dialog.setOnShowListener(ignored ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (applyAccountEdit(account, nameInput, providerInput, baseUrlInput, apiKeyInput,
                    clearKeyCheckBox)) {
                    dialog.dismiss();
                }
            }));
        dialog.show();
    }

    /**
     * Validate the account dialog inputs and apply them to the config.
     *
     * <p>When a new account was created, its model list is pulled right away so the account is
     * usable without a separate fetch step.</p>
     *
     * @return true when the edit was saved (so the dialog can close).
     */
    private boolean applyAccountEdit(@Nullable AtomCodeAccount account,
                                     @NonNull EditText nameInput,
                                     @NonNull EditText providerInput,
                                     @NonNull EditText baseUrlInput,
                                     @NonNull EditText apiKeyInput,
                                     @NonNull CheckBox clearKeyCheckBox) {
        if (mConfig == null) return false;

        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.msg_atomcode_account_name_required, Toast.LENGTH_SHORT).show();
            return false;
        }
        // A renamed account may keep its own name; any other duplicate is rejected.
        AtomCodeAccount existing = mConfig.findAccount(name);
        if (existing != null && existing != account) {
            Toast.makeText(this, getString(R.string.msg_atomcode_account_name_exists, name),
                Toast.LENGTH_SHORT).show();
            return false;
        }

        String newApiKey = apiKeyInput.getText().toString().trim();

        final boolean created = account == null;
        AtomCodeAccount target = account;
        if (target == null) {
            target = new AtomCodeAccount(name);
            mConfig.addAccount(target);
        }
        target.name = name;
        target.provider = emptyToDefault(providerInput.getText().toString().trim(), "openai-compatible");
        target.baseUrl = baseUrlInput.getText().toString().trim();
        // Only touch the key when the user actually asked for a change.
        if (!newApiKey.isEmpty()) {
            target.apiKeyToWrite = newApiKey;
            target.deleteApiKey = false;
        } else if (clearKeyCheckBox.isChecked()) {
            target.apiKeyToWrite = "";
            target.deleteApiKey = true;
        }

        if (!saveConfig()) {
            if (created) mConfig.accounts.remove(target);
            return false;
        }
        Toast.makeText(this, R.string.msg_atomcode_account_saved, Toast.LENGTH_SHORT).show();
        refreshList();

        // A brand new account is only useful once it has models, so pull them immediately.
        if (created && !target.baseUrl.isEmpty()) fetchModelsForAccount(target);
        return true;
    }

    /**
     * Show the add/edit dialog for a model.
     *
     * @param model The model to edit, or null to add a new one.
     */
    private void showModelEditDialog(@Nullable AtomCodeModel model) {
        if (mConfig == null) {
            Toast.makeText(this, R.string.atomcode_config_missing, Toast.LENGTH_SHORT).show();
            return;
        }

        View content = LayoutInflater.from(this).inflate(R.layout.dialog_atomcode_model_edit, null, false);
        EditText idInput = content.findViewById(R.id.atomcode_edit_model_id);
        AutoCompleteTextView accountInput = content.findViewById(R.id.atomcode_edit_account);
        EditText modelNameInput = content.findViewById(R.id.atomcode_edit_model_name);
        EditText contextWindowInput = content.findViewById(R.id.atomcode_edit_context_window);
        EditText reasoningInput = content.findViewById(R.id.atomcode_edit_reasoning_levels);
        CheckBox visionCheckBox = content.findViewById(R.id.atomcode_edit_vision);

        // Only account names are offered; API keys are never read from the config.
        List<String> accountNames = new ArrayList<>();
        for (AtomCodeAccount account : mConfig.accounts) accountNames.add(account.name);
        accountInput.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, accountNames));

        final boolean isNew = model == null;
        if (!isNew) {
            idInput.setText(model.id);
            accountInput.setText(model.account, false);
            modelNameInput.setText(model.displayModel());
            contextWindowInput.setText(model.contextWindow == null ? "" : String.valueOf(model.contextWindow));
            reasoningInput.setText(TextUtils.join(", ", model.reasoningEffortLevels));
            visionCheckBox.setChecked(model.supportsVision);
        } else if (!accountNames.isEmpty()) {
            accountInput.setText(accountNames.get(0), false);
        }
        // Renaming the table key of a model in use is confusing; keep the ID fixed when editing.
        idInput.setEnabled(isNew);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(isNew ? R.string.title_add_atomcode_model : R.string.title_edit_atomcode_model)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
            .create();
        // Keep the dialog open when validation fails, so the user can fix the input in place.
        dialog.setOnShowListener(ignored ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (applyModelEdit(model, idInput, accountInput, modelNameInput, contextWindowInput,
                    reasoningInput, visionCheckBox)) {
                    dialog.dismiss();
                }
            }));
        dialog.show();
    }

    /**
     * Validate the model dialog inputs and apply them to the config.
     *
     * @return true when the edit was saved (so the dialog can close).
     */
    private boolean applyModelEdit(@Nullable AtomCodeModel model,
                                   @NonNull EditText idInput,
                                   @NonNull AutoCompleteTextView accountInput,
                                   @NonNull EditText modelNameInput,
                                   @NonNull EditText contextWindowInput,
                                   @NonNull EditText reasoningInput,
                                   @NonNull CheckBox visionCheckBox) {
        if (mConfig == null) return false;

        String id = idInput.getText().toString().trim();
        if (id.isEmpty()) {
            Toast.makeText(this, R.string.msg_atomcode_model_id_required, Toast.LENGTH_SHORT).show();
            return false;
        }
        if (mConfig.isModelIdTaken(id, model)) {
            Toast.makeText(this, getString(R.string.msg_atomcode_model_id_exists, id), Toast.LENGTH_SHORT).show();
            return false;
        }

        String accountName = accountInput.getText().toString().trim();
        if (accountName.isEmpty() || mConfig.findAccount(accountName) == null) {
            Toast.makeText(this, R.string.msg_atomcode_account_required, Toast.LENGTH_SHORT).show();
            return false;
        }

        Integer contextWindow = null;
        String contextText = contextWindowInput.getText().toString().trim();
        if (!contextText.isEmpty()) {
            try {
                contextWindow = Integer.valueOf(contextText);
            } catch (NumberFormatException e) {
                Toast.makeText(this, R.string.atomcode_model_context_hint, Toast.LENGTH_SHORT).show();
                return false;
            }
        }

        AtomCodeModel target = model;
        if (target == null) {
            target = new AtomCodeModel();
            mConfig.addModel(target);
        }
        target.id = id;
        target.account = accountName;
        target.model = emptyToDefault(modelNameInput.getText().toString().trim(), id);
        target.contextWindow = contextWindow;
        target.supportsVision = visionCheckBox.isChecked();
        target.reasoningEffortLevels.clear();
        for (String level : reasoningInput.getText().toString().split(",")) {
            String trimmed = level.trim();
            if (!trimmed.isEmpty()) target.reasoningEffortLevels.add(trimmed);
        }

        if (!saveConfig()) {
            // Roll the in-memory change back so the list keeps matching the file on disk.
            if (model == null) mConfig.models.remove(target);
            return false;
        }
        Toast.makeText(this, R.string.msg_atomcode_model_saved, Toast.LENGTH_SHORT).show();
        refreshList();
        return true;
    }

    @NonNull
    private static String emptyToDefault(@NonNull String value, @NonNull String fallback) {
        return value.isEmpty() ? fallback : value;
    }

    /** One row of the grouped list. */
    private static class Row {

        final int type;
        /** Set for header rows; null for the trailing "unknown account" group. */
        @Nullable final AtomCodeAccount account;
        /** Set for model rows. */
        @Nullable final AtomCodeModel model;

        private Row(int type, @Nullable AtomCodeAccount account, @Nullable AtomCodeModel model) {
            this.type = type;
            this.account = account;
            this.model = model;
        }

        static Row header(@Nullable AtomCodeAccount account) {
            return new Row(TYPE_HEADER, account, null);
        }

        static Row model(@NonNull AtomCodeModel model) {
            return new Row(TYPE_MODEL, null, model);
        }
    }

    /** Adapter rendering account headers and their models in one list. */
    private class GroupedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public int getItemViewType(int position) {
            return mRows.get(position).type;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_HEADER) {
                return new HeaderViewHolder(
                    inflater.inflate(R.layout.atomcode_account_group_header, parent, false));
            }
            return new ModelViewHolder(
                inflater.inflate(R.layout.atomcode_model_list_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Row row = mRows.get(position);
            if (row.type == TYPE_HEADER) {
                bindHeader((HeaderViewHolder) holder, row.account);
            } else {
                bindModel((ModelViewHolder) holder, row.model);
            }
        }

        private void bindHeader(@NonNull HeaderViewHolder holder, @Nullable AtomCodeAccount account) {
            if (account == null) {
                // Models whose account is not in the config any more.
                holder.nameView.setText(R.string.atomcode_group_unknown_account);
                holder.summaryView.setText(R.string.atomcode_group_unknown_account_summary);
                holder.manageButton.setVisibility(View.GONE);
                return;
            }

            holder.nameView.setText(account.name);
            List<String> details = new ArrayList<>();
            if (!account.provider.isEmpty()) details.add(account.provider);
            details.add(getString(account.willHaveApiKey()
                ? R.string.atomcode_account_key_configured
                : R.string.atomcode_account_key_missing));
            int usedBy = mConfig == null ? 0 : mConfig.countModelsUsingAccount(account.name);
            details.add(getString(R.string.atomcode_account_models_count, usedBy));
            holder.summaryView.setText(TextUtils.join(" · ", details));
            holder.manageButton.setVisibility(View.VISIBLE);
            holder.manageButton.setOnClickListener(v -> showAccountActions(account));
        }

        private void bindModel(@NonNull ModelViewHolder holder, @Nullable AtomCodeModel model) {
            if (model == null) return;
            holder.idView.setText(model.id);

            List<String> details = new ArrayList<>();
            if (model.contextWindow != null) details.add(String.valueOf(model.contextWindow));
            if (!model.reasoningEffortLevels.isEmpty())
                details.add(TextUtils.join("/", model.reasoningEffortLevels));
            holder.summaryView.setText(TextUtils.join(" · ", details));

            boolean isDefault = mConfig != null && mConfig.isDefaultModel(model);
            holder.badgeView.setVisibility(isDefault ? View.VISIBLE : View.GONE);
            holder.itemView.setOnClickListener(v -> showModelActions(model));
        }

        @Override
        public int getItemCount() {
            return mRows.size();
        }
    }

    /** View holder for an account header row. */
    private static class HeaderViewHolder extends RecyclerView.ViewHolder {

        final TextView nameView;
        final TextView summaryView;
        final View manageButton;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            nameView = itemView.findViewById(R.id.atomcode_group_name);
            summaryView = itemView.findViewById(R.id.atomcode_group_summary);
            manageButton = itemView.findViewById(R.id.atomcode_group_manage);
        }
    }

    /** View holder for a model row. */
    private static class ModelViewHolder extends RecyclerView.ViewHolder {

        final TextView idView;
        final TextView summaryView;
        final TextView badgeView;

        ModelViewHolder(@NonNull View itemView) {
            super(itemView);
            idView = itemView.findViewById(R.id.atomcode_model_id);
            summaryView = itemView.findViewById(R.id.atomcode_model_summary);
            badgeView = itemView.findViewById(R.id.atomcode_model_default_badge);
        }
    }
}
