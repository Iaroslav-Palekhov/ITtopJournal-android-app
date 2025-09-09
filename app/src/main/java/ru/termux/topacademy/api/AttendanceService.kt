package ru.termux.topacademy.api

import ru.termux.topacademy.model.AttendanceItem
import retrofit2.Response
import retrofit2.http.GET

interface AttendanceService {
    @GET("progress/operations/student-visits")
    suspend fun getAttendance(): Response<List<AttendanceItem>>
}