package com.restify.rest

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(private val api: RestPartnerApi) : ViewModel() {

    // Состояние списка заказов
    private val _orders = MutableStateFlow<List<PartnerOrder>>(emptyList())
    val orders: StateFlow<List<PartnerOrder>> = _orders

    // Состояние загрузки
    val isLoading = mutableStateOf(false)

    // Состояние ошибок
    val errorMessage = mutableStateOf<String?>(null)

    init {
        fetchOrders()
    }

    // Загрузка заказов заведения
    fun fetchOrders() {
        viewModelScope.launch {
            isLoading.value = true
            try {
                val response = api.getOrders()
                if (response.isSuccessful) {
                    _orders.value = response.body() ?: emptyList()
                    errorMessage.value = null
                } else {
                    errorMessage.value = "Ошибка загрузки: ${response.code()}"
                }
            } catch (e: Exception) {
                errorMessage.value = "Ошибка сети: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    // Отметка заказа как "Готов" (уведомляет курьера)
    fun markOrderAsReady(jobId: Int) {
        viewModelScope.launch {
            try {
                val response = api.markAsReady(jobId)
                if (response.isSuccessful) {
                    // Обновляем список локально или запрашиваем заново
                    fetchOrders()
                }
            } catch (e: Exception) {
                errorMessage.value = "Не удалось обновить статус"
            }
        }
    }

    // Логика создания нового заказа
    fun createNewOrder(request: OrderCreateRequest, onSuccess: () -> Unit) {
        viewModelScope.launch {
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
                } else {
                    errorMessage.value = "Ошибка при создании заказа"
                }
            } catch (e: Exception) {
                errorMessage.value = "Сбой сети"
            }
        }
    }
}