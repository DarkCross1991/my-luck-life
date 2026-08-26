package life.myluck.w124.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import life.myluck.w124.ui.theme.Muted

@Composable
fun SettingsScreen(
    snapshot: GithubSettings,
    hasToken: Boolean,
    onBack: () -> Unit,
    onSave: (token: String, owner: String, repo: String, branch: String) -> Unit,
) {
    var token by remember { mutableStateOf(snapshot.token) }
    var owner by remember { mutableStateOf(snapshot.owner) }
    var repo by remember { mutableStateOf(snapshot.repo) }
    var branch by remember { mutableStateOf(snapshot.branch) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Источник правды — git. С телефона пишем заправки, пробег и заметки. " +
                "С компьютера агент дописывает analytics.md и регламент узлов.",
            color = Muted,
        )
        Text("GitHub", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Personal Access Token") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Fine-grained token: доступ Contents Read and write к репозиторию my-luck-life. " +
                "Токен живёт только на телефоне.",
            color = Muted,
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(value = owner, onValueChange = { owner = it }, label = { Text("Owner") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = repo, onValueChange = { repo = it }, label = { Text("Репозиторий") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = branch, onValueChange = { branch = it }, label = { Text("Ветка") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text(
            if (hasToken) "Токен задан. После сохранения журнал уедет в data/w124/."
            else "Без токена всё работает офлайн и никуда не уезжает.",
            color = Muted,
        )
        Button(
            onClick = { onSave(token, owner, repo, branch) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Сохранить и синхронизировать") }
        TextButton(onClick = onBack) { Text("Назад") }
        Spacer(Modifier.height(12.dp))
        Text("Как пользоваться", style = MaterialTheme.typography.titleMedium)
        Text(
            "1. Ставите APK.\n" +
                "2. На каждой заправке пишете литры, пробег и полный бак / долив.\n" +
                "3. Тип поездки (город/трасса/короткие) нужен, чтобы отличать стиль езды от прожорливости мотора.\n" +
                "4. Узлы закрываете кнопкой «Сделано».\n" +
                "5. С компьютера я читаю тот же state.json и дописываю разбор в analytics.md.",
            color = Muted,
        )
        Row { }
    }
}
