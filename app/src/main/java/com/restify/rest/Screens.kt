package com.restify.rest

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Основной экран со списком заказов заведения
@Composable
fun PartnerDashboardScreen(viewModel: MainViewModel) {
    val orders by viewModel.orders.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Заказы заведения",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (viewModel.isLoading.value) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(orders) { order ->
                    OrderCard(order, onReadyClick = { viewModel.markOrderAsReady(order.id) })
                }
            }
        }
    }
}

// Карточка заказа (версия для ресторана)
@Composable
fun OrderCard(order: PartnerOrder, onReadyClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Заказ #${order.id}", fontWeight = FontWeight.Bold)
                Text(
                    text = order.status.uppercase(),
                    color = if (order.status == "delivered") Color(0xFF4CAF50) else Color(0xFFF44336)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Адрес: ${order.address}")
            Text(text = "Сумма: ${order.orderPrice} ₴")

            // Информация о курьере, если он назначен
            order.courier?.let {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Text(text = "Курьер: ${it.name}", fontWeight = FontWeight.Medium)
                Text(text = "Рейтинг: ⭐ ${it.rating}", fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Кнопка "Готов", если заказ еще не отмечен как готовый
            if (!order.isReady && order.status != "delivered") {
                Button(
                    onClick = onReadyClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                ) {
                    Text("ЗАКАЗ ГОТОВ")
                }
            } else if (order.isReady) {
                Text(
                    text = "🍳 Ожидает курьера",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = Color.Gray
                )
            }
        }
    }
}
