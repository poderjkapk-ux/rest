package com.restify.rest

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.google.firebase.messaging.FirebaseMessaging
import com.restify.rest.ui.theme.RestTheme
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// Експортуємо об'єкт RetrofitClient, щоб він був доступний у MyFirebaseMessagingService
object RetrofitClient {
    lateinit var apiService: RestPartnerApi
}

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

    // Ресивер для автоматичного оновлення UI при отриманні пуш-сповіщення
    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.restify.rest.UPDATE_ORDERS") {
                Log.d("MainActivity", "Отримано сигнал на оновлення замовлень")
                if (::viewModel.isInitialized) {
                    viewModel.fetchOrders() // Оновлюємо список
                }
            }
        }
    }

    // Ланчер для запиту дозволу на сповіщення (Android 13+)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("MainActivity", "Дозвіл на сповіщення надано")
        } else {
            Log.w("MainActivity", "Дозвіл на сповіщення відхилено - пуші для чату не працюватимуть")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Запитуємо дозвіл на показ пуш-сповіщень
        askNotificationPermission()

        val cookieJar = object : CookieJar {
            private val cookieStore = HashMap<String, List<Cookie>>()
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                cookieStore[url.host()] = cookies
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore[url.host()] ?: ArrayList()
            }
        }

        // Створюємо OkHttpClient
        val client = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://restify.site")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val api = retrofit.create(RestPartnerApi::class.java)

        // Ініціалізуємо глобальний RetrofitClient
        RetrofitClient.apiService = api

        val viewModelFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                // ДОДАНО: Передаємо `client` у MainViewModel для роботи WebSocket
                return MainViewModel(api, client) as T
            }
        }

        // Отримуємо ViewModel
        viewModel = ViewModelProvider(this, viewModelFactory)[MainViewModel::class.java]

        // Отримуємо FCM токен поточного пристрою та зберігаємо його
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("MainActivity", "Не вдалося отримати FCM токен", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result
            Log.d("MainActivity", "Ваш FCM Token: $token")

            // Зберігаємо токен локально
            val sharedPref = getSharedPreferences("PartnerPrefs", Context.MODE_PRIVATE)
            sharedPref.edit().putString("pending_fcm_token", token).apply()

            // Якщо кукі вже є (ми залогінені), відправляємо токен на сервер
            val cookie = sharedPref.getString("cookie", null)
            if (cookie != null) {
                viewModel.sendFcmToken(cookie, token)
            }
        }

        // ДОДАНО: Підключаємо WebSocket, якщо користувач вже авторизований при старті додатку
        val sharedPref = getSharedPreferences("PartnerPrefs", Context.MODE_PRIVATE)
        val cookie = sharedPref.getString("cookie", null)
        if (cookie != null) {
            viewModel.connectWebSocket()
        }

        // Реєструємо BroadcastReceiver для прослуховування оновлень
        val filter = IntentFilter("com.restify.rest.UPDATE_ORDERS")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(updateReceiver, filter)
        }

        setContent {
            RestTheme {
                MainAppScreen(viewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Обов'язково знімаємо реєстрацію ресивера, щоб уникнути витоків пам'яті
        unregisterReceiver(updateReceiver)
    }

    private fun askNotificationPermission() {
        // Для Android 13 (API Level 33) і вище потрібно явно запитувати дозвіл
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                // Дозвіл вже є
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                // Можна показати діалог-пояснення, чому потрібні сповіщення (для чату)
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // Прямий запит дозволу
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DAYBERG Партнер", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            if (isLoggedIn) {
                NavigationBar {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination

                    NavigationBarItem(
                        icon = { Icon(Icons.Default.List, contentDescription = "Замовлення") },
                        label = { Text("Замовлення") },
                        selected = currentDestination?.hierarchy?.any { it.route == "orders" } == true,
                        onClick = {
                            navController.navigate("orders") {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Add, contentDescription = "Нова доставка") },
                        label = { Text("Нова доставка") },
                        selected = currentDestination?.hierarchy?.any { it.route == "create_order" } == true,
                        onClick = {
                            navController.navigate("create_order") {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (isLoggedIn) "orders" else "login",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("login") {
                LoginScreen(viewModel) {
                    navController.navigate("orders") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            }
            composable("orders") {
                PartnerDashboardScreen(viewModel)
            }
            composable("create_order") {
                CreateOrderScreen(viewModel) {
                    navController.navigate("orders") {
                        popUpTo("orders") { inclusive = true }
                    }
                }
            }
        }
    }
}