package ru.termux.topacademy

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import ru.termux.topacademy.api.ApiClient
import ru.termux.topacademy.api.AttendanceService
import ru.termux.topacademy.model.AttendanceItem
import ru.termux.topacademy.utils.SharedPreferencesHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit

class AttendanceActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferencesHelper
    private lateinit var retrofit: Retrofit
    private lateinit var attendanceService: AttendanceService
    private lateinit var linearLayoutAttendance: LinearLayout
    private lateinit var progressBarAttendance: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance)

        prefs = SharedPreferencesHelper(this)
        retrofit = ApiClient.provideRetrofit(prefs)
        attendanceService = retrofit.create(AttendanceService::class.java)

        linearLayoutAttendance = findViewById(R.id.linearLayoutAttendance)
        progressBarAttendance = findViewById(R.id.progressBarAttendance)

        loadAttendance()
    }

    private fun loadAttendance() {
        linearLayoutAttendance.removeAllViews()
        progressBarAttendance.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = attendanceService.getAttendance()
                withContext(Dispatchers.Main) {
                    progressBarAttendance.visibility = View.GONE
                    if (response.isSuccessful) {
                        val attendanceList = response.body() ?: emptyList()
                        if (attendanceList.isEmpty()) {
                            addAttendanceItemView("Данные о посещаемости отсутствуют.", "", "", false, "")
                        } else {
                            // Сортируем по дате, самые свежие сверху
                            val sortedList = attendanceList.sortedByDescending { it.date_visit }
                            for (item in sortedList) {
                                addAttendanceItemView(
                                    item.date_visit ?: "—",
                                    item.spec_name ?: "Неизвестно",
                                    item.teacher_name ?: "—",
                                    item.status_was == 1,
                                    item.class_work_mark ?: "—"
                                )
                            }
                        }
                    } else {
                        Toast.makeText(this@AttendanceActivity, "❌ Ошибка загрузки посещаемости", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBarAttendance.visibility = View.GONE
                    Toast.makeText(this@AttendanceActivity, "⚠️ ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun addAttendanceItemView(date: String, subject: String, teacher: String, wasPresent: Boolean, grade: String) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(android.R.color.white))
            setPadding(16, 16, 16, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
        }

        val statusIcon = if (wasPresent) "✅" else "❌"
        val statusText = if (wasPresent) "Был" else "Не был"

        val statusView = TextView(this).apply {
            text = "$statusIcon $statusText ($date)"
            textSize = 16f
            setTextColor(getColor(android.R.color.black))
        }

        val subjectView = TextView(this).apply {
            text = "📚 $subject"
            textSize = 14f
            setTextColor(getColor(android.R.color.darker_gray))
        }

        val teacherView = TextView(this).apply {
            text = "👨‍🏫 $teacher"
            textSize = 14f
            setTextColor(getColor(android.R.color.darker_gray))
        }

        val gradeView = TextView(this).apply {
            text = "⭐ Оценка: $grade"
            textSize = 14f
            setTextColor(getColor(android.R.color.darker_gray))
        }

        container.addView(statusView)
        container.addView(subjectView)
        container.addView(teacherView)
        container.addView(gradeView)

        linearLayoutAttendance.addView(container)
    }
}