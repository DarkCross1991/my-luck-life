package life.myluck.w124.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import life.myluck.w124.core.GarageState
import life.myluck.w124.core.TripType
import life.myluck.w124.ui.theme.Bg
import life.myluck.w124.ui.theme.Gold
import life.myluck.w124.ui.theme.Muted
import java.time.LocalDate

@Composable
fun FuelComposerScreen(
    garage: GarageState,
    ui: GarageUi,
    vm: GarageViewModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val draft = ui.receiptDraft
    val parsed = draft?.parsed
    val focus = remember { FocusRequester() }
    var odo by remember(garage.odometer.km) { mutableStateOf("") }
    var liters by remember(parsed?.liters) {
        mutableStateOf(parsed?.liters?.let { trimNum(it) }.orEmpty())
    }
    var price by remember(parsed?.pricePerLiter) {
        mutableStateOf(parsed?.pricePerLiter?.let { trimNum(it) }.orEmpty())
    }
    var total by remember(parsed?.totalCost) {
        mutableStateOf(parsed?.totalCost?.let { trimNum(it) }.orEmpty())
    }
    var date by remember(parsed?.date) { mutableStateOf(parsed?.date ?: LocalDate.now().toString()) }
    var full by remember { mutableStateOf(true) }
    var trip by remember { mutableStateOf(ui.lastTripType) }
    var error by remember { mutableStateOf<String?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importReceipt(context, it, context.contentResolver.getType(it)) }
    }

    LaunchedEffect(parsed) {
        parsed?.liters?.let { liters = trimNum(it) }
        parsed?.pricePerLiter?.let { price = trimNum(it) }
        parsed?.totalCost?.let { total = trimNum(it) }
        parsed?.date?.let { date = it }
    }
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
    }

    Surface(Modifier.fillMaxSize(), color = Bg) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Заправка", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onClose) { Text("Закрыть") }
            }
            Text(
                "Перешлите чек из банка через «Поделиться → Бортжурнал» или прикрепите тут. Дальше нужен только пробег.",
                color = Muted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { pick.launch(arrayOf("image/*", "application/pdf")) }) {
                    Text("Квитанция")
                }
                OutlinedButton(onClick = { vm.clearDraft() }) { Text("Без чека") }
            }
            if (ui.receiptBusy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Gold, strokeWidth = 2.dp)
                    Text("Читаю квитанцию…", color = Muted)
                }
            }
            draft?.previewPath?.let { path ->
                val bmp = remember(path) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = "Квитанция",
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            parsed?.station?.let { Text(it, color = Gold, fontWeight = FontWeight.Medium) }
            OutlinedTextField(
                value = odo,
                onValueChange = { odo = it.filter { ch -> ch.isDigit() } },
                label = { Text("Пробег, км") },
                placeholder = { Text("сейчас ${km(garage.odometer.km)}") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            OutlinedTextField(
                value = liters,
                onValueChange = { liters = it.replace(',', '.') },
                label = { Text("Литры") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = total,
                    onValueChange = { total = it.replace(',', '.') },
                    label = { Text("Сумма ₽") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it.replace(',', '.') },
                    label = { Text("₽/л") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            DateField(date) { date = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = full, onCheckedChange = { full = it })
                Text("Полный бак")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    TripType.MIXED to "Смеш.",
                    TripType.CITY to "Город",
                    TripType.HIGHWAY to "Трасса",
                    TripType.SHORT to "Коротк.",
                ).forEach { (value, label) ->
                    FilterChip(selected = trip == value, onClick = { trip = value }, label = { Text(label) })
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    val odoVal = odo.toIntOrNull()
                    val litersVal = liters.toDoubleOrNull()
                    when {
                        odoVal == null || odoVal < garage.odometer.km ->
                            error = "Пробег не меньше текущего (${km(garage.odometer.km)} км)"
                        litersVal == null || litersVal <= 0.0 ->
                            error = "Не разобрал литры — поправьте вручную"
                        else -> {
                            val priceVal = price.toDoubleOrNull()
                            val totalVal = total.toDoubleOrNull()
                                ?: if (priceVal != null) priceVal * litersVal else null
                            vm.addFuel(
                                date = date,
                                odometer = odoVal,
                                liters = litersVal,
                                full = full,
                                tripType = trip,
                                pricePerLiter = priceVal,
                                totalCost = totalVal,
                                note = null,
                                station = parsed?.station,
                                receiptText = draft?.text?.takeIf { it.isNotBlank() },
                            )
                            onClose()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Записать", fontSize = 18.sp) }
        }
    }
}

private fun trimNum(value: Double): String {
    val s = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
    return s.trimEnd('0').trimEnd('.')
}
