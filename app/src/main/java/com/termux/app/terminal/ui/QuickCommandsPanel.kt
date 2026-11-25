package com.termux.app.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termux.R
import com.termux.app.models.QuickCommand
import kotlin.math.roundToInt

/** Colors of the dark quick commands panel, shared by every page. */
private val OverlayTitle = Color(0xE6FFFFFF)
private val OverlaySecondary = Color(0x99FFFFFF)
private val OverlayDivider = Color(0x1FFFFFFF)
private val OverlayEmpty = Color(0x80FFFFFF)
private val OverlayDanger = Color(0xFFFF8A80)
private val OverlayItemBackground = Color(0x14FFFFFF)
private val OverlayToggleChip = Color(0x2EFFFFFF)

/**
 * The shared floating quick commands panel: one window that hosts the command list, the
 * add/edit form, the run/edit/delete actions and the restore-defaults page. Switching pages
 * keeps the window and its dragged position, so only the content changes.
 *
 * The panel is draggable by its title bar and is always clamped inside the host, which is the
 * activity content area passed in by the caller.
 */
@Composable
fun QuickCommandsPanel(
    state: QuickCommandsPanelState,
    commands: List<QuickCommand>,
    restoreRows: List<RestoreRow>,
    onClose: () -> Unit,
    onAdd: () -> Unit,
    onRestorePage: () -> Unit,
    onRun: (QuickCommand) -> Unit,
    onEdit: (Int) -> Unit,
    onShowActions: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onToggleFloating: (QuickCommand) -> Unit,
    onToggleNotify: (QuickCommand) -> Unit,
    onSave: () -> Unit,
    onNameChange: (String) -> Unit,
    onCommandChange: (String) -> Unit,
    onToggleRestoreRow: (Int) -> Unit,
    onToggleRestoreAll: () -> Unit,
    onRestoreSelected: () -> Unit,
    onCancelPage: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val panelShape = RoundedCornerShape(14.dp)

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(panelShape)
            .background(Color(0xF2222222), panelShape)
            .border(1.dp, Color(0x1FFFFFFF), panelShape)
    ) {
        QuickCommandsTitleBar(
            title = state.title,
            page = state.page,
            onClose = onClose,
            onAdd = onAdd,
            onRestorePage = onRestorePage,
            onToggleRestoreAll = onToggleRestoreAll,
            hasSelectableRestoreRows = restoreRows.any { !it.alreadyInList },
            allRestoreSelected = restoreRows.filterNot { it.alreadyInList }.let {
                it.isNotEmpty() && it.all { row -> row.checked }
            },
            onDrag = onDrag,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(OverlayDivider)
        )

        when (state.page) {
            QuickCommandsPage.LIST -> QuickCommandsList(
                commands = commands,
                onRun = onRun,
                onShowActions = onShowActions,
                onToggleFloating = onToggleFloating,
                onToggleNotify = onToggleNotify,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
            )

            QuickCommandsPage.EDIT -> QuickCommandEditPage(
                state = state,
                onNameChange = onNameChange,
                onCommandChange = onCommandChange,
                onSave = onSave,
                onCancel = onCancelPage,
            )

            QuickCommandsPage.ACTIONS -> QuickCommandActionsPage(
                onRun = {
                    commands.getOrNull(state.actionPosition)?.let(onRun)
                },
                onEdit = { onEdit(state.actionPosition) },
                onDelete = { onDelete(state.actionPosition) },
            )

            QuickCommandsPage.RESTORE -> QuickCommandRestorePage(
                rows = restoreRows,
                onToggleRow = onToggleRestoreRow,
                onRestore = onRestoreSelected,
                onCancel = onCancelPage,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

/** Title bar of the shared panel: title, page-dependent actions and the close button. It is
 * also the drag handle of the panel. */
@Composable
private fun QuickCommandsTitleBar(
    title: String,
    page: QuickCommandsPage,
    onClose: () -> Unit,
    onAdd: () -> Unit,
    onRestorePage: () -> Unit,
    onToggleRestoreAll: () -> Unit,
    hasSelectableRestoreRows: Boolean,
    allRestoreSelected: Boolean,
    onDrag: (Float, Float) -> Unit,
) {
    var dragX = 0f
    var dragY = 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(Color(0xF0333333))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        dragX = 0f
                        dragY = 0f
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragX += amount.x
                        dragY += amount.y
                        onDrag(amount.x, amount.y)
                    },
                )
            }
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = OverlayTitle,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (page == QuickCommandsPage.LIST) {
            TitleBarAction(stringResource(R.string.action_add_quick_command), onAdd)
            TitleBarAction(stringResource(R.string.action_restore_quick_commands), onRestorePage)
        }

        if (page == QuickCommandsPage.RESTORE && hasSelectableRestoreRows) {
            TitleBarAction(
                text = stringResource(
                    if (allRestoreSelected) R.string.action_unselect_all_quick_commands
                    else R.string.action_select_all_quick_commands
                ),
                onClick = onToggleRestoreAll,
            )
        }

        Box(
            modifier = Modifier
                .padding(start = 2.dp)
                .size(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** A text-only action in the panel title bar. */
@Composable
private fun TitleBarAction(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = OverlayTitle,
        fontSize = 12.sp,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

/** The list page: one card per command, plus the empty state. */
@Composable
private fun QuickCommandsList(
    commands: List<QuickCommand>,
    onRun: (QuickCommand) -> Unit,
    onShowActions: (Int) -> Unit,
    onToggleFloating: (QuickCommand) -> Unit,
    onToggleNotify: (QuickCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        if (commands.isEmpty()) {
            Text(
                text = stringResource(R.string.msg_quick_commands_empty),
                color = OverlayEmpty,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp, bottom = 4.dp)
            ) {
                commands.forEachIndexed { index, command ->
                    QuickCommandCard(
                        command = command,
                        onRun = { onRun(command) },
                        onShowActions = { onShowActions(index) },
                        onToggleFloating = { onToggleFloating(command) },
                        onToggleNotify = { onToggleNotify(command) },
                    )
                }
            }
        }
    }
}

/**
 * Resolve a quick command icon to a painter that can actually be loaded. `painterResource` throws
 * for an id that does not resolve to a raster or vector drawable anymore, which happens when a
 * command saved by an older build kept a resource id that this build does not define, so such ids
 * fall back to a built-in icon instead of crashing the panel.
 */
@Composable
private fun quickCommandIconPainter(iconRes: Int): Painter {
    val resources = LocalContext.current.resources
    val safeIconRes = remember(iconRes) {
        val isDrawable = runCatching { resources.getResourceTypeName(iconRes) == "drawable" }
            .getOrDefault(false)
        if (isDrawable) iconRes else R.drawable.ic_drawer_bolt
    }
    return painterResource(safeIconRes)
}

/** One command card: icon, name, command preview, run button and the two execution switches. */
@Composable
private fun QuickCommandCard(
    command: QuickCommand,
    onRun: () -> Unit,
    onShowActions: () -> Unit,
    onToggleFloating: () -> Unit,
    onToggleNotify: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(OverlayItemBackground)
            .clickable(onClick = onShowActions)
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = quickCommandIconPainter(command.iconRes),
                contentDescription = null,
                tint = OverlayTitle,
                modifier = Modifier.size(24.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = command.name,
                    color = OverlayTitle,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = command.command,
                    color = OverlaySecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            Box(
                modifier = Modifier
                    .padding(start = 10.dp)
                    .size(30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .clickable(onClick = onRun),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_drawer_play),
                    contentDescription = null,
                    tint = OverlayTitle,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Row(
            modifier = Modifier
                .padding(start = 30.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuickCommandToggle(
                // `painterResource` cannot load the state list drawable the toggle icon used to be,
                // since it only supports vector and rasterised assets, so pick the variant here.
                iconRes = if (command.useFloatingTerminal)
                    R.drawable.ic_quick_command_floating_terminal_selected
                else
                    R.drawable.ic_quick_command_floating_terminal_outline,
                label = stringResource(R.string.quick_command_toggle_floating_short),
                description = stringResource(R.string.quick_command_toggle_floating_terminal_hint),
                on = command.useFloatingTerminal,
                enabled = true,
                onClick = onToggleFloating,
            )

            // 「结果提示」只对后台执行生效，悬浮终端模式下置灰。
            QuickCommandToggle(
                iconRes = if (command.notifyResult)
                    R.drawable.ic_quick_command_notify_selected
                else
                    R.drawable.ic_quick_command_notify_outline,
                label = stringResource(R.string.quick_command_toggle_notify_short),
                description = stringResource(R.string.quick_command_toggle_notify_result_hint),
                on = command.notifyResult,
                enabled = !command.useFloatingTerminal,
                onClick = onToggleNotify,
                modifier = Modifier.padding(start = 18.dp),
            )
        }
    }
}

/** A switch shown on a command card: icon chip plus label, dimmed when it does not apply. */
@Composable
private fun QuickCommandToggle(
    iconRes: Int,
    label: String,
    description: String,
    on: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (on) OverlayTitle else Color(0xFF98989F)
    val alpha = if (enabled) 1f else 0.35f

    Row(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (on) OverlayToggleChip else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = description,
                tint = tint.copy(alpha = alpha),
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = label,
            color = tint.copy(alpha = alpha),
            fontSize = 11.sp,
            maxLines = 1,
            modifier = Modifier.padding(start = 5.dp),
        )
    }
}

/** The add/edit page: name and command inputs plus save/cancel. */
@Composable
private fun QuickCommandEditPage(
    state: QuickCommandsPanelState,
    onNameChange: (String) -> Unit,
    onCommandChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 12.dp)
    ) {
        OverlayLabel(stringResource(R.string.quick_command_name_label))
        OverlayInput(
            value = state.nameInput,
            hint = stringResource(R.string.quick_command_name_hint),
            onValueChange = onNameChange,
            singleLine = true,
        )

        Spacer(Modifier.height(14.dp))
        OverlayLabel(stringResource(R.string.quick_command_command_label))
        OverlayInput(
            value = state.commandInput,
            hint = stringResource(R.string.quick_command_command_hint),
            onValueChange = onCommandChange,
            singleLine = false,
            minLines = 2,
        )

        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(OverlayDivider)
        )

        OverlayButtons(
            confirmText = stringResource(android.R.string.ok),
            onConfirm = onSave,
            onCancel = onCancel,
        )
    }
}

/** The actions page: run, edit or delete the tapped command. */
@Composable
private fun QuickCommandActionsPage(
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 6.dp)
    ) {
        ActionRow(R.drawable.ic_drawer_play, stringResource(R.string.action_run_quick_command), OverlayTitle, onRun)
        ActionRow(R.drawable.ic_quick_command_edit, stringResource(R.string.action_edit_quick_command), OverlayTitle, onEdit)
        ActionRow(R.drawable.ic_quick_command_delete, stringResource(R.string.action_delete_quick_command), OverlayDanger, onDelete)
    }
}

/** One row of the actions page. */
@Composable
private fun ActionRow(iconRes: Int, label: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            color = color,
            fontSize = 14.sp,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

/** The restore-defaults page: a checkable row per built-in command. */
@Composable
private fun QuickCommandRestorePage(
    rows: List<RestoreRow>,
    onToggleRow: (Int) -> Unit,
    onRestore: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.msg_quick_command_restore_hint),
            color = OverlaySecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 4.dp),
        )

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 4.dp)
        ) {
            rows.forEach { row ->
                RestoreRowView(row = row, onToggle = { onToggleRow(row.index) })
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(OverlayDivider)
        )

        OverlayButtons(
            confirmText = stringResource(R.string.action_restore_quick_commands),
            onConfirm = onRestore,
            onCancel = onCancel,
        )
    }
}

/** One checkable restore row; rows already in the list stay checked and disabled. */
@Composable
private fun RestoreRowView(row: RestoreRow, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !row.alreadyInList, onClick = onToggle)
            .padding(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = row.checked,
            onCheckedChange = null,
            enabled = !row.alreadyInList,
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFF4CAF50),
                uncheckedColor = OverlaySecondary,
                disabledUncheckedColor = Color(0xFF98989F),
            ),
        )
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(
                text = row.name,
                color = OverlayTitle,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.command,
                color = OverlaySecondary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (row.alreadyInList) {
                Text(
                    text = stringResource(R.string.msg_quick_command_already_in_list),
                    color = OverlaySecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** Small label above a panel input. */
@Composable
private fun OverlayLabel(text: String) {
    Text(text = text, color = OverlaySecondary, fontSize = 12.sp)
}

/** A borderless input on the dark panel, matching the previous EditText styling. */
@Composable
private fun OverlayInput(
    value: String,
    hint: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean,
    minLines: Int = 1,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 10.dp)
    ) {
        if (value.isEmpty()) {
            Text(text = hint, color = OverlaySecondary, fontSize = 14.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = TextStyle(color = OverlayTitle, fontSize = 14.sp),
            cursorBrush = SolidColor(OverlayTitle),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Cancel/confirm row shared by the edit and restore pages. */
@Composable
private fun OverlayButtons(
    confirmText: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onCancel) {
            Text(text = stringResource(android.R.string.cancel), color = OverlaySecondary, fontSize = 14.sp)
        }
        Spacer(Modifier.width(4.dp))
        TextButton(onClick = onConfirm) {
            Text(text = confirmText, color = OverlayTitle, fontSize = 14.sp)
        }
    }
}
