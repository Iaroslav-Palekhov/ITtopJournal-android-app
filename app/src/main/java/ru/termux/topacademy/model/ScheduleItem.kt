package ru.termux.topacademy.model

data class ScheduleItem(
    val lesson: Int?,
    val started_at: String?,
    val finished_at: String?,
    val subject_name: String?,
    val teacher_name: String?,
    val room_name: String?
)