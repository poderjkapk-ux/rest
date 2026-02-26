package com.restify.rest

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.*

// Модели данных для ресторана
data class PartnerOrder(
    val id: Int,
    val status: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("dropoff_address") val address: String,
    @SerializedName("order_price") val orderPrice: Double,
    @SerializedName("delivery_fee") val deliveryFee: Double,
    @SerializedName("payment_type") val paymentType: String, // prepaid, cash, buyout
    @SerializedName("is_ready") val isReady: Boolean,
    val courier: CourierInfo?
)

data class CourierInfo(
    val name: String,
    val phone: String,
    val rating: Double
)

data class OrderCreateRequest(
    val address: String,
    val phone: String,
    val price: Double,
    val fee: Double,
    val comment: String,
    val paymentType: String,
    val isReturnRequired: Boolean
)

// --- НОВІ МОДЕЛІ ДЛЯ ЧАТУ ТА ТРЕКІНГУ ---

data class ChatMessage(
    val role: String, // 'partner' або 'courier'
    val text: String,
    val time: String
)

data class TrackCourierResponse(
    val status: String, // "ok" або "waiting", "error"
    val lat: Double?,
    val lon: Double?,
    val name: String?,
    val phone: String?,
    @SerializedName("job_status") val jobStatus: String?
)

// Интерфейс API для заведения
interface RestPartnerApi {

    @FormUrlEncoded
    @POST("/api/partner/login_native")
    suspend fun login(
        @Field("email") email: String,
        @Field("password") password: String
    ): Response<Unit>

    @GET("/api/partner/orders_native")
    suspend fun getOrders(): Response<List<PartnerOrder>>

    @FormUrlEncoded
    @POST("/api/partner/create_order_native")
    suspend fun createOrder(
        @Field("dropoff_address") address: String,
        @Field("customer_phone") phone: String,
        @Field("order_price") price: Double,
        @Field("delivery_fee") fee: Double,
        @Field("comment") comment: String,
        @Field("payment_type") paymentType: String,
        @Field("is_return_required") isReturn: Boolean
    ): Response<Unit>

    @FormUrlEncoded
    @POST("/api/partner/order_ready")
    suspend fun markAsReady(@Field("job_id") jobId: Int): Response<Unit>

    @FormUrlEncoded
    @POST("/api/partner/rate_courier")
    suspend fun rateCourier(
        @Field("job_id") jobId: Int,
        @Field("rating") rating: Int,
        @Field("review") review: String
    ): Response<Unit>

    // --- НОВІ ЕНДПОІНТИ ДЛЯ ЧАТУ ТА ВІДСТЕЖЕННЯ ---

    @GET("/api/chat/history/{job_id}")
    suspend fun getChatHistory(
        @Path("job_id") jobId: Int
    ): Response<List<ChatMessage>>

    @FormUrlEncoded
    @POST("/api/chat/send")
    suspend fun sendChatMessage(
        @Field("job_id") jobId: Int,
        @Field("message") message: String,
        @Field("role") role: String = "partner" // Завжди відправляємо як заклад
    ): Response<Unit>

    @GET("/api/partner/track_courier/{job_id}")
    suspend fun trackCourier(
        @Path("job_id") jobId: Int
    ): Response<TrackCourierResponse>
}