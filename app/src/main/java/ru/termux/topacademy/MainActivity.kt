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
    private lateinit var textViewScheduleDate: TextView
    private lateinit var linearLayoutSchedule: LinearLayout
    private lateinit var buttonProfile: Button
//    private lateinit var buttonLogout: Button
    private lateinit var buttonAttendance: Button
    private lateinit var buttonYesterday: Button
    private lateinit var buttonToday: Button
    private lateinit var buttonTomorrow: Button
    private lateinit var progressBarMain: ProgressBar

    private lateinit var buttonMarket: Button

    private var currentDate: Calendar = Calendar.getInstance()

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
        textViewScheduleDate = findViewById(R.id.textViewScheduleDate)
        linearLayoutSchedule = findViewById(R.id.linearLayoutSchedule)
        buttonProfile = findViewById(R.id.buttonProfile)
//        buttonLogout = findViewById(R.id.buttonLogout)
        buttonAttendance = findViewById(R.id.buttonAttendance)
        buttonYesterday = findViewById(R.id.buttonYesterday)
        buttonToday = findViewById(R.id.buttonToday)
        buttonTomorrow = findViewById(R.id.buttonTomorrow)
        progressBarMain = findViewById(R.id.progressBarMain)
        buttonMarket = findViewById(R.id.buttonMarket)

        textViewGreeting.text = "Привет, ${prefs.username ?: "Пользователь"}!"

        // Настройка обработчиков кнопок
        buttonProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        buttonAttendance.setOnClickListener {
            startActivity(Intent(this, AttendanceActivity::class.java))
        }

        buttonMarket.setOnClickListener {
            startActivity(Intent(this, MarketActivity::class.java))
        }

//        buttonLogout.setOnClickListener {
//            prefs.clear()
//            Toast.makeText(this, "🚪 Вы вышли из аккаунта", Toast.LENGTH_SHORT).show()
//            navigateToLogin()
//        }

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

    private fun getHumanReadableDate(dateStr: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = inputFormat.parse(dateStr) ?: return dateStr
            val calendarDate = Calendar.getInstance().apply { time = date }
            val today = Calendar.getInstance()
            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }
            val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }

            when {
                isSameDay(calendarDate, today) -> "сегодня"
                isSameDay(calendarDate, yesterday) -> "вчера"
                isSameDay(calendarDate, tomorrow) -> "завтра"
                else -> dateStr
            }
        } catch (e: Exception) {
            dateStr
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
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

                    when {
                        response.isSuccessful -> {
                            val schedule = response.body() ?: emptyList()
                            if (schedule.isEmpty()) {
                                addScheduleItemView(
                                    subject = "На этот день занятий нет.",
                                    start = "",
                                    end = "",
                                    teacher = "",
                                    room = "",
                                    lessonNum = ""
                                )
                            } else {
                                schedule.forEach { item ->
                                    addScheduleItemView(
                                        subject = item.subject_name ?: "Предмет неизвестен",
                                        start = item.started_at ?: "--:--",
                                        end = item.finished_at ?: "--:--",
                                        teacher = item.teacher_name ?: "",
                                        room = item.room_name ?: "",
                                        lessonNum = item.lesson?.toString() ?: "?"
                                    )
                                }
                            }
                        }
                        response.code() == 401 -> {
                            handleUnauthorized()
                        }
                        else -> {
                            Toast.makeText(
                                this@MainActivity,
                                "❌ Ошибка сервера: ${response.code()}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBarMain.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "⚠️ Ошибка сети: ${e.message}", Toast.LENGTH_LONG).show()
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

        if (lessonNum.isNotEmpty() && subject != "На этот день занятий нет.") {
            val lessonView = TextView(this).apply {
                text = "$lessonNum️⃣ $subject"
                textSize = 16f
                setTextColor(getColor(android.R.color.black))
            }
            container.addView(lessonView)
        } else {
            val lessonView = TextView(this).apply {
                text = subject
                textSize = 16f
                setTextColor(getColor(android.R.color.black))
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            container.addView(lessonView)
        }

        if (start.isNotEmpty() && end.isNotEmpty()) {
            val timeView = TextView(this).apply {
                text = "🕒 $start – $end"
                textSize = 14f
                setTextColor(getColor(android.R.color.darker_gray))
            }
            container.addView(timeView)
        }

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

    private fun handleUnauthorized() {
        Toast.makeText(this, "⚠️ Сессия истекла. Пожалуйста, войдите снова.", Toast.LENGTH_LONG).show()
        prefs.clear()
        navigateToLogin()
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}