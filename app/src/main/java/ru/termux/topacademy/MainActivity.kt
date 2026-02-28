package ru.termux.topacademy

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import ru.termux.topacademy.api.ApiClient
import ru.termux.topacademy.api.AuthService
import ru.termux.topacademy.api.LoginRequest
import ru.termux.topacademy.api.ScheduleService
import ru.termux.topacademy.utils.SharedPreferencesHelper
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferencesHelper
    private lateinit var retrofit: Retrofit
    private lateinit var scheduleService: ScheduleService

    private lateinit var textViewGreeting: TextView
    private lateinit var textViewScheduleDate: TextView
    private lateinit var linearLayoutSchedule: LinearLayout
    private lateinit var progressBarMain: ProgressBar
    private lateinit var buttonYesterday: TextView
    private lateinit var buttonToday: TextView
    private lateinit var buttonTomorrow: TextView

    // Navigation Drawer элементы
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: MaterialToolbar

    private var currentDate: Calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = SharedPreferencesHelper(this)
        retrofit = ApiClient.provideRetrofit(prefs)
        scheduleService = retrofit.create(ScheduleService::class.java)

        // Проверка авторизации
        if (prefs.accessToken.isNullOrEmpty()) {
            navigateToLoginWithAutoFill()
            return
        }

        // Инициализация Navigation Drawer
        initNavigationDrawer()

        // Инициализация View
        textViewGreeting = findViewById(R.id.textViewGreeting)
        textViewScheduleDate = findViewById(R.id.textViewScheduleDate)
        linearLayoutSchedule = findViewById(R.id.linearLayoutSchedule)
        progressBarMain = findViewById(R.id.progressBarMain)
        buttonYesterday = findViewById(R.id.buttonYesterday)
        buttonToday = findViewById(R.id.buttonToday)
        buttonTomorrow = findViewById(R.id.buttonTomorrow)

        textViewGreeting.text = "Привет, ${prefs.username ?: "Пользователь"}!"

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

    private fun initNavigationDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)
        toolbar = findViewById(R.id.topAppBar)

        // Настройка Toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_menu_white)

        // Обработка кликов по меню
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    // Уже на главной
                }
                R.id.nav_attendance -> {
                    startActivity(Intent(this, AttendanceActivity::class.java))
                }
                R.id.nav_reviews -> {
                    startActivity(Intent(this, ReviewsActivity::class.java))
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                }
                R.id.nav_homework -> {
                    startActivity(Intent(this, HomeworkActivity::class.java))
                }
                R.id.nav_market -> {
                    startActivity(Intent(this, MarketActivity::class.java))
                }
                R.id.nav_settings -> {
                    // TODO: создать SettingsActivity
                    Toast.makeText(this, "Настройки", Toast.LENGTH_SHORT).show()
                }
                R.id.nav_logout -> {
                    performLogout("Вы вышли из аккаунта")
                }
            }
            // Закрыть меню после выбора
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        // Настройка заголовка Navigation Drawer (если есть)
        val headerView = navigationView.getHeaderView(0)
        headerView?.findViewById<TextView>(R.id.textViewEmail)?.text = prefs.username ?: "Гость"
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // Открыть/закрыть меню при нажатии на иконку
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    drawerLayout.openDrawer(GravityCompat.START)
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
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
                            // Сессия истекла, пытаемся автоматически перелогиниться
                            attemptReLogin()
                        }
                        response.code() == 500 -> {
                            Toast.makeText(
                                this@MainActivity,
                                "😴 Сервер спит",
                                Toast.LENGTH_SHORT
                            ).show()
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

    private fun attemptReLogin() {
        val savedUsername = prefs.username
        val savedPassword = prefs.password

        if (!savedUsername.isNullOrEmpty() && !savedPassword.isNullOrEmpty()) {
            // Показываем уведомление о попытке автоматического входа
            Toast.makeText(this, "🔄 Автоматический вход...", Toast.LENGTH_SHORT).show()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val retrofitWithoutToken = ApiClient.provideRetrofitWithoutToken()
                    val authService = retrofitWithoutToken.create(AuthService::class.java)
                    val request = LoginRequest(username = savedUsername, password = savedPassword)
                    val response = authService.login(request)

                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful) {
                            val token = response.body()?.access_token
                            if (!token.isNullOrEmpty()) {
                                prefs.accessToken = token
                                // Успешно обновили токен, перезагружаем расписание
                                Toast.makeText(this@MainActivity, "✅ Сессия восстановлена", Toast.LENGTH_SHORT).show()
                                loadScheduleForDate()
                            } else {
                                forceLogout("Не удалось обновить сессию")
                            }
                        } else {
                            forceLogout("Неверные сохраненные данные")
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        forceLogout("Ошибка сети: ${e.message}")
                    }
                }
            }
        } else {
            forceLogout("Нет сохраненных данных для входа")
        }
    }

    private fun performLogout(message: String) {
        // Сохраняем логин и пароль перед очисткой для автозаполнения
        val savedUsername = prefs.username
        val savedPassword = prefs.password

        prefs.clear()

        // Восстанавливаем логин и пароль для автозаполнения
        if (!savedUsername.isNullOrEmpty()) {
            prefs.username = savedUsername
        }
        if (!savedPassword.isNullOrEmpty()) {
            prefs.password = savedPassword
        }

        Toast.makeText(this, "🚪 $message", Toast.LENGTH_SHORT).show()

        // Переходим на LoginActivity с автозаполнением
        navigateToLoginWithMessage(message)
    }

    private fun forceLogout(message: String) {
        // Сохраняем логин и пароль перед очисткой для автозаполнения
        val savedUsername = prefs.username
        val savedPassword = prefs.password

        prefs.clear()

        // Восстанавливаем логин и пароль для автозаполнения
        if (!savedUsername.isNullOrEmpty()) {
            prefs.username = savedUsername
        }
        if (!savedPassword.isNullOrEmpty()) {
            prefs.password = savedPassword
        }

        // Переходим на LoginActivity с сообщением
        navigateToLoginWithMessage("⚠️ $message. Войдите снова.")
    }

    private fun navigateToLoginWithMessage(message: String) {
        val intent = Intent(this, LoginActivity::class.java)
        intent.putExtra(LoginActivity.EXTRA_AUTO_FILL, true)
        intent.putExtra(LoginActivity.EXTRA_AUTO_LOGIN, true) // Добавляем флаг для авто-логина
        intent.putExtra(LoginActivity.EXTRA_SHOW_MESSAGE, message)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun navigateToLoginWithAutoFill() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.putExtra(LoginActivity.EXTRA_AUTO_FILL, true)
        intent.putExtra(LoginActivity.EXTRA_AUTO_LOGIN, true) // Добавляем флаг для авто-логина
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}