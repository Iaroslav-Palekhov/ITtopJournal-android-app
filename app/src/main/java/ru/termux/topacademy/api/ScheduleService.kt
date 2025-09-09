package ru.termux.topacademy.api

import ru.termux.topacademy.model.ScheduleItem
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface ScheduleService {
    @GET("schedule/operations/get-by-date")
    suspend fun getScheduleByDate(@Query("date_filter") date: String): Response<List<ScheduleItem>>
}