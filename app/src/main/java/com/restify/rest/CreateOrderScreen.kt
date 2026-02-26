package com.restify.rest

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CreateOrderScreen(viewModel: MainViewModel, onOrderCreated: () -> Unit) {
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf("50") } // Дефолтная цена доставки
    var comment by remember { mutableStateOf("") }
    var paymentType by remember { mutableStateOf("prepaid") } // prepaid, cash, buyout
    var isReturnRequired by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Новая доставка", fontSize = 22.sp, modifier = Modifier.padding(bottom = 16.dp))

        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("Адрес доставки") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Телефон клиента") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = price,
                onValueChange = { price = it },
                label = { Text("Сумма заказа") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = fee,
                onValueChange = { fee = it },
                label = { Text("Доставка (₴)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }

        Text("Тип оплаты:", modifier = Modifier.padding(top = 16.dp))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            RadioButton(selected = paymentType == "prepaid", onClick = { paymentType = "prepaid" })
            Text("Оплачено")
            RadioButton(selected = paymentType == "cash", onClick = { paymentType = "cash" })
            Text("Наличные")
            RadioButton(selected = paymentType == "buyout", onClick = { paymentType = "buyout" })
            Text("Выкуп")
        }

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = isReturnRequired, onCheckedChange = { isReturnRequired = it })
            Text("Нужен возврат денег в заведение")
        }

        OutlinedTextField(
            value = comment,
            onValueChange = { comment = it },
            label = { Text("Комментарий курьеру") },
            modifier = Modifier.fillMaxWidth().height(100.dp)
        )

        Button(
            onClick = {
                val request = OrderCreateRequest(
                    address = address,
                    phone = phone,
                    price = price.toDoubleOrNull() ?: 0.0,
                    fee = fee.toDoubleOrNull() ?: 50.0,
                    comment = comment,
                    paymentType = paymentType,
                    isReturnRequired = isReturnRequired
                )
                viewModel.createNewOrder(request, onOrderCreated)
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            enabled = address.isNotEmpty() && phone.isNotEmpty()
        ) {
            Text("ОТПРАВИТЬ КУРЬЕРАМ")
        }
    }
}