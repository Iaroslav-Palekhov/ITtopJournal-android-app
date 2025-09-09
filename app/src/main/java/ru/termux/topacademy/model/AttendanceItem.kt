package ru.termux.topacademy.model

data class AttendanceItem(
    val date_visit: String?,
    val spec_name: String?, // Название предмета
    val teacher_name: String?,
    val status_was: Int?,   // 1 - был, 0 - не был
    val class_work_mark: String? // Оценка
)