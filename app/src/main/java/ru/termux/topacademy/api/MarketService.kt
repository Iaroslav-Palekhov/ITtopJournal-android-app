package ru.termux.topacademy.api

import ru.termux.topacademy.model.MarketItem
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import ru.termux.topacademy.PurchaseRequest

interface MarketService {
    @GET("market/customer/product/list")
    suspend fun getProducts(
        @Query("page") page: Int = 1,
        @Query("type") type: Int = 0
    ): Response<MarketResponse>

    // Новый метод для покупки товара
    @POST("market/customer/order/create")
    suspend fun purchaseProduct(@Body request: PurchaseRequest): Response<Any>
}

data class MarketResponse(
    val total_count: Int,
    val products_list: List<MarketItem>
)
