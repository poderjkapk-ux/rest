package com.restify.rest

import android.preference.PreferenceManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.net.URL
import java.net.URLEncoder

// Модель для результатов поиска адреса
data class AddressResult(val name: String, val lat: Double, val lon: Double)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateOrderScreen(viewModel: MainViewModel, onOrderCreated: () -> Unit) {
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf("50") }
    var comment by remember { mutableStateOf("") }
    var paymentType by remember { mutableStateOf("prepaid") }
    var isReturnRequired by remember { mutableStateOf(false) }

    // Состояние для интерактивной кнопки
    var isSubmitting by remember { mutableStateOf(false) }

    // Состояние карты и автодополнения
    var mapCenter by remember { mutableStateOf(GeoPoint(46.4825, 30.7233)) } // Одесса по умолчанию
    var addressSuggestions by remember { mutableStateOf(emptyList<AddressResult>()) }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val scrollState = rememberScrollState()

    // Настройка ТЕМНОЙ темы
    val backgroundColor = Color(0xFF121212) // Глубокий черный
    val cardColor = Color(0xFF1E1E1E) // Темно-серый для карточек
    val primaryAccent = Color(0xFFBB86FC) // Классический фиолетовый акцент (можно заменить на свой)
    val textColor = Color.White
    val textSecondaryColor = Color(0xFFAAAAAA)
    val dividerColor = Color(0xFF333333)

    // Универсальные цвета для полей ввода
    val darkTextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = primaryAccent,
        unfocusedBorderColor = dividerColor,
        focusedContainerColor = cardColor,
        unfocusedContainerColor = cardColor,
        focusedTextColor = textColor,
        unfocusedTextColor = textColor,
        cursorColor = primaryAccent,
        focusedLabelColor = primaryAccent,
        unfocusedLabelColor = textSecondaryColor
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "Новая доставка",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        // Блок адреса с картой
        PremiumCard(cardColor = cardColor) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Куда везем?", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = textSecondaryColor)
                Spacer(modifier = Modifier.height(12.dp))

                // Ввод адреса с автодополнением
                ExposedDropdownMenuBox(
                    expanded = isDropdownExpanded,
                    onExpandedChange = { isDropdownExpanded = !isDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { newValue ->
                            address = newValue
                            isDropdownExpanded = true
                            // Debounce логика для поиска в OpenStreetMap
                            searchJob?.cancel()
                            searchJob = coroutineScope.launch {
                                delay(600) // Ждем 600мс после последнего ввода, чтобы не спамить API
                                if (newValue.length > 2) {
                                    addressSuggestions = fetchAddressesFromOSM(newValue)
                                } else {
                                    addressSuggestions = emptyList()
                                }
                            }
                        },
                        placeholder = { Text("Введите адрес доставки...", color = textSecondaryColor) },
                        leadingIcon = { Icon(Icons.Outlined.Place, contentDescription = "Address", tint = primaryAccent) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(), // Привязывает выпадающий список к этому полю
                        shape = RoundedCornerShape(12.dp),
                        colors = darkTextFieldColors,
                        singleLine = true
                    )

                    // Выпадающий список с результатами поиска
                    if (addressSuggestions.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = isDropdownExpanded,
                            onDismissRequest = { isDropdownExpanded = false },
                            modifier = Modifier.background(cardColor)
                        ) {
                            addressSuggestions.forEach { suggestion ->
                                DropdownMenuItem(
                                    text = { Text(suggestion.name, color = textColor, maxLines = 2) },
                                    onClick = {
                                        address = suggestion.name
                                        mapCenter = GeoPoint(suggestion.lat, suggestion.lon)
                                        isDropdownExpanded = false
                                        addressSuggestions = emptyList()
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Контейнер карты
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    OpenStreetMapComponent(centerPoint = mapCenter)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Блок данных клиента
        PremiumCard(cardColor = cardColor) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Детали заказа", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = textSecondaryColor)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    placeholder = { Text("Телефон клиента", color = textSecondaryColor) },
                    leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = "Phone", tint = primaryAccent) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = darkTextFieldColors
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        placeholder = { Text("Сумма (₴)", color = textSecondaryColor) },
                        leadingIcon = { Icon(Icons.Outlined.AttachMoney, contentDescription = "Price", tint = primaryAccent) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = darkTextFieldColors
                    )
                    OutlinedTextField(
                        value = fee,
                        onValueChange = { fee = it },
                        placeholder = { Text("Доставка (₴)", color = textSecondaryColor) },
                        leadingIcon = { Icon(Icons.Outlined.LocalShipping, contentDescription = "Fee", tint = primaryAccent) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = darkTextFieldColors
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Блок оплаты и настроек
        PremiumCard(cardColor = cardColor) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Оплата", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = textSecondaryColor)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PaymentOption("Оплачено", Icons.Outlined.CheckCircle, paymentType == "prepaid", primaryAccent) { paymentType = "prepaid" }
                    PaymentOption("Наличные", Icons.Outlined.Money, paymentType == "cash", primaryAccent) { paymentType = "cash" }
                    PaymentOption("Выкуп", Icons.Outlined.ShoppingBag, paymentType == "buyout", primaryAccent) { paymentType = "buyout" }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = dividerColor, thickness = 1.dp)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isReturnRequired,
                        onCheckedChange = { isReturnRequired = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = primaryAccent,
                            uncheckedColor = textSecondaryColor,
                            checkmarkColor = backgroundColor
                        )
                    )
                    Text("Нужен возврат денег в заведение", fontSize = 15.sp, color = textColor)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = comment,
            onValueChange = { comment = it },
            placeholder = { Text("Комментарий курьеру...", color = textSecondaryColor) },
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            shape = RoundedCornerShape(16.dp),
            colors = darkTextFieldColors
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Интерактивная кнопка подтверждения
        Button(
            onClick = {
                isSubmitting = true
                val request = OrderCreateRequest(
                    address = address,
                    phone = phone,
                    price = price.toDoubleOrNull() ?: 0.0,
                    fee = fee.toDoubleOrNull() ?: 50.0,
                    comment = comment,
                    paymentType = paymentType,
                    isReturnRequired = isReturnRequired
                )
                viewModel.createNewOrder(request) {
                    isSubmitting = false
                    onOrderCreated()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryAccent,
                disabledContainerColor = dividerColor
            ),
            enabled = address.isNotEmpty() && phone.isNotEmpty() && !isSubmitting
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    color = backgroundColor,
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp
                )
            } else {
                Text("ОТПРАВИТЬ КУРЬЕРУ", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = backgroundColor)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// Функция для выполнения запроса к бесплатному API OpenStreetMap (Nominatim)
suspend fun fetchAddressesFromOSM(query: String): List<AddressResult> = withContext(Dispatchers.IO) {
    try {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        // limit=5 — получаем топ-5 результатов, addressdetails=1 — просим детали адреса
        val urlString = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&addressdetails=1&limit=5"
        val connection = URL(urlString).openConnection() as java.net.HttpURLConnection
        connection.setRequestProperty("User-Agent", "RestifyPartnerApp/1.0")
        connection.connectTimeout = 3000
        connection.readTimeout = 3000

        if (connection.responseCode == 200) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(response)
            val results = mutableListOf<AddressResult>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)

                // Извлекаем объект address для точечной сборки строки
                val addressObj = obj.optJSONObject("address")
                var shortAddressName = obj.getString("display_name") // Фолбэк на полное имя, если что-то пойдет не так

                if (addressObj != null) {
                    val road = addressObj.optString("road", "")
                    val houseNumber = addressObj.optString("house_number", "")
                    // Город может быть записан как city, town или village в зависимости от размера населенного пункта
                    val city = addressObj.optString("city",
                        addressObj.optString("town",
                            addressObj.optString("village", "")))

                    // Собираем только те части, которые не пустые
                    val parts = listOf(road, houseNumber, city).filter { it.isNotBlank() }

                    if (parts.isNotEmpty()) {
                        shortAddressName = parts.joinToString(", ")
                    }
                }

                results.add(
                    AddressResult(
                        name = shortAddressName,
                        lat = obj.getString("lat").toDouble(),
                        lon = obj.getString("lon").toDouble()
                    )
                )
            }
            results
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

@Composable
fun PremiumCard(cardColor: Color, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = cardColor,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        content()
    }
}

@Composable
fun PaymentOption(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val color = if (isSelected) accentColor else Color(0xFFAAAAAA)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Icon(icon, contentDescription = text, tint = color, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = color)
    }
}

@Composable
fun OpenStreetMapComponent(centerPoint: GeoPoint) {
    val context = LocalContext.current

    remember {
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
        Configuration.getInstance().userAgentValue = context.packageName
    }

    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(17.0)
                controller.setCenter(centerPoint)
            }
        },
        update = { view ->
            // Плавная анимация карты к новой точке при выборе адреса
            view.controller.animateTo(centerPoint)
        },
        modifier = Modifier.fillMaxSize()
    )
}