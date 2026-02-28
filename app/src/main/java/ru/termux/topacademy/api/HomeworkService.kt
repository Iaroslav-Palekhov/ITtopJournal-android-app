package ru.termux.topacademy.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*
import ru.termux.topacademy.model.HomeworkItem
import ru.termux.topacademy.model.HomeworkStudent
import ru.termux.topacademy.model.UserInfo

interface HomeworkService {
    @GET("homework/operations/list")
    suspend fun getHomeworkList(
        @Query("page") page: Int,
        @Query("status") status: Int,
        @Query("type") type: Int = 0,
        @Query("group_id") groupId: Int
    ): Response<List<HomeworkItem>>

    // Создать/отправить решение домашнего задания
    @Multipart
    @POST("homework/operations/create")
    suspend fun submitHomework(
        @Part("id") id: RequestBody,
        @Part file: MultipartBody.Part? = null,
        @Part("answerText") answerText: RequestBody? = null,
        @Part("spentTimeHour") spentTimeHour: RequestBody? = null,
        @Part("spentTimeMin") spentTimeMin: RequestBody? = null
    ): Response<HomeworkStudent>
}