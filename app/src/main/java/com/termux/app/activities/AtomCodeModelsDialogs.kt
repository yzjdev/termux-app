package com.termux.app.activities

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.termux.R
import com.termux.app.atomcode.AtomCodeAccount
import com.termux.app.atomcode.AtomCodeConfig
import com.termux.app.atomcode.AtomCodeModel

/** Which modal is currently shown by [AtomCodeModelsScreen]. */
internal sealed interface DialogState {
    data object None : DialogState
    data class ModelActions(val model: AtomCodeModel) : DialogState
    data class ConfirmDeleteModel(val model: AtomCodeModel) : DialogState
    data class AccountActions(val account: AtomCodeAccount) : DialogState
    data class ConfirmDeleteAccount(val account: AtomCodeAccount) : DialogState
    data class EditAccount(val account: AtomCodeAccount?) : DialogState
    data class EditModel(val model: AtomCodeModel) : DialogState
}

/**
 * A fetched model list waiting to be picked. Kept out of [DialogState] because it is shown as a
 * second dialog layer above the account dialog, which stays open after a fetch.
 */
internal data class FetchPickRequest(val accountName: String, val ids: List<String>)

@Composable
internal fun ActionsDialog(
    title: String,
    actions: List<String>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                actions.forEachIndexed { index, label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(index) }
                            .padding(vertical = 14.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
internal fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

/** Add/edit a provider account. Mirrors the original dialog's validation and key handling. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountEditDialog(
    target: AtomCodeAccount?,
    config: AtomCodeConfig?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onFetch: (AtomCodeAccount) -> Unit,
    toast: (String) -> Unit,
    save: () -> Boolean,
) {
    val context = LocalContext.current
    val protocols = stringArrayResource(R.array.atomcode_provider_protocols)
    var name by remember(target) { mutableStateOf(target?.name ?: "") }
    var provider by remember(target) {
        mutableStateOf(if (target == null) protocols.first() else (target.provider ?: ""))
    }
    var baseUrl by remember(target) { mutableStateOf(target?.baseUrl ?: "") }
    var apiKey by remember { mutableStateOf("") }
    var clearKey by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    /** Validate, write the inputs to the config and return the saved account. */
    fun persist(): AtomCodeAccount? {
        if (config == null) return null
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            toast(context.getString(R.string.msg_atomcode_account_name_required))
            return null
        }
        val existing = config.findAccount(trimmedName)
        if (existing != null && existing !== target) {
            toast(context.getString(R.string.msg_atomcode_account_name_exists, trimmedName))
            return null
        }

        val created = target == null
        val account = target ?: AtomCodeAccount(trimmedName).also { config.addAccount(it) }
        account.name = trimmedName
        account.provider = provider.trim().ifEmpty { "openai-compatible" }
        account.baseUrl = baseUrl.trim()
        val typedKey = apiKey.trim()
        if (typedKey.isNotEmpty()) {
            account.apiKeyToWrite = typedKey
            account.deleteApiKey = false
        } else if (clearKey) {
            account.apiKeyToWrite = ""
            account.deleteApiKey = true
        }

        if (!save()) {
            // Roll a brand new account back so the list keeps matching the file on disk.
            if (created) config.removeAccount(account)
            return null
        }
        toast(context.getString(R.string.msg_atomcode_account_saved))
        onSaved()
        return account
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (target == null)
                        R.string.title_add_atomcode_account
                    else
                        R.string.title_edit_atomcode_account,
                ),
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.atomcode_account_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                ) {
                    OutlinedTextField(
                        value = provider,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.atomcode_account_provider_label)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        singleLine = true,
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        protocols.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    provider = option
                                    expanded = false
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text(stringResource(R.string.atomcode_account_base_url_label)) },
                    placeholder = { Text(stringResource(R.string.atomcode_account_base_url_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(stringResource(R.string.atomcode_account_api_key_label)) },
                    placeholder = {
                        Text(
                            stringResource(
                                if (target == null)
                                    R.string.atomcode_account_api_key_hint
                                else
                                    R.string.atomcode_account_api_key_new_hint,
                            ),
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (target?.hasApiKey == true) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(checked = clearKey, onCheckedChange = { clearKey = it })
                        Text(stringResource(R.string.action_clear_atomcode_api_key))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val saved = persist() ?: return@TextButton
                    // The edit is finished here, so the dialog closes. A brand new account is only
                    // useful once it has models, so those are pulled right away.
                    onDismiss()
                    if (target == null && saved.baseUrl.isNotEmpty()) onFetch(saved)
                },
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            // Fetch needs the credentials as typed, so save first and then pull. The dialog stays
            // open, since a fetch does not finish the edit and the result is shown above it.
            TextButton(
                onClick = {
                    if (baseUrl.trim().isEmpty()) {
                        toast(context.getString(R.string.msg_atomcode_account_base_url_required))
                        return@TextButton
                    }
                    val saved = persist() ?: return@TextButton
                    onFetch(saved)
                },
            ) {
                Text(stringResource(R.string.action_fetch_atomcode_models))
            }
        },
    )
}

/** Edit an existing model. The table key is fixed, exactly like the original dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelEditDialog(
    model: AtomCodeModel,
    config: AtomCodeConfig?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    toast: (String) -> Unit,
    save: () -> Boolean,
) {
    val context = LocalContext.current
    val accountNames = config?.accounts?.map { it.name } ?: emptyList()
    var id by remember(model) { mutableStateOf(model.id) }
    var account by remember(model) { mutableStateOf(model.account) }
    var modelName by remember(model) { mutableStateOf(model.displayModel()) }
    var contextWindowText by remember(model) {
        mutableStateOf(model.contextWindow?.toString() ?: "")
    }
    var reasoning by remember(model) {
        mutableStateOf(model.reasoningEffortLevels.joinToString(", "))
    }
    var vision by remember(model) { mutableStateOf(model.supportsVision) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_edit_atomcode_model)) },
        text = {
            Column {
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it },
                    label = { Text(stringResource(R.string.atomcode_model_id_label)) },
                    singleLine = true,
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                ) {
                    OutlinedTextField(
                        value = account,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.atomcode_model_account_label)) },
                        placeholder = { Text(stringResource(R.string.atomcode_model_account_hint)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        singleLine = true,
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        accountNames.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    account = option
                                    expanded = false
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text(stringResource(R.string.atomcode_model_name_label)) },
                    placeholder = { Text(stringResource(R.string.atomcode_model_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = contextWindowText,
                    onValueChange = { contextWindowText = it },
                    label = { Text(stringResource(R.string.atomcode_model_context_label)) },
                    placeholder = { Text(stringResource(R.string.atomcode_model_context_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = reasoning,
                    onValueChange = { reasoning = it },
                    label = { Text(stringResource(R.string.atomcode_model_reasoning_label)) },
                    placeholder = { Text(stringResource(R.string.atomcode_model_reasoning_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Checkbox(checked = vision, onCheckedChange = { vision = it })
                    Text(stringResource(R.string.atomcode_model_vision_label))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmedId = id.trim()
                    if (trimmedId.isEmpty()) {
                        toast(context.getString(R.string.msg_atomcode_model_id_required))
                        return@TextButton
                    }
                    if (config?.isModelIdTaken(trimmedId, model) == true) {
                        toast(context.getString(R.string.msg_atomcode_model_id_exists, trimmedId))
                        return@TextButton
                    }
                    val accountName = account.trim()
                    if (accountName.isEmpty() || config?.findAccount(accountName) == null) {
                        toast(context.getString(R.string.msg_atomcode_account_required))
                        return@TextButton
                    }
                    var contextWindow: Int? = null
                    if (contextWindowText.trim().isNotEmpty()) {
                        contextWindow = contextWindowText.trim().toIntOrNull()
                        if (contextWindow == null) {
                            toast(context.getString(R.string.atomcode_model_context_hint))
                            return@TextButton
                        }
                    }

                    model.id = trimmedId
                    model.account = accountName
                    model.model = modelName.trim().ifEmpty { trimmedId }
                    model.contextWindow = contextWindow
                    model.supportsVision = vision
                    model.reasoningEffortLevels.clear()
                    reasoning.split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .forEach { model.reasoningEffortLevels.add(it) }

                    if (!save()) return@TextButton
                    toast(context.getString(R.string.msg_atomcode_model_saved))
                    onSaved()
                    onDismiss()
                },
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

/** Multi-select the fetched model ids and import the checked ones. */
@Composable
internal fun FetchPickDialog(
    accountName: String,
    ids: List<String>,
    config: AtomCodeConfig?,
    onDismiss: () -> Unit,
    onImported: () -> Unit,
    toast: (String) -> Unit,
    save: () -> Boolean,
) {
    val context = LocalContext.current
    val addable = remember(ids, config) { ids.filter { config?.findModel(it) == null } }
    val checked = remember(addable) { MutableList(addable.size) { false }.toMutableStateList() }

    // The original showed a toast and no dialog when everything was already present.
    LaunchedEffect(addable) {
        if (addable.isEmpty()) {
            toast(context.getString(R.string.msg_fetch_atomcode_empty_new, accountName))
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_fetch_atomcode_pick, addable.size)) },
        text = {
            Column {
                addable.forEachIndexed { index, id ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { checked[index] = !checked[index] }
                            .padding(vertical = 6.dp),
                    ) {
                        Checkbox(
                            checked = checked[index],
                            onCheckedChange = { checked[index] = it },
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(id, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val selected = addable.indices.filter { checked[it] }
                    if (selected.isEmpty()) {
                        onDismiss()
                        return@TextButton
                    }
                    selected.forEach { index ->
                        val id = addable[index]
                        config?.addModel(
                            AtomCodeModel(id).apply {
                                this.account = accountName
                                this.model = id
                            },
                        )
                    }
                    if (!save()) {
                        // Roll back so the list keeps matching the file on disk.
                        config?.models?.removeAll { it.isNew }
                        onDismiss()
                        return@TextButton
                    }
                    toast(context.getString(R.string.msg_fetch_atomcode_imported, selected.size))
                    onImported()
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.action_fetch_atomcode_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
