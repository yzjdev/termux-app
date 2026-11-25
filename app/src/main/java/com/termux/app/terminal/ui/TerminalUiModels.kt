package com.termux.app.terminal.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.termux.app.models.QuickCommand
import java.io.File

/**
 * Pages of the shared quick commands panel. The same floating panel hosts the list, the
 * add/edit form, the run/edit/delete actions and the restore-defaults list, so only one
 * window is ever on screen.
 */
enum class QuickCommandsPage { LIST, EDIT, ACTIONS, RESTORE }

/**
 * Content state of the shared quick commands panel. Window position, size and drag translation
 * stay in the activity, exactly like the previous View implementation, so switching pages can
 * keep the panel anchored at the same visual top.
 */
class QuickCommandsPanelState {

    /** The page currently shown in the panel. */
    var page by mutableStateOf(QuickCommandsPage.LIST)

    /** Title shown in the panel title bar, which changes with the page. */
    var title by mutableStateOf("")

    /** The command being edited on the add/edit page; null while adding a new one. */
    var editing by mutableStateOf<QuickCommand?>(null)

    /** Position of the command whose action page is shown, or -1 when none. */
    var actionPosition by mutableIntStateOf(-1)

    /** Current text of the add/edit page inputs. */
    var nameInput by mutableStateOf("")
    var commandInput by mutableStateOf("")
}

/**
 * Content state of the floating terminal panel. The panel stays attached (and its TerminalView
 * keeps its session) while it is minimized, so restoring it shows the same output.
 */
class FloatingTerminalState {

    /** The command currently shown in the panel, used by the re-run button. */
    var command: String? = null

    /** Title shown in the panel title bar, i.e. the shortened working directory. */
    var title by mutableStateOf("~")

    /** Bumped when a fresh session must be started in the panel's TerminalView. */
    var sessionVersion by mutableIntStateOf(0)
}

/**
 * One row of the quick commands panel's restore page: a built-in default command together
 * with whether it already exists in the list (in which case it cannot be selected again).
 */
data class RestoreRow(
    val index: Int,
    val name: String,
    val command: String,
    val alreadyInList: Boolean,
    val checked: Boolean,
)

/** File metadata read with the directory listing, never during composition. */
data class DrawerFileItem(
    val file: File,
    val isDirectory: Boolean,
    val isApk: Boolean,
) {
    val name: String = file.name
}

/**
 * One storage accordion card in the left drawer, as read by the Compose drawer. The
 * implementation keeps every value in Compose state so the drawer recomposes when a card is
 * expanded, a directory is entered or its listing changes.
 */
interface DrawerGroupUi {

    /** Label of the card ("内部存储", "外部存储", ...). */
    val title: String

    /** Whether the card is expanded. */
    val expanded: Boolean

    /** Whether a directory listing is being read in the background. */
    val isLoading: Boolean

    /** Path shown under the title, relative to the storage root when inside it. */
    val pathText: String

    /** Entries of the current directory: directories first, then files, both by name. */
    val items: List<DrawerFileItem>

    /** Whether the "go up" button is shown, i.e. the card is inside a subdirectory. */
    val canGoUp: Boolean

    /** Expand or collapse the card. */
    fun toggleExpanded()

    /** Navigate one directory up, never past the storage root. */
    fun goUp()

    /** Enter a directory, expanding the card. */
    fun enterDirectory(dir: File)

    /** Handle a tap on a listed file. */
    fun openFile(file: File)

    /** Copy the current directory path to the clipboard. */
    fun copyPath()
}
