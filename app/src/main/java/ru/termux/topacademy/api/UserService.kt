package ru.termux.topacademy.api

import retrofit2.Response
import retrofit2.http.GET
import ru.termux.topacademy.model.UserInfo

interface UserService {
    @GET("settings/user-info")
    suspend fun getUserInfo(): Response<UserInfo>
}