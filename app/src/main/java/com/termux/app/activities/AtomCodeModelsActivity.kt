package com.termux.app.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.termux.R
import com.termux.app.atomcode.AtomCodeAccount
import com.termux.app.atomcode.AtomCodeConfig
import com.termux.app.atomcode.AtomCodeModel
import com.termux.app.atomcode.AtomCodeModelFetcher
import com.termux.app.ui.theme.TermuxTheme
import com.termux.shared.activity.media.AppCompatActivityUtils
import com.termux.shared.logger.Logger
import com.termux.shared.theme.NightMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AtomCode model management, rendered with Compose.
 *
 * This is a UI-only rewrite: all configuration parsing, writing and provider fetching still go
 * through the Java [AtomCodeConfig] / [AtomCodeModelFetcher] layer, so the file format and the
 * "never keep the API key in memory" rule stay exactly as before.
 */
class AtomCodeModelsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true)
        setContent {
            TermuxTheme {
                AtomCodeModelsScreen(onBack = { onBackPressed() })
            }
        }
    }
}

/** One row of the grouped list: either an account header or a model under it. */
private sealed interface ListRow {
    data class Header(val account: AtomCodeAccount?) : ListRow
    data class Model(val model: AtomCodeModel) : ListRow
}

/** The banner shown when there is no usable config (atomcode missing or config unreadable). */
private data class Notice(val message: String, val showCommand: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AtomCodeModelsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var config by remember { mutableStateOf<AtomCodeConfig?>(null) }
    var notice by remember { mutableStateOf<Notice?>(null) }
    // Java config objects are mutated in place; bumping this forces the list to be rebuilt.
    var revision by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<DialogState>(DialogState.None) }
    // A fetched model list is shown as its own layer, so the account dialog can stay open behind it.
    var fetchPick by remember { mutableStateOf<FetchPickRequest?>(null) }

    fun bump() {
        revision++
    }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun save(): Boolean {
        val cfg = config ?: return false
        return try {
            cfg.save()
            true
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Failed to write atomcode config: ${e.message}")
            Toast.makeText(
                context,
                context.getString(R.string.msg_atomcode_model_save_failed, e.message ?: ""),
                Toast.LENGTH_LONG,
            ).show()
            false
        }
    }

    fun loadConfig() {
        if (!AtomCodeConfig.configExists()) {
            config = null
            val message = if (AtomCodeConfig.atomcodeInstalled())
                context.getString(R.string.atomcode_config_missing)
            else
                context.getString(R.string.atomcode_not_installed)
            notice = Notice(message, true)
            bump()
            return
        }
        try {
            config = AtomCodeConfig.load()
            notice = null
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Failed to read atomcode config: ${e.message}")
            config = null
            notice = Notice(
                context.getString(R.string.msg_atomcode_config_read_failed, e.message ?: ""),
                false,
            )
        }
        bump()
    }

    // Reload on every resume so edits made in the terminal show up without a restart.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        loadConfig()
    }

    fun fetchModels(account: AtomCodeAccount) {
        val cfg = config ?: return
        toast(context.getString(R.string.msg_fetch_atomcode_running, account.name))
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    // The key is read for this one request only and never stored in a dialog or row.
                    val key = cfg.readAccountApiKey(account.name)
                    val request = AtomCodeAccount(account.name).apply {
                        baseUrl = account.baseUrl
                        provider = account.provider
                    }
                    Result.success(AtomCodeModelFetcher.fetchModelIds(request, key))
                } catch (e: Exception) {
                    Result.failure<List<String>>(e)
                }
            }
            result.fold(
                onSuccess = { ids -> fetchPick = FetchPickRequest(account.name, ids) },
                onFailure = { e ->
                    toast(
                        context.getString(
                            R.string.msg_fetch_atomcode_failed,
                            e.message ?: e.javaClass.simpleName,
                        ),
                    )
                },
            )
        }
    }

    fun copyInstallCommand() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return
        clipboard.setPrimaryClip(
            ClipData.newPlainText("atomcode install", AtomCodeConfig.INSTALL_COMMAND),
        )
        toast(context.getString(R.string.msg_atomcode_install_command_copied))
    }

    val rows = remember(config, revision) { buildRows(config) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_activity_atomcode_models)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("‹", style = MaterialTheme.typography.headlineMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            notice?.let { banner ->
                NoticeCard(banner, onCopy = { copyInstallCommand() })
            }

            if (rows.isEmpty() && config != null) {
                Text(
                    text = stringResource(R.string.atomcode_accounts_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                items(rows) { row ->
                    when (row) {
                        is ListRow.Header -> HeaderRow(
                            account = row.account,
                            config = config,
                            onManage = { account -> dialog = DialogState.AccountActions(account) },
                        )

                        is ListRow.Model -> ModelRow(
                            model = row.model,
                            isDefault = config?.isDefaultModel(row.model) == true,
                            onClick = { dialog = DialogState.ModelActions(row.model) },
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 2.dp,
            ) {
                Button(
                    onClick = { dialog = DialogState.EditAccount(null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(stringResource(R.string.action_add_atomcode_account))
                }
            }
        }
    }

    when (val current = dialog) {
        DialogState.None -> Unit
        is DialogState.ModelActions -> ActionsDialog(
            title = current.model.id,
            actions = listOf(
                stringResource(R.string.action_set_default_model),
                stringResource(R.string.action_edit_atomcode_model),
                stringResource(R.string.action_delete_atomcode_model),
            ),
            onSelect = { index ->
                dialog = when (index) {
                    0 -> {
                        val cfg = config
                        if (cfg != null) {
                            cfg.defaultModel = current.model.id
                            cfg.defaultProvider = current.model.id
                            if (save()) {
                                toast(
                                    context.getString(
                                        R.string.msg_atomcode_default_set,
                                        current.model.id,
                                    ),
                                )
                                bump()
                            }
                        }
                        DialogState.None
                    }

                    1 -> DialogState.EditModel(current.model)
                    else -> DialogState.ConfirmDeleteModel(current.model)
                }
            },
            onDismiss = { dialog = DialogState.None },
        )

        is DialogState.ConfirmDeleteModel -> ConfirmDialog(
            title = stringResource(R.string.action_delete_atomcode_model),
            message = stringResource(R.string.msg_confirm_delete_atomcode_model, current.model.id),
            confirmLabel = stringResource(R.string.action_delete_atomcode_model),
            onConfirm = {
                val cfg = config
                if (cfg != null) {
                    cfg.models.remove(current.model)
                    if (cfg.isDefaultModel(current.model)) {
                        cfg.defaultModel = ""
                        cfg.defaultProvider = ""
                    }
                    if (save()) {
                        toast(context.getString(R.string.msg_atomcode_model_deleted))
                        bump()
                    }
                }
                dialog = DialogState.None
            },
            onDismiss = { dialog = DialogState.None },
        )

        is DialogState.AccountActions -> ActionsDialog(
            title = current.account.name,
            actions = listOf(
                stringResource(R.string.action_fetch_atomcode_models),
                stringResource(R.string.action_edit_atomcode_account),
                stringResource(R.string.action_delete_atomcode_account),
            ),
            onSelect = { index ->
                dialog = when (index) {
                    0 -> {
                        fetchModels(current.account)
                        DialogState.None
                    }

                    1 -> DialogState.EditAccount(current.account)
                    else -> {
                        val cfg = config
                        val used = cfg?.countModelsUsingAccount(current.account.name) ?: 0
                        if (used > 0) {
                            toast(
                                context.getString(
                                    R.string.msg_atomcode_account_in_use,
                                    current.account.name,
                                    used,
                                ),
                            )
                            DialogState.None
                        } else {
                            DialogState.ConfirmDeleteAccount(current.account)
                        }
                    }
                }
            },
            onDismiss = { dialog = DialogState.None },
        )

        is DialogState.ConfirmDeleteAccount -> ConfirmDialog(
            title = stringResource(R.string.action_delete_atomcode_account),
            message = stringResource(
                R.string.msg_confirm_delete_atomcode_account,
                current.account.name,
            ),
            confirmLabel = stringResource(R.string.action_delete_atomcode_account),
            onConfirm = {
                val cfg = config
                if (cfg != null) {
                    cfg.removeAccount(current.account)
                    if (save()) {
                        toast(context.getString(R.string.msg_atomcode_account_deleted))
                        bump()
                    }
                }
                dialog = DialogState.None
            },
            onDismiss = { dialog = DialogState.None },
        )

        is DialogState.EditAccount -> AccountEditDialog(
            target = current.account,
            config = config,
            onDismiss = { dialog = DialogState.None },
            onSaved = { bump() },
            onFetch = { account ->
                // The account dialog deliberately stays open: fetching is not the end of the edit,
                // and the result picker is shown as a second layer above it.
                bump()
                fetchModels(account)
            },
            toast = { toast(it) },
            save = { save() },
        )

        is DialogState.EditModel -> ModelEditDialog(
            model = current.model,
            config = config,
            onDismiss = { dialog = DialogState.None },
            onSaved = { bump() },
            toast = { toast(it) },
            save = { save() },
        )
    }

    // Rendered after the main dialog on purpose: the picker stacks above the account dialog, which
    // stays open behind it because a fetch does not finish the edit.
    fetchPick?.let { pick ->
        FetchPickDialog(
            accountName = pick.accountName,
            ids = pick.ids,
            config = config,
            onDismiss = { fetchPick = null },
            onImported = { bump() },
            toast = { toast(it) },
            save = { save() },
        )
    }
}

private fun buildRows(config: AtomCodeConfig?): List<ListRow> {
    if (config == null) return emptyList()
    val rows = mutableListOf<ListRow>()
    for (account in config.accounts) {
        rows.add(ListRow.Header(account))
        for (model in config.models) {
            if (account.name == model.account) rows.add(ListRow.Model(model))
        }
    }
    // Models pointing at a missing account stay visible instead of silently disappearing.
    val orphans = config.models.filter { config.findAccount(it.account) == null }
    if (orphans.isNotEmpty()) {
        rows.add(ListRow.Header(null))
        orphans.forEach { rows.add(ListRow.Model(it)) }
    }
    return rows
}

@Composable
private fun NoticeCard(notice: Notice, onCopy: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = notice.message, style = MaterialTheme.typography.bodyMedium)
            if (notice.showCommand) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = AtomCodeConfig.INSTALL_COMMAND,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onCopy) {
                    Text(stringResource(R.string.action_copy_install_command))
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(
    account: AtomCodeAccount?,
    config: AtomCodeConfig?,
    onManage: (AtomCodeAccount) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account?.name
                    ?: stringResource(R.string.atomcode_group_unknown_account),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val summary = if (account == null) {
                stringResource(R.string.atomcode_group_unknown_account_summary)
            } else {
                buildList {
                    if (account.provider.isNotEmpty()) add(account.provider)
                    add(
                        stringResource(
                            if (account.willHaveApiKey())
                                R.string.atomcode_account_key_configured
                            else
                                R.string.atomcode_account_key_missing,
                        ),
                    )
                    add(
                        stringResource(
                            R.string.atomcode_account_models_count,
                            config?.countModelsUsingAccount(account.name) ?: 0,
                        ),
                    )
                }.joinToString(" · ")
            }
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (account != null) {
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { onManage(account) }) {
                Text(stringResource(R.string.atomcode_group_manage))
            }
        }
    }
}

@Composable
private fun ModelRow(model: AtomCodeModel, isDefault: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = model.id,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val details = buildList {
                model.contextWindow?.let { add(it.toString()) }
                if (model.reasoningEffortLevels.isNotEmpty()) {
                    add(model.reasoningEffortLevels.joinToString("/"))
                }
            }.joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (isDefault) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.atomcode_model_default_badge),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

private const val LOG_TAG = "AtomCodeModelsActivity"
