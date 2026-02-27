package com.restify.rest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun PartnerDashboardScreen(viewModel: MainViewModel) {
    val orders by viewModel.orders.collectAsState()
    val chatHistory by viewModel.chatMessages.collectAsState()
    // Стан для оцінених замовлень (обов'язково додайте _ratedOrders у MainViewModel, як я писав раніше)
    val ratedOrders by viewModel.ratedOrders.collectAsState(initial = emptySet())

    var chatOrderId by remember { mutableStateOf<Int?>(null) }
    var rateOrderId by remember { mutableStateOf<Int?>(null) }
    var mapCoordinates by remember { mutableStateOf<Pair<Double, Double>?>(null) } // Координати для внутрішньої карти
    var selectedTab by remember { mutableStateOf(0) } // 0 - Активні, 1 - Виконані

    val context = LocalContext.current

    // Автоматичне оновлення списку при отриманні пуш-сповіщення
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                viewModel.fetchOrders()
            }
        }
        val filter = IntentFilter("com.restify.rest.UPDATE_ORDERS")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    // Розділення списків замовлень
    val activeOrders = orders.filter { it.status != "delivered" && it.status != "cancelled" }
    val completedOrders = orders.filter { it.status == "delivered" || it.status == "cancelled" }
    val displayedOrders = if (selectedTab == 0) activeOrders else completedOrders

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Шапка
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Замовлення",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                IconButton(onClick = { viewModel.fetchOrders() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Оновити", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }

        // Вкладки (Таби)
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Активні (${activeOrders.size})") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Виконані (${completedOrders.size})") }
            )
        }

        // Контент
        Box(modifier = Modifier.fillMaxSize()) {
            if (viewModel.isLoading.value && orders.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (displayedOrders.isEmpty()) {
                EmptyStateView()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(displayedOrders, key = { it.id }) { order ->
                        OrderCard(
                            order = order,
                            isRated = ratedOrders.contains(order.id),
                            onReadyClick = { viewModel.markOrderAsReady(order.id) },
                            onChatClick = {
                                chatOrderId = order.id
                                viewModel.loadChatHistory(order.id)
                            },
                            onTrackClick = {
                                viewModel.trackCourier(
                                    jobId = order.id,
                                    onSuccess = { lat, lon ->
                                        // Відкриваємо карту всередині додатку
                                        mapCoordinates = Pair(lat, lon)
                                    },
                                    onError = { errorMsg -> viewModel.errorMessage.value = errorMsg }
                                )
                            },
                            onRateClick = { rateOrderId = order.id }
                        )
                    }
                }
            }
        }
    }

    // Діалоги
    if (chatOrderId != null) {
        ChatDialog(
            orderId = chatOrderId!!,
            messages = chatHistory,
            onSendMessage = { msg -> viewModel.sendMessage(chatOrderId!!, msg) },
            onDismiss = {
                chatOrderId = null
                viewModel.clearChat()
            }
        )
    }

    if (rateOrderId != null) {
        RateCourierDialog(
            orderId = rateOrderId!!,
            onDismiss = { rateOrderId = null },
            onSubmit = { rating, review ->
                viewModel.rateCourier(rateOrderId!!, rating, review)
                rateOrderId = null
            }
        )
    }

    // Вікно з картою всередині додатку
    mapCoordinates?.let { (lat, lon) ->
        MapDialog(
            lat = lat,
            lon = lon,
            onDismiss = { mapCoordinates = null }
        )
    }
}

@Composable
fun EmptyStateView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Color.LightGray
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Замовлень поки немає",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Gray
        )
        Text(
            text = "Ви можете створити нове замовлення",
            fontSize = 14.sp,
            color = Color.LightGray,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun OrderCard(
    order: PartnerOrder,
    isRated: Boolean,
    onReadyClick: () -> Unit,
    onChatClick: () -> Unit,
    onTrackClick: () -> Unit,
    onRateClick: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(300)),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Заголовок та статус
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Замовлення #${order.id}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                val (statusText, statusColor, bgColor) = when (order.status.lowercase()) {
                    "delivered" -> Triple("Доставлено", Color(0xFF2E7D32), Color(0xFFE8F5E9))
                    "pending" -> Triple("Очікує", Color(0xFFE65100), Color(0xFFFFF3E0))
                    "in_progress", "picked_up", "assigned" -> Triple("В дорозі", Color(0xFF1565C0), Color(0xFFE3F2FD))
                    else -> Triple(order.status, Color.DarkGray, Color(0xFFF5F5F5))
                }

                Surface(
                    shape = CircleShape,
                    color = bgColor
                ) {
                    Text(
                        text = statusText.uppercase(),
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Інформація про замовлення
            InfoRow(icon = Icons.Default.LocationOn, text = order.address)
            InfoRow(icon = Icons.Default.ShoppingCart, text = "Сума: ${order.orderPrice} ₴ (Дост: ${order.deliveryFee} ₴)")

            val paymentIcon = when (order.paymentType) {
                "prepaid" -> Icons.Default.CheckCircle
                "cash" -> Icons.Default.Check
                else -> Icons.Default.Info
            }
            val paymentText = when (order.paymentType) {
                "prepaid" -> "Оплачено онлайн"
                "cash" -> "Готівка при отриманні"
                "buyout" -> "Викуп кур'єром"
                else -> order.paymentType
            }
            InfoRow(icon = paymentIcon, text = paymentText, iconTint = Color(0xFF4CAF50))

            // Інформація про кур'єра
            AnimatedVisibility(visible = order.courier != null) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.padding(8.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = order.courier?.name ?: "", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "${order.courier?.rating}", fontSize = 14.sp, color = Color.Gray)
                            }
                        }
                    }

                    // Кнопки взаємодії
                    if (order.status != "delivered" && order.status != "cancelled") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ActionIconButton(
                                icon = Icons.Default.Phone,
                                color = Color(0xFF4CAF50),
                                onClick = {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${order.courier?.phone}"))
                                    context.startActivity(intent)
                                }
                            )
                            ActionIconButton(
                                icon = Icons.Default.Email,
                                color = Color(0xFF2196F3),
                                onClick = onChatClick
                            )
                            ActionIconButton(
                                icon = Icons.Default.Place,
                                color = Color(0xFFFF9800),
                                onClick = onTrackClick
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Головні кнопки дій
            // Кнопка з'являється ТІЛЬКИ якщо є кур'єр і замовлення не готове
            if (!order.isReady && order.status != "delivered" && order.courier != null) {
                Button(
                    onClick = onReadyClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ЗАМОВЛЕННЯ ГОТОВЕ", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            } else if (order.isReady && order.status != "delivered") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // ИСПРАВЛЕНИЕ 3: Заменили CircularProgressIndicator на зеленую галочку
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Готово. Очікує кур'єра", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (order.status == "delivered" && !isRated) {
                // Кнопка оцінки не показується, якщо ми вже натиснули її
                Button(
                    onClick = onRateClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107), contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ОЦІНИТИ КУР'ЄРА", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun InfoRow(icon: ImageVector, text: String, iconTint: Color = Color.Gray) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ActionIconButton(icon: ImageVector, color: Color, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.15f),
        modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.padding(14.dp)
        )
    }
}

@Composable
fun ChatDialog(
    orderId: Int,
    messages: List<ChatMessage>,
    onSendMessage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var messageText by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.8f)
        ) {
            Column {
                // Шапка чату
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Закрити", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Чат з кур'єром",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Список повідомлень
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    reverseLayout = false
                ) {
                    items(messages) { msg ->
                        val isPartner = msg.role == "partner"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = if (isPartner) Arrangement.End else Arrangement.Start
                        ) {
                            Surface(
                                shape = RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isPartner) 16.dp else 4.dp,
                                    bottomEnd = if (isPartner) 4.dp else 16.dp
                                ),
                                color = if (isPartner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.widthIn(max = 280.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = msg.text,
                                        fontSize = 15.sp,
                                        color = if (isPartner) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = msg.time,
                                        fontSize = 10.sp,
                                        modifier = Modifier
                                            .align(Alignment.End)
                                            .padding(top = 4.dp),
                                        color = if (isPartner) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Поле вводу
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp)
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Написати повідомлення...") },
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                onSendMessage(messageText)
                                messageText = ""
                            }
                        },
                        modifier = Modifier.size(50.dp),
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Відправити")
                    }
                }
            }
        }
    }
}

@Composable
fun RateCourierDialog(orderId: Int, onDismiss: () -> Unit, onSubmit: (Int, String) -> Unit) {
    var rating by remember { mutableStateOf(5) }
    var review by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Оцініть доставку", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Наскільки ви задоволені кур'єром?", fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))

                Spacer(modifier = Modifier.height(24.dp))

                Row(horizontalArrangement = Arrangement.Center) {
                    for (i in 1..5) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = if (i <= rating) Color(0xFFFFC107) else Color.LightGray,
                            modifier = Modifier
                                .size(44.dp)
                                .clickable { rating = i }
                                .padding(4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = review,
                    onValueChange = { review = it },
                    label = { Text("Короткий відгук (за бажанням)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Скасувати", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSubmit(rating, review) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("Відправити", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// Нове вікно з інтерактивною картою всередині додатку (OpenStreetMap)
@Composable
fun MapDialog(lat: Double, lon: Double, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.8f),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Заголовок вікна карти
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Локація кур'єра", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Закрити", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }

                // Вбудований WebView для відображення карти Leaflet.js (OpenStreetMap)
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            // ИСПРАВЛЕНИЕ 2.1: Включаем DOM Storage для карты
                            settings.domStorageEnabled = true
                            webViewClient = WebViewClient()

                            val htmlData = """
                                <!DOCTYPE html>
                                <html>
                                <head>
                                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                                    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
                                    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                                    <style>
                                        body { margin: 0; padding: 0; }
                                        #map { width: 100vw; height: 100vh; }
                                    </style>
                                </head>
                                <body>
                                    <div id="map"></div>
                                    <script>
                                        var map = L.map('map').setView([$lat, $lon], 16);
                                        
                                        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                                            attribution: '&copy; OpenStreetMap contributors'
                                        }).addTo(map);
                                        
                                        L.marker([$lat, $lon]).addTo(map)
                                            .bindPopup('Кур\'єр').openPopup();
                                    </script>
                                </body>
                                </html>
                            """.trimIndent()

                            // ИСПРАВЛЕНИЕ 2.2: Добавляем базовый URL вместо null
                            loadDataWithBaseURL("https://localhost", htmlData, "text/html", "UTF-8", null)
                        }
                    }
                )
            }
        }
    }
}