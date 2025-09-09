package ru.termux.topacademy

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import ru.termux.topacademy.api.ApiClient
import ru.termux.topacademy.api.ScheduleService
import ru.termux.topacademy.model.ScheduleItem
import ru.termux.topacademy.utils.SharedPreferencesHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferencesHelper
    private lateinit var retrofit: Retrofit
    private lateinit var scheduleService: ScheduleService

    private lateinit var textViewGreeting: TextView
    private lateinit var textViewScheduleDate: TextView // <-- НОВЫЙ TextView для отображения текущей даты
    private lateinit var linearLayoutSchedule: LinearLayout
    private lateinit var buttonProfile: Button
    private lateinit var buttonLogout: Button
    private lateinit var buttonAttendance: Button // <-- Кнопка для посещаемости
    private lateinit var buttonYesterday: Button // <-- Кнопка "Вчера"
    private lateinit var buttonToday: Button // <-- Кнопка "Сегодня"
    private lateinit var buttonTomorrow: Button // <-- Кнопка "Завтра"
    private lateinit var progressBarMain: ProgressBar

    private var currentDate: Calendar = Calendar.getInstance() // <-- Текущая выбранная дата

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = SharedPreferencesHelper(this)
        retrofit = ApiClient.provideRetrofit(prefs)
        scheduleService = retrofit.create(ScheduleService::class.java)

        // Проверка авторизации
        if (prefs.accessToken.isNullOrEmpty()) {
            navigateToLogin()
            return
        }

        // Инициализация View
        textViewGreeting = findViewById(R.id.textViewGreeting)
        textViewScheduleDate = findViewById(R.id.textViewScheduleDate) // <-- Инициализация
        linearLayoutSchedule = findViewById(R.id.linearLayoutSchedule)
        buttonProfile = findViewById(R.id.buttonProfile)
        buttonLogout = findViewById(R.id.buttonLogout)
        buttonAttendance = findViewById(R.id.buttonAttendance) // <-- Инициализация
        buttonYesterday = findViewById(R.id.buttonYesterday) // <-- Инициализация
        buttonToday = findViewById(R.id.buttonToday) // <-- Инициализация
        buttonTomorrow = findViewById(R.id.buttonTomorrow) // <-- Инициализация
        progressBarMain = findViewById(R.id.progressBarMain)

        textViewGreeting.text = "Привет, ${prefs.username ?: "Пользователь"}!"

        // Настройка обработчиков кнопок
        buttonProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        buttonAttendance.setOnClickListener {
            startActivity(Intent(this, AttendanceActivity::class.java))
        }

        buttonLogout.setOnClickListener {
            prefs.clear()
            Toast.makeText(this, "🚪 Вы вышли из аккаунта", Toast.LENGTH_SHORT).show()
            navigateToLogin()
        }

        // Обработчики для кнопок даты
        buttonYesterday.setOnClickListener {
            currentDate.add(Calendar.DAY_OF_MONTH, -1)
            updateScheduleDateLabel()
            loadScheduleForDate()
        }

        buttonToday.setOnClickListener {
            currentDate = Calendar.getInstance()
            updateScheduleDateLabel()
            loadScheduleForDate()
        }

        buttonTomorrow.setOnClickListener {
            currentDate.add(Calendar.DAY_OF_MONTH, 1)
            updateScheduleDateLabel()
            loadScheduleForDate()
        }

        // Загружаем расписание на сегодня при старте
        updateScheduleDateLabel()
        loadScheduleForDate()
    }

    private fun updateScheduleDateLabel() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val displayDate = getHumanReadableDate(dateFormat.format(currentDate.time))
        textViewScheduleDate.text = "📅 Расписание на $displayDate"
    }

    // Вспомогательная функция для "человеческого" отображения даты (аналог Python-функции)
    private fun getHumanReadableDate(dateStr: String): String {
        return try {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)?.let { Calendar.getInstance().apply { time = it } }
            val today = Calendar.getInstance()
            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }
            val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }

            when {
                date?.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                        date.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "сегодня"
                date?.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
                        date.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) -> "вчера"
                date?.get(Calendar.YEAR) == tomorrow.get(Calendar.YEAR) &&
                        date.get(Calendar.DAY_OF_YEAR) == tomorrow.get(Calendar.DAY_OF_YEAR) -> "завтра"
                else -> dateStr
            }
        } catch (e: Exception) {
            dateStr
        }
    }

    private fun loadScheduleForDate() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val targetDate = dateFormat.format(currentDate.time)
        loadSchedule(targetDate)
    }

    private fun loadSchedule(date: String) {
        linearLayoutSchedule.removeAllViews()
        progressBarMain.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = scheduleService.getScheduleByDate(date)
                withContext(Dispatchers.Main) {
                    progressBarMain.visibility = View.GONE
                    if (response.isSuccessful) {
                        val schedule = response.body() ?: emptyList()
                        if (schedule.isEmpty()) {
                            addScheduleItemView("На этот день занятий нет.", "", "", "", "", "")
                        } else {
                            for (item in schedule) {
                                addScheduleItemView(
                                    item.subject_name ?: "Предмет неизвестен",
                                    item.started_at ?: "--:--",
                                    item.finished_at ?: "--:--",
                                    item.teacher_name ?: "",
                                    item.room_name ?: "",
                                    item.lesson?.toString() ?: "?"
                                )
                            }
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "❌ Ошибка загрузки расписания", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBarMain.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "⚠️ ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun addScheduleItemView(subject: String, start: String, end: String, teacher: String, room: String, lessonNum: String) {
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

        val lessonView = TextView(this).apply {
            text = "$lessonNum️⃣ $subject"
            textSize = 16f
            setTextColor(getColor(android.R.color.black))
        }

        val timeView = TextView(this).apply {
            text = "🕒 $start – $end"
            textSize = 14f
            setTextColor(getColor(android.R.color.darker_gray))
        }

        container.addView(lessonView)
        container.addView(timeView)

        if (teacher.isNotEmpty()) {
            val teacherView = TextView(this).apply {
                text = "👨‍🏫 $teacher"
                textSize = 14f
                setTextColor(getColor(android.R.color.darker_gray))
            }
            container.addView(teacherView)
        }

        if (room.isNotEmpty()) {
            val roomView = TextView(this).apply {
                text = "📍 $room"
                textSize = 14f
                setTextColor(getColor(android.R.color.darker_gray))
            }
            container.addView(roomView)
        }

        linearLayoutSchedule.addView(container)
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}