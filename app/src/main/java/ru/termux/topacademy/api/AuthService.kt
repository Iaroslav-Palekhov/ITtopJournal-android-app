package ru.termux.topacademy.api

import ru.termux.topacademy.model.AuthResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class LoginRequest(
    val application_key: String = "6a56a5df2667e65aab73ce76d1dd737f7d1faef9c52e8b8c55ac75f565d8e8a6",
    val id_city: Any? = null,
    val username: String,
    val password: String
)

interface AuthService {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>
}