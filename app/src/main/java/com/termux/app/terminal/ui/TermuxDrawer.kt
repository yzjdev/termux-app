package com.termux.app.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termux.R

/**
 * The left drawer of the terminal screen: connected accordion groups for the storage roots
 * (internal, external and shared storage) and the two shortcut buttons at the bottom.
 *
 * Each group expands independently, shows one directory level at a time and remembers the
 * directory it was last browsed to. The directory state itself is owned by the activity
 * through [DrawerGroupUi] so it survives drawer open/close and activity recreation.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TermuxDrawer(
    groups: List<DrawerGroupUi>,
    onOpenAtomCodeModels: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Demo-style drawer sheet: ModalDrawerSheet paints its own container colour across the whole
    // sheet (behind both transparent system bars), so the navigation-bar strip can never show the
    // black window background. Its content is inset from the system bars automatically.
    ModalDrawerSheet(
        modifier = modifier
            .fillMaxSize()
            // Consume taps on blank areas so they do not pass through to the terminal view and
            // raise the soft keyboard. No indication, so it stays invisible; DrawerLayout still
            // handles its own swipe-to-close gesture at the parent level.
            .clickable(
                onClick = {},
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ),
        drawerContainerColor = colorResource(R.color.drawer_background_start),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            if (groups.isNotEmpty()) {
                val shape = RoundedCornerShape(22.dp)
                val tint = colorResource(R.color.drawer_icon_tint)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(colorResource(R.color.drawer_section_card_background), shape)
                        .border(1.dp, colorResource(R.color.drawer_section_card_stroke), shape)
                ) {
                    groups.forEachIndexed { index, group ->
                        stickyHeader(key = "header:$index") {
                            Column {
                                if (index > 0) DrawerListDivider()
                                DrawerStorageHeader(group)
                            }
                        }
                        if (group.expanded) {
                            items(
                                items = group.items,
                                key = { "file:$index:${it.file.path}" },
                                contentType = { "file" },
                            ) { entry ->
                                Column {
                                    DrawerListDivider()
                                    DrawerFileRow(
                                        item = entry,
                                        tint = tint,
                                        onClick = {
                                            if (entry.isDirectory) group.enterDirectory(entry.file)
                                            else group.openFile(entry.file)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DrawerBottomButton(
                iconRes = R.drawable.ic_drawer_model,
                label = stringResource(R.string.atomcode_models_preferences_title),
                modifier = Modifier.weight(1f),
                onClick = onOpenAtomCodeModels,
            )
            DrawerBottomButton(
                iconRes = R.drawable.ic_settings,
                label = stringResource(R.string.action_drawer_settings),
                modifier = Modifier.weight(1f),
                onClick = onOpenSettings,
            )
        }
    }
}

/** Header of a connected storage group; its entries are separate lazy list items. */
@Composable
private fun DrawerStorageHeader(group: DrawerGroupUi) {
    val tint = colorResource(R.color.drawer_icon_tint)
    val secondary = colorResource(R.color.drawer_section_title)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(colorResource(R.color.drawer_section_card_background))
            .clickable { group.toggleExpanded() }
            .padding(start = 14.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colorResource(R.color.drawer_icon_chip_background)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_drawer_folder),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = group.title,
                color = tint,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = group.pathText,
                color = secondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { group.copyPath() },
            )
        }

        if (group.canGoUp) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { group.goUp() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_up),
                    contentDescription = stringResource(R.string.action_drawer_parent_dir),
                    tint = tint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        if (group.expanded && group.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(18.dp),
                color = secondary,
                strokeWidth = 2.dp,
            )
        }

        Icon(
            painter = painterResource(R.drawable.ic_drawer_expand),
            contentDescription = null,
            tint = secondary,
            modifier = Modifier
                .padding(start = 2.dp)
                .size(24.dp)
                .rotate(if (group.expanded) 180f else 0f),
        )
    }
}

/** Hairline separator between two connected rows of a storage card's list. */
@Composable
private fun DrawerListDivider() {
    HorizontalDivider(
        thickness = 1.dp,
        color = colorResource(R.color.drawer_list_divider),
    )
}

/** One entry of a storage card's connected directory list: folder, APK or plain file. */
@Composable
private fun DrawerFileRow(item: DrawerFileItem, tint: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(drawerFileIcon(item)),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = item.name,
            color = tint,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f),
        )
    }
}

/** Folder, APK or plain file icon from the metadata loaded with [item]. */
private fun drawerFileIcon(item: DrawerFileItem): Int = when {
    item.isDirectory -> R.drawable.ic_drawer_folder
    item.isApk -> R.drawable.ic_drawer_apk
    else -> R.drawable.ic_drawer_file
}

/** Bottom shortcut button of the drawer: outlined pill with an icon and a label. */
@Composable
private fun DrawerBottomButton(
    iconRes: Int,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tint = colorResource(R.color.drawer_icon_tint)
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, colorResource(R.color.drawer_section_card_stroke), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 120.dp),
        )
    }
}
