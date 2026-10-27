package lk.picksmart.ui.account

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import lk.picksmart.R

/**
 * Asked once, right after a new account is created on a phone with records made without one:
 * move them to the account, or keep them separate (owner decision 2026-10-27, `DataSpaces.kt`).
 * It needs an answer, so back and taps outside don't close it.
 */
@Composable
fun MoveRecordsDialog(onAnswer: (move: Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.auth_move_records_title)) },
        text = { Text(stringResource(R.string.auth_move_records_body)) },
        confirmButton = { TextButton(onClick = { onAnswer(true) }) { Text(stringResource(R.string.auth_move_records_move)) } },
        dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text(stringResource(R.string.auth_move_records_keep)) } },
    )
}
