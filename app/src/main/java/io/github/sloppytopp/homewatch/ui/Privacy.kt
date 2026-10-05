package io.github.sloppytopp.homewatch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sloppytopp.homewatch.data.Prefs
import io.github.sloppytopp.homewatch.scan.Monitor

/** Deliberately generic: shows nothing about what the app is for. */
@Composable
fun LockScreen() {
    var pin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var fails by remember { mutableIntStateOf(0) }
    var until by remember { mutableLongStateOf(0L) }
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Enter PIN", color = UiColors.text, fontSize = 20.sp)
        OutlinedTextField(
            value = pin, onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pin = it }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
        Button(
            onClick = {
                val now = System.currentTimeMillis()
                when {
                    now < until -> err = "Too many tries. Wait a moment."
                    Prefs.checkPin(pin) -> { Monitor.locked = false; pin = ""; fails = 0; err = null }
                    else -> { fails++; pin = ""; err = "Wrong PIN"; if (fails >= 5) { until = now + 30_000; fails = 0; err = "Too many tries. Wait 30 seconds." } }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = UiColors.buttonBg, contentColor = UiColors.buttonFg),
        ) { Text("Unlock") }
        err?.let { Text(it, color = UiColors.warn, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable
fun PrivacySection() {
    val ctx = LocalContext.current
    var pinDialog by remember { mutableStateOf(false) }
    var pin1 by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Privacy and discreet mode", color = UiColors.text, fontSize = 16.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Discreet mode", color = UiColors.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Switch(checked = Prefs.discreet, onCheckedChange = { Prefs.saveDiscreet(it) })
        }
        Text("Notifications say only \"Update - tap to open\" with a plain icon and are hidden on the lock screen. The app is hidden from the recent-apps list and screenshots are blocked.",
            color = UiColors.dim, fontSize = 12.sp)

        Text("App name and icon on the home screen", color = UiColors.dim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("N0RMA", "Notes", "Weather").forEachIndexed { i, name ->
                val on = Prefs.launcher == i
                Button(
                    onClick = { Prefs.saveLauncher(ctx, i); msg = "Done. Your launcher may take a few seconds to show the new name and icon." },
                    colors = ButtonDefaults.buttonColors(containerColor = if (on) UiColors.buttonBg else androidx.compose.ui.graphics.Color(0xFF111111),
                        contentColor = if (on) UiColors.buttonFg else UiColors.dim),
                ) { Text(name, fontSize = 12.sp) }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (Prefs.hasPin) "PIN lock is ON (asks after 30 s away)" else "PIN lock is off", color = UiColors.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { if (Prefs.hasPin) { Prefs.savePin(null); msg = "PIN removed." } else { pin1 = ""; pinDialog = true } }) {
                Text(if (Prefs.hasPin) "Remove PIN" else "Set a PIN")
            }
        }
        Text("The \"✕\" at the top of every screen closes N0RMA and removes it from recent apps in one tap.", color = UiColors.dim, fontSize = 12.sp)
        Text("Honest limits: Android's own Settings > Apps list still shows this app, and someone with your phone unlocked and enough time can find it. " +
            "This only raises the bar against a quick look. There is no PIN recovery by design - if you forget it, clear the app's data in Android Settings.",
            color = UiColors.faint, fontSize = 11.sp)
        msg?.let { Text(it, color = UiColors.warn, fontSize = 12.sp) }
    }

    if (pinDialog) AlertDialog(
        containerColor = UiColors.dialogBg, titleContentColor = UiColors.text, textContentColor = UiColors.text,
        onDismissRequest = { pinDialog = false },
        title = { Text("Choose a PIN") },
        text = {
            Column {
                Text("4 to 8 digits. It is stored only on this phone, as a salted hash.", fontSize = 12.sp)
                OutlinedTextField(
                    value = pin1, onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pin1 = it }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = pin1.length >= 4, onClick = { Prefs.savePin(pin1); pinDialog = false; msg = "PIN set. Remember it - it can't be recovered." }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = { pinDialog = false }) { Text("Cancel") } },
    )
}
