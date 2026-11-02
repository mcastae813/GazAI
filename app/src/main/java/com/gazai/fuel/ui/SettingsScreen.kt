package com.gazai.fuel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.gazai.fuel.AppViewModel

@Composable
fun SettingsScreen(vm: AppViewModel) {
    var draft by remember { mutableStateOf(vm.settings) }
    var saved by remember { mutableStateOf(false) }
    fun edit(s: com.gazai.fuel.data.Settings) { draft = s; saved = false }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Text("OpenRouter", style = MaterialTheme.typography.titleMedium)
        Text(
            "Create a free key at openrouter.ai/keys. GazAI only uses models whose price is 0 " +
                "(the \":free\" ones), picking one automatically and falling back if it is busy.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            draft.apiKey, { edit(draft.copy(apiKey = it)) },
            label = { Text("API key") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            draft.textModel, { edit(draft.copy(textModel = it)) },
            label = { Text("Text model (blank = auto)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            draft.visionModel, { edit(draft.copy(visionModel = it)) },
            label = { Text("Photo model (blank = auto)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )

        Text("Preferences", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(checked = draft.imperial, onCheckedChange = { edit(draft.copy(imperial = it)) })
            Text("Imperial units (mi, US gal, mpg)")
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(checked = draft.autoSave, onCheckedChange = { edit(draft.copy(autoSave = it)) })
            Text("Add entries automatically when AI finds fuel and cost")
        }
        OutlinedTextField(
            draft.currency, { edit(draft.copy(currency = it.take(4))) },
            label = { Text("Currency symbol") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )

        Button(onClick = { vm.updateSettings(draft); saved = true }) { Text("Save settings") }
        if (saved) Text("Saved.", color = MaterialTheme.colorScheme.primary)

        Text(
            "Tip: share a receipt photo or text from any app to GazAI and it will be read automatically. " +
                "Your key and data stay on this phone; photos are sent to OpenRouter only when you ask for AI reading.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
