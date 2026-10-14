package org.calamares.miga.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.export.BackupCrypto

/** Rough strength of a backup password, only to guide the user (it is never enforced beyond the minimum length). */
internal fun passwordStrength(password: String): Int {
    if (password.length < BackupCrypto.MIN_PASSWORD_LENGTH) return 0
    val kinds = listOf(
        password.any { it.isLowerCase() },
        password.any { it.isUpperCase() },
        password.any { it.isDigit() },
        password.any { !it.isLetterOrDigit() }
    ).count { it }
    return when {
        password.length >= 14 || (password.length >= 10 && kinds >= 3) -> 3
        password.length >= 10 || kinds >= 3 -> 2
        else -> 1
    }
}

@Composable
private fun PasswordField(value: String, onValueChange: (String) -> Unit, label: String, isError: Boolean = false, supporting: String? = null) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        supportingText = supporting?.let { { Text(it) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrect = false, imeAction = ImeAction.Next),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = L10n.str(if (visible) R.string.hide_password else R.string.show_password)
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Asked before creating a backup: optionally protect it with a password (entered twice). [onConfirm]
 * gets the password, or null for an unprotected backup.
 */
@Composable
fun ExportBackupDialog(onConfirm: (password: CharArray?) -> Unit, onDismiss: () -> Unit) {
    var protect by remember { mutableStateOf(true) }
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val tooShort = password.length < BackupCrypto.MIN_PASSWORD_LENGTH
    val mismatch = repeat.isNotEmpty() && repeat != password
    val canCreate = !protect || (!tooShort && repeat == password)
    val strength = passwordStrength(password)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L10n.str(R.string.export_whole_app)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(L10n.str(R.string.backup_protect_with_password), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            L10n.str(R.string.backup_protect_explanation),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(checked = protect, onCheckedChange = { protect = it })
                }
                if (protect) {
                    PasswordField(
                        value = password,
                        onValueChange = { password = it },
                        label = L10n.str(R.string.backup_password),
                        supporting = when {
                            password.isEmpty() -> L10n.str(R.string.backup_password_min_x, BackupCrypto.MIN_PASSWORD_LENGTH)
                            tooShort -> L10n.str(R.string.backup_password_min_x, BackupCrypto.MIN_PASSWORD_LENGTH)
                            else -> L10n.str(
                                when (strength) {
                                    3 -> R.string.backup_password_strong
                                    2 -> R.string.backup_password_good
                                    else -> R.string.backup_password_weak
                                }
                            )
                        }
                    )
                    PasswordField(
                        value = repeat,
                        onValueChange = { repeat = it },
                        label = L10n.str(R.string.backup_password_repeat),
                        isError = mismatch,
                        supporting = if (mismatch) L10n.str(R.string.backup_passwords_dont_match) else null
                    )
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                            Icon(
                                Icons.Filled.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                L10n.str(R.string.backup_password_cannot_be_recovered),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(if (protect) password.toCharArray() else null) },
                enabled = canCreate
            ) { Text(L10n.str(R.string.backup_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L10n.str(R.string.cancel)) } }
    )
}

/** Asked when restoring an encrypted backup; [wrongPassword] after a failed attempt. */
@Composable
fun BackupPasswordPromptDialog(wrongPassword: Boolean, onSubmit: (CharArray) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L10n.str(R.string.backup_encrypted_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(L10n.str(R.string.backup_encrypted_enter_password), style = MaterialTheme.typography.bodyMedium)
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = L10n.str(R.string.backup_password),
                    isError = wrongPassword,
                    supporting = if (wrongPassword) L10n.str(R.string.backup_wrong_password) else null
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(password.toCharArray()) }, enabled = password.isNotEmpty()) {
                Text(L10n.str(R.string.backup_open))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L10n.str(R.string.cancel)) } }
    )
}
