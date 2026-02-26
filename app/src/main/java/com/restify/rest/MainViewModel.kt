package com.restify.rest

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONObject

class MainViewModel(
    private val api: RestPartnerApi,
    private val okHttpClient: OkHttpClient // Додано для WebSocket
) : ViewModel() {

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn

    private val _orders = MutableStateFlow<List<PartnerOrder>>(emptyList())
    val orders: StateFlow<List<PartnerOrder>> = _orders

    // Стейт для збереження історії чату конкретного замовлення
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages

    // Стейт для миттєвого приховування кнопки оцінки після натискання
    private val _ratedOrders = MutableStateFlow<Set<Int>>(emptySet())
    val ratedOrders: StateFlow<Set<Int>> = _ratedOrders

    val isLoading = mutableStateOf(false)
    val errorMessage = mutableStateOf<String?>(null)

    // Змінна для збереження поточного з'єднання WebSocket
    private var webSocket: WebSocket? = null

    // --- ЛОГІКА WEBSOCKET ---

    fun connectWebSocket() {
        // Якщо з'єднання вже існує, не створюємо нове
        if (webSocket != null) return

        val request = Request.Builder()
            .url("wss://restify.site/ws/partner") // Ендпоінт вашого бекенду
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocket", "З'єднання встановлено успішно!")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("WebSocket", "Отримано повідомлення: $text")
                try {
                    val json = JSONObject(text)
                    val type = json.optString("type")

                    // Обробляємо події від бекенда
                    when (type) {
                        "order_update", "new_order" -> {
                            // Оновлюємо список замовлень у реальному часі
                            fetchOrders()
                        }
                        "chat_message" -> {
                            // Якщо ми знаходимось у чаті цього замовлення, оновлюємо його
                            val jobId = json.optInt("job_id")
                            loadChatHistory(jobId)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WebSocket", "Помилка парсингу JSON з WebSocket", e)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocket", "З'єднання закрито: $reason")
                this@MainViewModel.webSocket = null
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocket", "Помилка з'єднання", t)
                this@MainViewModel.webSocket = null
            }
        })
    }

    fun disconnectWebSocket() {
        webSocket?.close(1000, "Відключення користувачем")
        webSocket = null
    }

    override fun onCleared() {
        super.onCleared()
        // Обов'язково закриваємо з'єднання при знищенні ViewModel
        disconnectWebSocket()
    }

    // --- ІСНУЮЧА ЛОГІКА API ---

    fun login(email: String, pass: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            try {
                val response = api.login(email, pass)
                if (response.isSuccessful) {
                    _isLoggedIn.value = true
                    fetchOrders()
                    connectWebSocket() // Підключаємо WebSocket після успішного входу
                    onSuccess()
                } else {
                    errorMessage.value = "Невірний логін або пароль"
                }
            } catch (e: Exception) {
                errorMessage.value = "Помилка мережі: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun fetchOrders() {
        viewModelScope.launch {
            isLoading.value = true
            try {
                val response = api.getOrders()
                if (response.isSuccessful) {
                    _orders.value = response.body() ?: emptyList()
                    errorMessage.value = null
                } else {
                    errorMessage.value = "Помилка завантаження: ${response.code()}"
                }
            } catch (e: Exception) {
                errorMessage.value = "Помилка мережі: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun markOrderAsReady(jobId: Int) {
        viewModelScope.launch {
            try {
                val response = api.markAsReady(jobId)
                if (response.isSuccessful) fetchOrders()
            } catch (e: Exception) {
                errorMessage.value = "Не вдалося оновити статус"
            }
        }
    }

    fun createNewOrder(request: OrderCreateRequest, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading.value = true
            try {
                val response = api.createOrder(
                    address = request.address,
                    phone = request.phone,
                    price = request.price,
                    fee = request.fee,
                    comment = request.comment,
                    paymentType = request.paymentType,
                    isReturn = request.isReturnRequired
                )
                if (response.isSuccessful) {
                    fetchOrders()
                    onSuccess()
                    errorMessage.value = null
                } else {
                    errorMessage.value = "Помилка при створенні замовлення"
                }
            } catch (e: Exception) {
                errorMessage.value = "Збій мережі"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun loadChatHistory(jobId: Int) {
        viewModelScope.launch {
            try {
                val response = api.getChatHistory(jobId)
                if (response.isSuccessful) {
                    _chatMessages.value = response.body() ?: emptyList()
                }
            } catch (e: Exception) {
                errorMessage.value = "Помилка завантаження чату"
            }
        }
    }

    fun clearChat() {
        _chatMessages.value = emptyList()
    }

    fun sendMessage(jobId: Int, message: String) {
        viewModelScope.launch {
            try {
                val response = api.sendChatMessage(jobId, message)
                if (response.isSuccessful) {
                    loadChatHistory(jobId)
                } else {
                    errorMessage.value = "Не вдалося відправити повідомлення"
                }
            } catch (e: Exception) {
                errorMessage.value = "Помилка мережі при відправці"
            }
        }
    }

    fun rateCourier(jobId: Int, rating: Int, review: String) {
        viewModelScope.launch {
            try {
                val response = api.rateCourier(jobId, rating, review)
                if (response.isSuccessful) {
                    _ratedOrders.value = _ratedOrders.value + jobId
                    fetchOrders()
                } else {
                    errorMessage.value = "Помилка при відправці оцінки"
                }
            } catch (e: Exception) {
                errorMessage.value = "Помилка мережі"
            }
        }
    }

    fun trackCourier(jobId: Int, onSuccess: (lat: Double, lon: Double) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val response = api.trackCourier(jobId)
                if (response.isSuccessful) {
                    val trackData = response.body()
                    if (trackData != null && trackData.status == "ok" && trackData.lat != null && trackData.lon != null) {
                        onSuccess(trackData.lat, trackData.lon)
                    } else {
                        onError("Кур'єр ще не призначений або координати недоступні")
                    }
                } else {
                    onError("Помилка сервера")
                }
            } catch (e: Exception) {
                onError("Помилка мережі")
            }
        }
    }

    fun sendFcmToken(cookie: String, token: String) {
        viewModelScope.launch {
            try {
                val response = api.sendFcmToken(cookie, token)
                if (response.isSuccessful) {
                    Log.d("MainViewModel", "FCM токен успішно відправлено на сервер при старті")
                } else {
                    Log.e("MainViewModel", "Помилка відправки токена (код: ${response.code()})")
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Помилка відправки FCM токена: ${e.message}")
            }
        }
    }
}