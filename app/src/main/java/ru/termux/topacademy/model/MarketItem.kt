package ru.termux.topacademy.model

data class MarketItem(
    val id: Int,
    val title: String,
    val description: String,
    val file_name: String?, // URL изображения
    val quantity: Int,
    val prices: List<Price>?
)

data class Price(
    val point_type_id: Int, // 1 или 2
    val points_sum: Int     // Стоимость в баллах
)