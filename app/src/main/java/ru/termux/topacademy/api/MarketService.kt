package ru.termux.topacademy.api

import ru.termux.topacademy.model.MarketItem
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface MarketService {
    @GET("market/customer/product/list")
    suspend fun getProducts(
        @Query("page") page: Int = 1,
        @Query("type") type: Int = 0
    ): Response<MarketResponse>
}

data class MarketResponse(
    val total_count: Int,
    val products_list: List<MarketItem>
)