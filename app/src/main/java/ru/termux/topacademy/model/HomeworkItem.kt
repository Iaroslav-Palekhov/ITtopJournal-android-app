// HomeworkItem.kt
package ru.termux.topacademy.model

data class HomeworkItem(
    val id: Int,
    val id_spec: Int,
    val id_teach: Int,
    val id_group: Int,
    val fio_teach: String,
    val name_spec: String,
    val theme: String,
    val comment: String?,
    val creation_time: String,
    val completion_time: String,
    val overdue_time: String,
    val cover_image: String?,
    val file_path: String?,
    val filename: String?,
    val status: Int,
    val common_status: Int?,
    val homework_stud: HomeworkStudent?,
    val homework_comment: HomeworkComment?
)

data class HomeworkStudent(
    val id: Int,
    val stud_answer: String?,
    val filename: String?,
    val file_path: String?,
    val creation_time: String,
    val mark: Int?,
    val auto_mark: Boolean,
    val tmp_file: String?
)

data class HomeworkComment(
    val text_comment: String?,
    val attachment: String?,
    val attachment_path: String?,
    val date_updated: String
)