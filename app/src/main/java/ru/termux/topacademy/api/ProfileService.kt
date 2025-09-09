package ru.termux.topacademy.api

import ru.termux.topacademy.model.Profile
import retrofit2.Response
import retrofit2.http.GET

interface ProfileService {
    @GET("profile/operations/settings")
    suspend fun getProfile(): Response<Profile>
}