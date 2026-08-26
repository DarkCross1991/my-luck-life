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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import life.myluck.w124.core.AppRelease
import life.myluck.w124.update.UpdateUi
import life.myluck.w124.ui.theme.Gold
import life.myluck.w124.ui.theme.Muted

@Composable
fun SettingsScreen(
    snapshot: GithubSettings,
    hasToken: Boolean,
    updates: UpdateUi?,
    onBack: () -> Unit,
    onSave: (token: String, owner: String, repo: String, branch: String) -> Unit,
    onCheckUpdates: () -> Unit,
    onInstall: (AppRelease) -> Unit,
    onAllowInstalls: () -> Unit,
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
        Text("Приложение", style = MaterialTheme.typography.titleMedium)
        if (updates != null) {
            Text("Сейчас ${updates.currentName} (сборка ${updates.currentCode})", fontWeight = FontWeight.Medium)
            updates.message?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
            if (!updates.canInstallPackages) {
                Text("Нужно разрешить установку из Бортжурнала — иначе обновление не поставится.", color = Gold)
                OutlinedButton(onClick = onAllowInstalls, modifier = Modifier.fillMaxWidth()) {
                    Text("Разрешить установку APK")
                }
            }
            Button(
                onClick = onCheckUpdates,
                enabled = !updates.checking && !updates.downloading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (updates.checking) "Проверяю…" else "Проверить обновления")
            }
            val latest = updates.latest
            if (latest != null && latest.versionCode > updates.currentCode) {
                Button(
                    onClick = { onInstall(latest) },
                    enabled = !updates.downloading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (updates.downloading) "Скачиваю…" else "Обновить до ${latest.versionName}")
                }
            }
            val older = updates.history.filter { it.versionCode < updates.currentCode }
            if (older.isNotEmpty()) {
                Text("Откат", style = MaterialTheme.typography.titleSmall)
                Text("Поставится предыдущий APK из GitHub Releases. Android спросит подтверждение.", color = Muted)
                older.take(5).forEach { release ->
                    OutlinedButton(
                        onClick = { onInstall(release) },
                        enabled = !updates.downloading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Откатить на ${release.versionName}")
                    }
                }
            }
        }

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
                "Токен живёт только на телефоне. Для обновлений APK токен не обязателен.",
            color = Muted,
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(value = owner, onValueChange = { owner = it }, label = { Text("Owner") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = repo, onValueChange = { repo = it }, label = { Text("Репозиторий") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = branch, onValueChange = { branch = it }, label = { Text("Ветка") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text(
            if (hasToken) "Токен задан. После сохранения журнал уедет в data/w124/."
            else "Без токена журнал на телефоне, обновления приложения всё равно проверяются.",
            color = Muted,
        )
        Button(
            onClick = { onSave(token, owner, repo, branch) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Сохранить и синхронизировать") }
        TextButton(onClick = onBack) { Text("Назад") }
        Spacer(Modifier.height(12.dp))
        Text("Как заправлять", style = MaterialTheme.typography.titleMedium)
        Text(
            "1. Оплатили на АЗС с телефона.\n" +
                "2. В банке «Поделиться» → Бортжурнал (фото, PDF или текст чека).\n" +
                "3. Проверьте литры и впишите пробег — всё.\n" +
                "4. Тип поездки нужен, чтобы отличать город от прожорливости мотора.",
            color = Muted,
        )
        Row { }
    }
}
