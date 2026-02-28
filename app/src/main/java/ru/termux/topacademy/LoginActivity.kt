package ru.termux.topacademy

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import ru.termux.topacademy.api.ApiClient
import ru.termux.topacademy.api.AuthService
import ru.termux.topacademy.api.LoginRequest
import ru.termux.topacademy.utils.SharedPreferencesHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit

class LoginActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferencesHelper
    private lateinit var authService: AuthService

    private lateinit var editTextUsername: EditText
    private lateinit var editTextPassword: EditText
    private lateinit var buttonLogin: Button
    private lateinit var progressBar: ProgressBar

    companion object {
        const val EXTRA_AUTO_FILL = "auto_fill"
        const val EXTRA_SHOW_MESSAGE = "show_message"
        const val EXTRA_AUTO_LOGIN = "auto_login" // Новый флаг для автоматического входа
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        prefs = SharedPreferencesHelper(this)

        editTextUsername = findViewById(R.id.editTextUsername)
        editTextPassword = findViewById(R.id.editTextPassword)
        buttonLogin = findViewById(R.id.buttonLogin)
        progressBar = findViewById(R.id.progressBar)

        // Проверяем флаги из Intent
        val autoFill = intent.getBooleanExtra(EXTRA_AUTO_FILL, true)
        val autoLogin = intent.getBooleanExtra(EXTRA_AUTO_LOGIN, true)
        val showMessage = intent.getStringExtra(EXTRA_SHOW_MESSAGE)

        // Показываем сообщение если есть
        if (!showMessage.isNullOrEmpty()) {
            Toast.makeText(this, showMessage, Toast.LENGTH_LONG).show()
        }

        // Получаем сохраненные данные
        val savedUsername = prefs.username
        val savedPassword = prefs.password

        // Проверяем нужно ли пытаться автоматически войти
        if (autoFill && autoLogin) {
            // Если есть токен И сохраненные логин/пароль - идем сразу на главную
            if (!prefs.accessToken.isNullOrEmpty() && !savedUsername.isNullOrEmpty() && !savedPassword.isNullOrEmpty()) {
                navigateToMain()
                return
            } else if (!savedUsername.isNullOrEmpty() && !savedPassword.isNullOrEmpty()) {
                // Если есть сохраненные данные, но нет токена - пробуем автоматический вход
                attemptAutoLogin(savedUsername, savedPassword)
                return  // Не показываем форму пока пытаемся войти
            }
        }

        // Заполняем поля сохраненными данными (если есть и autoFill = true)
        if (autoFill) {
            if (!savedUsername.isNullOrEmpty()) {
                editTextUsername.setText(savedUsername)
            }
            if (!savedPassword.isNullOrEmpty()) {
                editTextPassword.setText(savedPassword)

                // Если есть пароль, делаем кнопку входа активной
                if (!savedUsername.isNullOrEmpty() && !savedPassword.isNullOrEmpty()) {
                    // Можно автоматически нажать кнопку входа через 500мс
                    // editTextPassword.postDelayed({
                    //     buttonLogin.performClick()
                    // }, 500)
                }
            }
        }

        buttonLogin.setOnClickListener {
            val username = editTextUsername.text.toString().trim()
            val password = editTextPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Введите логин и пароль", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            performLogin(username, password, remember = true)
        }
    }

    private fun attemptAutoLogin(username: String, password: String) {
        showProgress(true)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Создаем сервис для логина без токена
                val retrofitWithoutToken = ApiClient.provideRetrofitWithoutToken()
                val authServiceForLogin = retrofitWithoutToken.create(AuthService::class.java)

                val request = LoginRequest(username = username, password = password)
                val response = authServiceForLogin.login(request)

                withContext(Dispatchers.Main) {
                    showProgress(false)

                    if (response.isSuccessful) {
                        val token = response.body()?.access_token
                        if (!token.isNullOrEmpty()) {
                            prefs.accessToken = token
                            prefs.username = username
                            prefs.password = password

                            Toast.makeText(this@LoginActivity, "✅ Автоматический вход выполнен!", Toast.LENGTH_SHORT).show()
                            navigateToMain()
                        } else {
                            // Показываем форму входа с заполненными данными
                            showLoginFormWithAutoFill("Не удалось получить токен")
                        }
                    } else {
                        // Показываем форму входа с заполненными данными
                        val errorMsg = when (response.code()) {
                            401 -> "Неверный логин или пароль"
                            500 -> "Ошибка сервера"
                            else -> "Ошибка: ${response.code()}"
                        }
                        showLoginFormWithAutoFill(errorMsg)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showProgress(false)
                    // Показываем форму входа при ошибке сети
                    showLoginFormWithAutoFill("Ошибка сети")
                }
            }
        }
    }

    private fun showLoginFormWithAutoFill(errorMessage: String? = null) {
        // Эта функция вызывается при неудачном авто-логине
        // Показываем форму с уже заполненными полями
        val savedUsername = prefs.username
        val savedPassword = prefs.password

        if (!savedUsername.isNullOrEmpty()) {
            editTextUsername.setText(savedUsername)
        }
        if (!savedPassword.isNullOrEmpty()) {
            editTextPassword.setText(savedPassword)
        }

        // Показываем сообщение об ошибке если есть
        errorMessage?.let {
            Toast.makeText(this, "⚠️ $it", Toast.LENGTH_LONG).show()
        }
    }

    private fun performLogin(username: String, password: String, remember: Boolean = true) {
        showProgress(true)
        buttonLogin.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Создаем сервис для логина без токена
                val retrofitWithoutToken = ApiClient.provideRetrofitWithoutToken()
                val authServiceForLogin = retrofitWithoutToken.create(AuthService::class.java)

                val request = LoginRequest(username = username, password = password)
                val response = authServiceForLogin.login(request)

                withContext(Dispatchers.Main) {
                    showProgress(false)
                    buttonLogin.isEnabled = true

                    if (response.isSuccessful) {
                        val token = response.body()?.access_token
                        if (!token.isNullOrEmpty()) {
                            prefs.accessToken = token

                            // Сохраняем логин и пароль только если нужно запомнить
                            if (remember) {
                                prefs.username = username
                                prefs.password = password
                            } else {
                                // Если не нужно запоминать, очищаем только пароль
                                prefs.username = username
                                prefs.password = ""
                            }

                            Toast.makeText(this@LoginActivity, "✅ Успешный вход!", Toast.LENGTH_SHORT).show()
                            navigateToMain()
                        } else {
                            showError("Не удалось получить токен")
                        }
                    } else {
                        // Если ошибка 401, возможно неправильный пароль
                        when (response.code()) {
                            401 -> showError("Неверный логин или пароль")
                            500 -> showError("Ошибка сервера")
                            else -> showError("Ошибка: ${response.code()}")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showProgress(false)
                    buttonLogin.isEnabled = true
                    showError("Ошибка сети: ${e.message}")
                }
            }
        }
    }

    private fun showProgress(show: Boolean) {
        progressBar.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
        buttonLogin.isEnabled = !show
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showError(message: String) {
        Toast.makeText(this, "❌ $message", Toast.LENGTH_LONG).show()
    }
}