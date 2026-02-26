package com.restify.rest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.restify.rest.ui.theme.RestTheme
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Инициализация Retrofit (укажите IP вашего сервера FastAPI)
        val retrofit = Retrofit.Builder()
            .baseUrl("https://restify.site")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val api = retrofit.create(RestPartnerApi::class.java)
        val viewModel = MainViewModel(api)

        setContent {
            RestTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                Scaffold(
                    floatingActionButton = {
                        // Показываем кнопку добавления только на главном экране
                        if (currentRoute == "dashboard") {
                            FloatingActionButton(onClick = { navController.navigate("create_order") }) {
                                Icon(Icons.Default.Add, contentDescription = "Добавить заказ")
                            }
                        }
                    },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.List, contentDescription = null) },
                                label = { Text("Заказы") },
                                selected = currentRoute == "dashboard",
                                onClick = { navController.navigate("dashboard") }
                            )
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "dashboard",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("dashboard") {
                            PartnerDashboardScreen(viewModel)
                        }
                        composable("create_order") {
                            CreateOrderScreen(viewModel, onOrderCreated = {
                                navController.popBackStack() // Возврат к списку после создания
                            })
                        }
                    }
                }
            }
        }
    }
}