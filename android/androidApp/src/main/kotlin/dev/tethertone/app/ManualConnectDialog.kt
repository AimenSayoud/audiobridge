package dev.tethertone.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import dev.tethertone.shared.PairingInfo

/**
 * The escape hatch for when the camera is not an option: a phone with the
 * lens covered, a Mac on a headless screen, a token pasted from a terminal.
 */
@Composable
fun ManualConnectDialog(
    initial: PairingInfo?,
    onDismiss: () -> Unit,
    onConnect: (PairingInfo) -> Unit,
) {
    var host by remember { mutableStateOf(initial?.hosts?.firstOrNull().orEmpty()) }
    var port by remember { mutableStateOf((initial?.port ?: 45678).toString()) }
    var token by remember { mutableStateOf(initial?.token.orEmpty()) }

    val portNumber = port.toIntOrNull()
    val valid = host.isNotBlank() && portNumber != null && portNumber in 1..65535

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect manually") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "The server prints its address and token next to the QR code.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it.trim() },
                    label = { Text("Host") },
                    placeholder = { Text("10.0.0.5") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit).take(5) },
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it.trim() },
                    label = { Text("Token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onConnect(PairingInfo(listOf(host), portNumber ?: 45678, token, name = host))
                },
            ) { Text("Connect") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
