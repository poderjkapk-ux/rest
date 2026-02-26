package com.restify.rest

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(private val api: RestPartnerApi) : ViewModel() {

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn

    private val _orders = MutableStateFlow<List<PartnerOrder>>(emptyList())
    val orders: StateFlow<List<PartnerOrder>> = _orders

    // Стейт для збереження історії чату конкретного замовлення
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages

    val isLoading = mutableStateOf(false)
    val errorMessage = mutableStateOf<String?>(null)

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

    // --- НОВІ ФУНКЦІЇ ДЛЯ ЧАТУ ТА ВІДСТЕЖЕННЯ ---

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
                // Відправляємо повідомлення на сервер
                val response = api.sendChatMessage(jobId, message)
                if (response.isSuccessful) {
                    // Оновлюємо історію чату, щоб побачити нове повідомлення
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
                    fetchOrders() // Оновлюємо список, щоб прибрати кнопку "Оцінити"
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
}