package com.termux.app.atomcode;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.io.IndentStyle;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Reads and writes the parts of atomcode's {@code ~/.atomcode/config.toml} that the model
 * management screen edits: the {@code default_provider} / {@code default_model} keys and the
 * {@code [models."<id>"]} / {@code [provider_accounts.<name>]} tables.
 *
 * <p>TOML is handled by NightConfig ({@code TomlParser} / {@code TomlWriter}), so the whole file
 * is parsed and serialized by a full TOML implementation rather than by ad-hoc string handling.
 * Only the keys this screen owns are touched: every other setting — and the comments the file
 * carries — is left in the parsed config and written back untouched.</p>
 *
 * <p>API keys are never copied into {@link AtomCodeAccount}: only whether one is present is
 * exposed, so a key can never reach a list row, a dialog or a log. A key is only set for an
 * account that the user creates in this screen.</p>
 */
public class AtomCodeConfig {

    /** Directory atomcode keeps its user data in. */
    public static final String CONFIG_DIR_PATH = TermuxConstants.TERMUX_HOME_DIR_PATH + "/.atomcode";
    public static final String CONFIG_FILE_PATH = CONFIG_DIR_PATH + "/config.toml";

    /** Install command shown when atomcode is not installed yet. */
    public static final String INSTALL_COMMAND =
        "curl -fsSL https://raw.atomgit.com/atomgit_atomcode/atomcode/raw/main/scripts/install.sh | sh";

    private static final String KEY_DEFAULT_PROVIDER = "default_provider";
    private static final String KEY_DEFAULT_MODEL = "default_model";
    private static final String TABLE_MODELS = "models";
    private static final String TABLE_ACCOUNTS = "provider_accounts";

    /** The file this instance was loaded from. */
    private final File mFile;
    /** The parsed document; everything outside the managed tables is written back from here. */
    private final CommentedConfig mRoot;

    /** Models in config order. */
    public final List<AtomCodeModel> models = new ArrayList<>();
    /** Provider accounts in config order. */
    public final List<AtomCodeAccount> accounts = new ArrayList<>();

    /** Value of the top-level {@code default_provider} key, or an empty string when unset. */
    public String defaultProvider = "";
    /** Value of the top-level {@code default_model} key, or an empty string when unset. */
    public String defaultModel = "";

    private AtomCodeConfig(@NonNull File file, @NonNull CommentedConfig root) {
        mFile = file;
        mRoot = root;
    }

    /** Whether atomcode has written its config file yet. */
    public static boolean configExists() {
        return new File(CONFIG_FILE_PATH).isFile();
    }

    /** Whether the atomcode executable is present in the Termux prefix. */
    public static boolean atomcodeInstalled() {
        return new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/bin/atomcode").exists();
    }

    /**
     * Parse the config file.
     *
     * @throws IOException when the file cannot be read.
     */
    @NonNull
    public static AtomCodeConfig load() throws IOException {
        File file = new File(CONFIG_FILE_PATH);
        // LinkedHashMap keeps the file's entry order, so the screen lists models as configured.
        CommentedConfig root = TomlFormat.newConfig(LinkedHashMap::new);
        if (file.isFile()) {
            try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                new TomlParser().parse(reader, root, ParsingMode.MERGE);
            }
        }

        AtomCodeConfig config = new AtomCodeConfig(file, root);
        config.defaultProvider = stringValue(root, KEY_DEFAULT_PROVIDER);
        config.defaultModel = stringValue(root, KEY_DEFAULT_MODEL);

        Config modelsTable = root.get(TABLE_MODELS);
        if (modelsTable != null) {
            for (UnmodifiableConfig.Entry entry : modelsTable.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Config)
                    config.models.add(parseModel(entry.getKey(), (Config) value));
            }
        }

        Config accountsTable = root.get(TABLE_ACCOUNTS);
        if (accountsTable != null) {
            for (UnmodifiableConfig.Entry entry : accountsTable.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Config)
                    config.accounts.add(parseAccount(entry.getKey(), (Config) value));
            }
        }
        return config;
    }

    /** The account with the given table key, or null. */
    @Nullable
    public AtomCodeAccount findAccount(String name) {
        if (name == null) return null;
        for (AtomCodeAccount account : accounts) {
            if (account.name.equals(name)) return account;
        }
        return null;
    }

    /** The model with the given table key, or null. */
    @Nullable
    public AtomCodeModel findModel(String id) {
        if (id == null) return null;
        for (AtomCodeModel model : models) {
            if (model.id.equals(id)) return model;
        }
        return null;
    }

    /** Whether {@code id} is already used by a model other than {@code except}. */
    public boolean isModelIdTaken(String id, @Nullable AtomCodeModel except) {
        for (AtomCodeModel model : models) {
            if (model != except && model.id.equals(id)) return true;
        }
        return false;
    }

    /** Whether {@code name} is already used by an account. */
    public boolean isAccountNameTaken(String name) {
        return findAccount(name) != null;
    }

    /**
     * Read the API key of an account straight from the parsed document, for the one caller that
     * genuinely needs it (fetching the provider's model list).
     *
     * <p>The key is deliberately not stored on {@link AtomCodeAccount}: it is returned here and
     * never kept, so it cannot leak into a list row, a dialog or a log.</p>
     *
     * @return the key, or null when the account has none configured.
     */
    @Nullable
    public String readAccountApiKey(@NonNull String accountName) {
        Config table = mRoot.get(TABLE_ACCOUNTS);
        if (table == null) return null;
        Object entry = table.get(path(accountName));
        if (!(entry instanceof Config)) return null;
        String key = stringValue((Config) entry, "api_key");
        return key.isEmpty() ? null : key;
    }

    /** Add a new model entry (not yet written to disk). */
    public void addModel(@NonNull AtomCodeModel model) {
        model.isNew = true;
        models.add(model);
    }

    /** Add a new provider account (not yet written to disk). */
    public void addAccount(@NonNull AtomCodeAccount account) {
        account.isNew = true;
        accounts.add(account);
    }

    /** Remove an account from the config (applied on the next {@link #save()}). */
    public void removeAccount(@NonNull AtomCodeAccount account) {
        accounts.remove(account);
    }

    /** The number of models that still reference {@code accountName}. */
    public int countModelsUsingAccount(@NonNull String accountName) {
        int count = 0;
        for (AtomCodeModel model : models) {
            if (accountName.equals(model.account)) count++;
        }
        return count;
    }

    /** Whether the model is the config's default model. */
    public boolean isDefaultModel(@NonNull AtomCodeModel model) {
        return model.id.equals(defaultModel);
    }

    /**
     * Write the config back to disk.
     *
     * <p>The managed tables are rebuilt from {@link #models} / {@link #accounts}, so edits and
     * deletions apply, while every other part of the parsed document is written back as it was
     * read. The file is serialized to a temporary sibling first and then renamed into place, so
     * a failed write cannot leave a truncated config behind.</p>
     *
     * @throws IOException when the file cannot be written.
     */
    public void save() throws IOException {
        if (defaultProvider.isEmpty()) mRoot.remove(KEY_DEFAULT_PROVIDER);
        else mRoot.set(KEY_DEFAULT_PROVIDER, defaultProvider);
        if (defaultModel.isEmpty()) mRoot.remove(KEY_DEFAULT_MODEL);
        else mRoot.set(KEY_DEFAULT_MODEL, defaultModel);

        writeModelsTable();
        writeAccountsTable();
        writeFile(serialize());

        // The file on disk now matches the in-memory state.
        for (AtomCodeModel model : models) model.isNew = false;
        for (AtomCodeAccount account : accounts) {
            account.isNew = false;
            account.hasApiKey = account.willHaveApiKey();
            account.apiKeyToWrite = "";
            account.deleteApiKey = false;
            account.originalName = account.name;
        }
    }

    /** Rebuild the {@code [models.*]} table from {@link #models}. */
    private void writeModelsTable() {
        Config table = mRoot.get(TABLE_MODELS);
        // Nothing to write when there are no models and the file never had a models table:
        // creating an empty one would add a meaningless `models = {}` line on every save.
        if (table == null && models.isEmpty()) return;
        if (table == null) {
            table = mRoot.createSubConfig();
            mRoot.set(TABLE_MODELS, table);
        }

        // Drop entries that no longer exist (a deleted model, or a renamed table key).
        List<String> stale = new ArrayList<>();
        for (UnmodifiableConfig.Entry entry : table.entrySet()) {
            if (findModel(entry.getKey()) == null) stale.add(entry.getKey());
        }
        for (String key : stale) table.remove(path(key));

        for (AtomCodeModel model : models) {
            Config entry = table.get(path(model.id));
            if (entry == null) entry = mRoot.createSubConfig();
            entry.set("account", model.account);
            entry.set("model", model.displayModel());
            if (model.supportsVision) entry.set("supports_vision", Boolean.TRUE);
            else entry.remove("supports_vision");
            if (model.contextWindow != null) entry.set("context_window", model.contextWindow);
            else entry.remove("context_window");
            if (model.reasoningEffortLevels.isEmpty()) {
                entry.remove("reasoning_effort_levels");
            } else {
                entry.set("reasoning_effort_levels", new ArrayList<>(model.reasoningEffortLevels));
            }
            // A list path keeps dots and colons in the id from being read as path separators.
            table.set(path(model.id), entry);
        }
    }

    /**
     * Rewrite the {@code [provider_accounts.*]} tables that changed in this session.
     *
     * <p>Unchanged accounts are left exactly as read, so a key that was never edited is written
     * back byte for byte and never passes through this code. An account is rewritten only when
     * it is new, when the user typed a new key, or when the user asked for the key to be removed;
     * renamed and deleted accounts are handled through {@link AtomCodeAccount#originalName}.</p>
     */
    private void writeAccountsTable() {
        Config table = getOrCreateTable(TABLE_ACCOUNTS);

        // Drop tables whose account was deleted or renamed away.
        List<String> stale = new ArrayList<>();
        for (UnmodifiableConfig.Entry entry : table.entrySet()) {
            if (findAccount(entry.getKey()) == null) stale.add(entry.getKey());
        }
        for (String key : stale) table.remove(path(key));

        for (AtomCodeAccount account : accounts) {
            boolean needsRewrite = account.hasPendingWrite();
            if (!needsRewrite) continue;

            // Start from the existing table so keys this screen does not manage survive the edit.
            Config entry = table.get(path(account.originalName != null ? account.originalName : account.name));
            if (entry == null) entry = mRoot.createSubConfig();
            entry.set("provider", account.provider);
            entry.set("base_url", account.baseUrl);
            if (account.deleteApiKey) entry.remove("api_key");
            else if (!account.apiKeyToWrite.isEmpty()) entry.set("api_key", account.apiKeyToWrite);

            // A list path keeps dots in the name from being read as path separators.
            table.set(path(account.name), entry);
        }
    }

    /** The named sub-table, creating an empty one when the config does not have it yet. */
    @NonNull
    private Config getOrCreateTable(@NonNull String name) {
        Config table = mRoot.get(name);
        if (table == null) {
            table = mRoot.createSubConfig();
            mRoot.set(name, table);
        }
        return table;
    }

    /** Serialize the parsed document as TOML, matching atomcode's own formatting. */
    @NonNull
    private String serialize() {
        TomlWriter writer = new TomlWriter();
        writer.setIndent(IndentStyle.NONE);
        writer.setNewline("\n");
        return writer.writeToString(mRoot);
    }

    /** Write {@code content} to the config file through a temporary sibling. */
    private void writeFile(@NonNull String content) throws IOException {
        File parent = mFile.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs())
            throw new IOException("Cannot create " + parent.getAbsolutePath());

        File temp = new File(mFile.getAbsolutePath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp);
             Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            writer.write(content);
            writer.flush();
            out.getFD().sync();
        }
        if (!temp.renameTo(mFile)) {
            // Rename can fail when the target already exists on some filesystems.
            if (!mFile.delete() || !temp.renameTo(mFile)) {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
                throw new IOException("Cannot replace " + mFile.getAbsolutePath());
            }
        }
    }

    /** Single-element path, so a key containing dots is not split into nested tables. */
    @NonNull
    private static List<String> path(@NonNull String key) {
        return Collections.singletonList(key);
    }

    @NonNull
    private static String stringValue(@NonNull Config config, @NonNull String key) {
        Object value = config.get(key);
        return value instanceof CharSequence ? value.toString() : "";
    }

    @NonNull
    private static AtomCodeModel parseModel(@NonNull String id, @NonNull Config entry) {
        AtomCodeModel model = new AtomCodeModel(id);
        model.originalId = id;
        model.account = stringValue(entry, "account");
        model.model = stringValue(entry, "model");
        if (model.model.isEmpty()) model.model = id;
        Object contextWindow = entry.get("context_window");
        model.contextWindow = contextWindow instanceof Number ? ((Number) contextWindow).intValue() : null;
        Object supportsVision = entry.get("supports_vision");
        model.supportsVision = supportsVision instanceof Boolean && (Boolean) supportsVision;
        Object levels = entry.get("reasoning_effort_levels");
        if (levels instanceof Iterable) {
            for (Object level : (Iterable<?>) levels) {
                if (level != null) model.reasoningEffortLevels.add(level.toString());
            }
        }
        return model;
    }

    @NonNull
    private static AtomCodeAccount parseAccount(@NonNull String name, @NonNull Config entry) {
        AtomCodeAccount account = new AtomCodeAccount(name);
        account.originalName = name;
        account.provider = stringValue(entry, "provider");
        account.baseUrl = stringValue(entry, "base_url");
        account.hasApiKey = !stringValue(entry, "api_key").isEmpty();
        return account;
    }
}
