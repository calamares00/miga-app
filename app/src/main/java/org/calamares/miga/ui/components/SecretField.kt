package org.calamares.miga.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import org.calamares.miga.L10n
import org.calamares.miga.R

/**
 * A [TextFieldState] kept in step with a plain [value] and [onValueChange], as the screens hold
 * their text in view models: typing reports the new text, and a [value] changed from outside
 * (dictation, the assistant, a reset) replaces the text with the cursor at the end.
 */
@Composable
fun rememberSyncedTextFieldState(value: String, onValueChange: (String) -> Unit): TextFieldState {
    val state = rememberTextFieldState(value, TextRange(value.length))
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    LaunchedEffect(value) {
        if (state.text.toString() != value) state.setTextAndPlaceCursorAtEnd(value)
    }
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { text -> if (text != currentValue) currentOnValueChange(text) }
    }
    return state
}

/**
 * Field for a password, key or token: hidden while typed (the last character shows for a moment,
 * as the system does), with a button to show it, and without suggestions or autocorrect.
 */
@Composable
fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    placeholder: String? = null,
    isError: Boolean = false,
    supporting: String? = null,
    imeAction: ImeAction = ImeAction.Done
) {
    val state = rememberSyncedTextFieldState(value, onValueChange)
    var visible by remember { mutableStateOf(false) }
    OutlinedSecureTextField(
        state = state,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = supporting?.let { { Text(it) } },
        isError = isError,
        textObfuscationMode = if (visible) TextObfuscationMode.Visible else TextObfuscationMode.RevealLastTyped,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = imeAction),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = L10n.str(if (visible) R.string.hide_password else R.string.show_password)
                )
            }
        },
        modifier = modifier
    )
}
