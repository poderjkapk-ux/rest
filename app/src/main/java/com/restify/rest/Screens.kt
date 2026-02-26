package com.restify.rest

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PartnerDashboardScreen(viewModel: MainViewModel) {
    val orders by viewModel.orders.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Активні замовлення",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (viewModel.isLoading.value && orders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (orders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Замовлень поки немає", color = Color.Gray, fontSize = 16.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(orders) { order ->
                    OrderCard(order, onReadyClick = { viewModel.markOrderAsReady(order.id) })
                }
            }
        }
    }
}

@Composable
fun OrderCard(order: PartnerOrder, onReadyClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Замовлення #${order.id}", fontWeight = FontWeight.Bold, fontSize = 18.sp)

                val (statusText, statusColor) = when (order.status.lowercase()) {
                    "delivered" -> "Доставлено" to Color(0xFF4CAF50)
                    "pending" -> "Очікує" to Color(0xFFFFA000)
                    "in_progress" -> "В дорозі" to Color(0xFF2196F3)
                    else -> order.status to Color.Gray
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = statusText.uppercase(),
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "📍 ${order.address}", fontSize = 15.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "💰 Сума: ${order.orderPrice} ₴ (Доставка: ${order.deliveryFee} ₴)", fontSize = 15.sp)

            val paymentText = when (order.paymentType) {
                "prepaid" -> "✅ Оплачено"
                "cash" -> "💵 Готівка"
                "buyout" -> "🛍️ Викуп"
                else -> order.paymentType
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = paymentText, fontSize = 15.sp, fontWeight = FontWeight.Medium)

            order.courier?.let {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🛵 Кур'єр: ${it.name}", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(text = "⭐ ${it.rating}", fontWeight = FontWeight.Bold, color = Color(0xFFFFC107))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!order.isReady && order.status != "delivered") {
                Button(
                    onClick = onReadyClick,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("📦 ЗАМОВЛЕННЯ ГОТОВЕ", fontWeight = FontWeight.Bold)
                }
            } else if (order.isReady && order.status != "delivered") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF5F5F5)
                ) {
                    Text(
                        text = "🍳 Очікує кур'єра",
                        modifier = Modifier.padding(12.dp),
                        color = Color.DarkGray,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}