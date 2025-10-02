package ru.termux.topacademy.api

import retrofit2.Response
import retrofit2.http.GET
import ru.termux.topacademy.model.Review

interface ReviewService {
    @GET("reviews/index/list")
    suspend fun getReviews(): Response<List<Review>>
}