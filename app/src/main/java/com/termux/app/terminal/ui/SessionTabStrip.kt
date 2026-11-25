package com.termux.app.terminal.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termux.R
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession

/** Brand red gradient used for the selected session tab. */
private val TabSelectedBrush = Brush.horizontalGradient(listOf(Color(0xFFFF0000), Color(0xFFC4001D)))

/** One entry of the session strip. */
data class SessionTabItem(
    /** Stable identity of the tab, i.e. the terminal session handle. */
    val handle: String,
    /** Text shown in the tab: the user-renamed session name, or a fallback. */
    val title: String,
    /** Whether this tab belongs to the session currently shown in the terminal view. */
    val selected: Boolean,
)

/**
 * The scrollable strip of terminal sessions above the terminal view, plus the new-session
 * button at its end.
 *
 * Tapping a tab switches to that session, long pressing it renames the session and the close
 * button kills it after a confirmation. The strip scrolls to the selected tab whenever the
 * selection changes, which replaces the scroll-to-selected behaviour the previous
 * TabLayout-based controller provided.
 */
@Composable
fun SessionTabStrip(
    tabs: List<SessionTabItem>,
    onSelect: (SessionTabItem) -> Unit,
    onRename: (SessionTabItem) -> Unit,
    onClose: (SessionTabItem) -> Unit,
    onNewSession: () -> Unit,
    onNewSessionLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Keep the selected tab visible: sessions are appended at the end, so a new session would
    // otherwise be created off screen.
    LaunchedEffect(tabs) {
        val index = tabs.indexOfFirst { it.selected }
        if (index >= 0) listState.animateScrollToItem(index)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(tabs, key = { _, item -> item.handle }) { index, item ->
                SessionTab(
                    number = index + 1,
                    item = item,
                    onSelect = { onSelect(item) },
                    onRename = { onRename(item) },
                    onClose = { onClose(item) },
                )
            }
        }

        Box(
            modifier = Modifier
                .padding(end = 4.dp)
                .size(40.dp)
                .clip(CircleShape)
                .combinedClickable(
                    onClick = onNewSession,
                    onLongClick = onNewSessionLongClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = stringResource(R.string.action_new_session),
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** One session capsule: number badge, title and close button. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionTab(
    number: Int,
    item: SessionTabItem,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onClose: () -> Unit,
) {
    val shape = RoundedCornerShape(17.dp)
    val selected = item.selected

    Row(
        modifier = Modifier
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .defaultMinSize(minWidth = 48.dp, minHeight = 34.dp)
            .clip(shape)
            .then(
                if (selected) Modifier.background(TabSelectedBrush, shape)
                else Modifier
                    .background(Color(0x1FFFFFFF), shape)
                    .border(1.dp, Color(0x29FFFFFF), shape)
            )
            .combinedClickable(onClick = onSelect, onLongClick = onRename)
            .padding(start = 12.dp, top = 6.dp, end = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (selected) Color.White else Color(0x33FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                color = if (selected) Color(0xFFFF0000) else Color(0xFFBDBDBD),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Text(
            text = item.title,
            color = if (selected) Color.White else Color(0xFFBDBDBD),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 6.dp, end = 4.dp)
                .widthIn(max = 180.dp),
        )

        Box(
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .size(24.dp)
                .clip(CircleShape)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.action_close_session),
                tint = if (selected) Color.White else Color(0x80FFFFFF),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Build the tab list for the sessions shown in the main interface, skipping overlay-only
 * sessions (the ones running inside the floating terminal panel). */
fun buildSessionTabs(
    sessions: List<TermuxSession>,
    currentHandle: String?,
): List<SessionTabItem> = sessions
    .filterNot { it.executionCommand?.isFloatingPanelSession == true }
    .map { termuxSession ->
        val session = termuxSession.terminalSession
        SessionTabItem(
            handle = session?.mHandle.orEmpty(),
            title = session?.mSessionName?.takeIf { it.isNotEmpty() } ?: "session",
            selected = session?.mHandle != null && session.mHandle == currentHandle,
        )
    }
