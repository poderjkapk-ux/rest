package com.restify.rest

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    // Вызывается, когда Firebase генерирует новый токен для устройства
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_PARTNER", "Новый токен: $token")
        sendTokenToServer(token)
    }

    // Обработка входящего пуш-уведомления
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Бэкенд (Python) должен отправлять данные строго в поле `data`
        val title = remoteMessage.data["title"] ?: "DAYBERG Партнер"
        val body = remoteMessage.data["body"] ?: "У вас нове сповіщення"

        Log.d("FCM_PARTNER", "Отримано пуш: Title=$title, Body=$body")

        showNotification(title, body)

        // Отправляем сигнал для автоматического обновления списка заказов в UI (например, в MainActivity)
        val updateIntent = Intent("com.restify.rest.UPDATE_ORDERS")
        updateIntent.setPackage(packageName) // Ограничиваем рассылку только нашим приложением ради безопасности
        sendBroadcast(updateIntent)
    }

    // Отправка токена на бэкенд
    private fun sendTokenToServer(token: String) {
        // Получаем сохраненные куки (токен авторизации)
        // ВАЖНО: Убедитесь, что "PartnerPrefs" - это именно то имя, которое вы используете при логине!
        val sharedPref = getSharedPreferences("PartnerPrefs", Context.MODE_PRIVATE)
        val cookie = sharedPref.getString("cookie", null)

        if (cookie != null) {
            // Если пользователь авторизован, отправляем токен на сервер в фоновом потоке
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // ВАЖНО: Замените RetrofitClient.apiService на ваш объект Retrofit, если он называется иначе
                    RetrofitClient.apiService.sendFcmToken(cookie, token)
                    Log.d("FCM_PARTNER", "Токен успішно відправлено на сервер")
                } catch (e: Exception) {
                    Log.e("FCM_PARTNER", "Помилка відправки токена: ${e.message}")
                }
            }
        } else {
            // Если пользователь еще не авторизован, сохраняем токен локально.
            // При успешном логине в будущем, вы сможете прочитать его отсюда и отправить на сервер.
            sharedPref.edit().putString("pending_fcm_token", token).apply()
        }
    }

    // Создание и показ системного уведомления Android
    private fun showNotification(title: String, message: String) {
        val channelId = "partner_push_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Для Android 8.0 (API 26) и выше обязательно нужен NotificationChannel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Сповіщення для ресторанів",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Сповіщення про статуси замовлень та нові повідомлення"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        // FLAG_IMMUTABLE обязателен для современных версий Android (API 31+)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher) // Иконка уведомления (замените на нужную, если требуется)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true) // Уведомление закрывается при нажатии
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Высокий приоритет для всплывающих пушей
            .setContentIntent(pendingIntent)

        // Генерируем уникальный ID на основе времени, чтобы новые пуши не перезаписывали старые
        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notificationBuilder.build())
    }
}