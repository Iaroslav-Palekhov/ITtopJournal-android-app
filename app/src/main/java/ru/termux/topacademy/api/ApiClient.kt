package ru.termux.topacademy.api

import ru.termux.topacademy.utils.SharedPreferencesHelper
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    private const val BASE_URL = "https://msapi.top-academy.ru/api/v2/"

    // Общие заголовки для всех запросов
    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:142.0) Gecko/20100101 Firefox/142.0",
        "Accept" to "application/json, text/plain, */*",
        "Accept-Language" to "ru_RU, ru",
        "Sec-GPC" to "1",
        "Sec-Fetch-Dest" to "empty",
        "Sec-Fetch-Mode" to "cors",
        "Sec-Fetch-Site" to "same-site",
        "Referer" to "https://journal.top-academy.ru/"
    )

    private fun createHttpClient(interceptors: List<Interceptor>): OkHttpClient {
        val builder = OkHttpClient.Builder()

        // Добавляем логирование
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        builder.addInterceptor(loggingInterceptor)

        // Добавляем все переданные интерцепторы
        interceptors.forEach { builder.addInterceptor(it) }

        return builder.build()
    }

    fun provideRetrofit(sharedPrefs: SharedPreferencesHelper): Retrofit {
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()

            // Добавляем общие заголовки
            commonHeaders.forEach { (key, value) ->
                requestBuilder.header(key, value)
            }

            // Добавляем авторизацию если есть токен
            val token = sharedPrefs.accessToken
            if (!token.isNullOrEmpty()) {
                requestBuilder.header("Authorization", "Bearer $token")
            } else {
                requestBuilder.header("Authorization", "Bearer null")
            }

            val request = requestBuilder.build()
            chain.proceed(request)
        }

        val client = createHttpClient(listOf(authInterceptor))

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    fun provideRetrofitWithoutToken(): Retrofit {
        val commonHeadersInterceptor = Interceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()

            // Добавляем общие заголовки
            commonHeaders.forEach { (key, value) ->
                requestBuilder.header(key, value)
            }

            // Для логина Content-Type должен быть application/json
            requestBuilder.header("Content-Type", "application/json")

            // Не добавляем Authorization заголовок
            val request = requestBuilder.build()
            chain.proceed(request)
        }

        val client = createHttpClient(listOf(commonHeadersInterceptor))

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}