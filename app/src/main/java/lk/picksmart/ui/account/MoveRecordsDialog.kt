package lk.picksmart.ui.account

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import lk.picksmart.R
import lk.picksmart.domain.usecase.MoveRecordsQuestion

/**
 * Asked once, when an account signs in for the first time on a phone with records made without
 * one: move them to the account (a new account starts with them, an existing one adds them to its
 * backup), or keep them separate (owner decisions 2026-10-27, `DataSpaces.kt`). It needs an answer,
 * so back and taps outside don't close it.
 */
@Composable
fun MoveRecordsDialog(question: MoveRecordsQuestion, onAnswer: (move: Boolean) -> Unit) {
    val newAccount = question == MoveRecordsQuestion.NEW_ACCOUNT
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = {
            Text(stringResource(if (newAccount) R.string.auth_move_records_title else R.string.auth_merge_records_title))
        },
        text = {
            Text(stringResource(if (newAccount) R.string.auth_move_records_body else R.string.auth_merge_records_body))
        },
        confirmButton = {
            TextButton(onClick = { onAnswer(true) }) {
                Text(stringResource(if (newAccount) R.string.auth_move_records_move else R.string.auth_merge_records_merge))
            }
        },
        dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text(stringResource(R.string.auth_move_records_keep)) } },
    )
}
