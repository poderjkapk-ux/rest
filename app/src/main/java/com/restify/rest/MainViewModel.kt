package com.restify.rest

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(private val api: RestPartnerApi) : ViewModel() {

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn

    private val _orders = MutableStateFlow<List<PartnerOrder>>(emptyList())
    val orders: StateFlow<List<PartnerOrder>> = _orders

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages

    private val _ratedOrders = MutableStateFlow<Set<Int>>(emptySet())
    val ratedOrders: StateFlow<Set<Int>> = _ratedOrders

    // --- НОВІ ЗМІННІ ДЛЯ МІТОК ЧАТУ ---
    private val _unreadChats = MutableStateFlow<Set<Int>>(emptySet())
    val unreadChats: StateFlow<Set<Int>> = _unreadChats

    val isLoading = mutableStateOf(false)
    val errorMessage = mutableStateOf<String?>(null)

    private var pollingJob: Job? = null

    // --- ФОНОВЕ ОНОВЛЕННЯ (ЗАМІНА WEBSOCKET) ---
    fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (true) {
                try {
                    val response = api.getOrders()
                    if (response.isSuccessful) {
                        // Оновлюємо список тихо, без зміни isLoading, щоб екран не блимав
                        _orders.value = response.body() ?: emptyList()
                    }
                } catch (e: Exception) {
                    // Ігноруємо помилки мережі в фоні, щоб не спамити користувача
                }
                delay(5000) // Запит кожні 5 секунд
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }
    // ------------------------------------------

    fun login(email: String, pass: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            try {
                val response = api.login(email, pass)
                if (response.isSuccessful) {
                    _isLoggedIn.value = true
                    fetchOrders()
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

    fun boostOrder(jobId: Int) {
        viewModelScope.launch {
            isLoading.value = true
            try {
                val response = api.boostOrder(jobId, 10.0)
                if (response.isSuccessful) {
                    fetchOrders()
                } else {
                    errorMessage.value = "Не вдалося підняти ціну"
                }
            } catch (e: Exception) {
                errorMessage.value = "Помилка мережі"
            } finally {
                isLoading.value = false
            }
        }
    }

    // --- НОВИЙ МЕТОД: ПІДТВЕРДЖЕННЯ ПОВЕРНЕННЯ КОШТІВ ---
    fun confirmReturn(jobId: Int) {
        viewModelScope.launch {
            isLoading.value = true
            try {
                val response = api.confirmReturn(jobId)
                if (response.isSuccessful) {
                    fetchOrders() // Оновлюємо список замовлень після успішного підтвердження
                } else {
                    errorMessage.value = "Не вдалося підтвердити"
                }
            } catch (e: Exception) {
                errorMessage.value = "Помилка мережі"
            } finally {
                isLoading.value = false
            }
        }
    }
    // ---------------------------------------------------

    // --- МЕТОДИ ДЛЯ КЕРУВАННЯ МІТКАМИ ЧАТУ ---
    fun markChatAsUnread(jobId: Int) {
        _unreadChats.value = _unreadChats.value + jobId
    }

    fun markChatAsRead(jobId: Int) {
        _unreadChats.value = _unreadChats.value - jobId
    }
    // -----------------------------------------

    fun loadChatHistory(jobId: Int) {
        markChatAsRead(jobId) // Одразу знімаємо мітку непрочитаного, коли відкриваємо чат
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
                if (response.isSuccessful) loadChatHistory(jobId)
                else errorMessage.value = "Не вдалося відправити повідомлення"
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
                } else errorMessage.value = "Помилка при відправці оцінки"
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
                } else onError("Помилка сервера")
            } catch (e: Exception) {
                onError("Помилка мережі")
            }
        }
    }

    fun updateFcmToken(token: String) {
        viewModelScope.launch {
            try {
                val response = api.sendFcmToken(token)
                if (response.isSuccessful) Log.d("FCM_TOKEN", "Токен успішно оновлено")
            } catch (e: Exception) {
                Log.e("FCM_TOKEN", "Помилка відправки токена: ${e.message}")
            }
        }
    }
}