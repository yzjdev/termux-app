package com.termux.app.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.R
import com.termux.view.TerminalView

/**
 * The floating terminal panel: an in-app overlay that runs one command in a dedicated session
 * and keeps showing its output after the command exits.
 *
 * The panel is presentational: the activity owns its window, position and visibility, exactly
 * like the previous View implementation did with `setTranslationX()`/`setVisibility()`. Only the
 * title bar drag and the three title bar buttons are wired here.
 *
 * The terminal itself is bridged with `AndroidView`, so the engine and its session are untouched
 * by Compose and the panel keeps showing its output while it is hidden.
 */
@Composable
fun FloatingTerminalPanel(
    title: String,
    onClose: () -> Unit,
    onRerun: () -> Unit,
    onMinimize: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onTerminalViewCreated: (TerminalView) -> Unit,
    modifier: Modifier = Modifier,
) {
    val panelShape = RoundedCornerShape(14.dp)

    Column(
        modifier = modifier
            .clip(panelShape)
            .background(Color(0xF2222222), panelShape)
            .border(1.dp, Color(0x1FFFFFFF), panelShape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(Color(0xF0333333))
                .pointerInput(Unit) {
                    detectDragGestures { change, amount ->
                        change.consume()
                        onDrag(amount.x, amount.y)
                    }
                }
                .padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_drawer_folder),
                contentDescription = null,
                tint = Color(0x99FFFFFF),
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(16.dp),
            )

            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.weight(1f),
            )

            PanelIconButton(
                iconRes = R.drawable.ic_drawer_play,
                description = stringResource(R.string.action_rerun_floating_terminal),
                onClick = onRerun,
            )
            PanelIconButton(
                iconRes = R.drawable.ic_quick_command_minimize,
                description = stringResource(R.string.action_minimize_floating_terminal),
                onClick = onMinimize,
            )
            PanelIconButton(
                iconRes = R.drawable.ic_close,
                description = null,
                onClick = onClose,
                modifier = Modifier.padding(start = 2.dp),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0x1FFFFFFF))
        )

        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            factory = { context ->
                TerminalView(context, null).also { onTerminalViewCreated(it) }
            },
        )
    }
}

/** A square icon button of the panel title bar. */
@Composable
private fun PanelIconButton(
    iconRes: Int,
    description: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * The circular bubble the panel collapses into. Tapping restores the panel; dragging moves the
 * bubble and releasing snaps it to the nearest screen edge, which also moves the panel's restore
 * anchor so the panel reopens next to the bubble.
 */
@Composable
fun FloatingTerminalBubble(
    onRestore: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color(0xF0222222), CircleShape)
            .border(1.dp, Color(0x33FFFFFF), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, amount ->
                        change.consume()
                        onDrag(amount.x, amount.y)
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                )
            }
            .clickable(onClick = onRestore),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_quick_command_floating_terminal_outline),
            contentDescription = stringResource(R.string.action_restore_floating_terminal),
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}
