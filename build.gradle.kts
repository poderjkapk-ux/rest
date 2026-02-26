// Top-level build file
plugins {
    // Указываем версии всех плагинов в одном месте
    id("com.android.application") version "8.2.2" apply false
    id("com.android.library") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false

    // Плагин Google Services для работы Firebase в проекте rest
    id("com.google.gms.google-services") version "4.4.1" apply false
}