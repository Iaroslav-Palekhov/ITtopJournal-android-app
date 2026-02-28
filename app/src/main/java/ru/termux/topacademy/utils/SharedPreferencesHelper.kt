//package ru.termux.topacademy.utils
//
//import android.content.Context
//import android.content.SharedPreferences
//
//class SharedPreferencesHelper(context: Context) {
//    private val prefs: SharedPreferences = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
//
//    var accessToken: String?
//        get() = prefs.getString("access_token", null)
//        set(value) = prefs.edit().putString("access_token", value).apply()
//
//    var username: String?
//        get() = prefs.getString("username", null)
//        set(value) = prefs.edit().putString("username", value).apply()
//
//    var password: String?
//        get() = prefs.getString("password", null)
//        set(value) = prefs.edit().putString("password", value).apply()
//
//    fun clear() {
//        prefs.edit().clear().apply()
//    }
//}





package ru.termux.topacademy.utils

import android.content.Context
import android.content.SharedPreferences
import ru.termux.topacademy.model.UserInfo

class SharedPreferencesHelper(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

    var accessToken: String?
        get() = sharedPreferences.getString("access_token", null)
        set(value) = sharedPreferences.edit().putString("access_token", value).apply()

    var username: String?
        get() = sharedPreferences.getString("username", null)
        set(value) = sharedPreferences.edit().putString("username", value).apply()

    var password: String?
        get() = sharedPreferences.getString("password", null)
        set(value) = sharedPreferences.edit().putString("password", value).apply()

    var groupId: Int
        get() = sharedPreferences.getInt("current_group_id", 0)
        set(value) = sharedPreferences.edit().putInt("current_group_id", value).apply()

    var studentId: Int
        get() = sharedPreferences.getInt("student_id", 0)
        set(value) = sharedPreferences.edit().putInt("student_id", value).apply()

    var fullName: String
        get() = sharedPreferences.getString("full_name", "") ?: ""
        set(value) = sharedPreferences.edit().putString("full_name", value).apply()

    // Метод для сохранения всей информации о пользователе
    fun saveUserInfo(userInfo: UserInfo) {
        groupId = userInfo.current_group_id
        studentId = userInfo.student_id
        fullName = userInfo.full_name
    }

    fun clear() {
        sharedPreferences.edit().clear().apply()
    }
}