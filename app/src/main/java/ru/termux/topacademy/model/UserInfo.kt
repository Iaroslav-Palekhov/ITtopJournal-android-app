// UserInfo.kt
package ru.termux.topacademy.model

data class UserInfo(
    val current_group_id: Int,
    val full_name: String,
    val student_id: Int,
    val groups: List<Group>,
    val group_name: String,
    val photo: String?,
    val birthday: String?,
    val age: Int,
    val achievements_count: Int,
    val last_date_visit: String,
    val registration_date: String,
    val stream_id: Int,
    val stream_name: String,
    val study_form_short_name: String,
    val gender: Int,
    val gaming_points: List<GamingPoint>,
    val spent_gaming_points: List<GamingPoint>,
    val level: Int,
    val visibility: Visibility,
    val manual_link: String?
)

data class Group(
    val id: Int,
    val name: String,
    val is_primary: Boolean,
    val group_status: Int
)

data class GamingPoint(
    val new_gaming_point_types__id: Int,
    val points: Int
)

data class Visibility(
    val is_design: Boolean,
    val is_video_courses: Boolean,
    val is_vacancy: Boolean,
    val is_signal: Boolean,
    val is_promo: Boolean
)