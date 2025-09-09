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
    private lateinit var retrofit: Retrofit
    private lateinit var authService: AuthService

    private lateinit var editTextUsername: EditText
    private lateinit var editTextPassword: EditText
    private lateinit var buttonLogin: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        prefs = SharedPreferencesHelper(this)
        retrofit = ApiClient.provideRetrofit(prefs)
        authService = retrofit.create(AuthService::class.java)

        // Если уже залогинены — переходим на главный экран
        if (!prefs.accessToken.isNullOrEmpty()) {
            navigateToMain()
            return
        }

        editTextUsername = findViewById(R.id.editTextUsername)
        editTextPassword = findViewById(R.id.editTextPassword)
        buttonLogin = findViewById(R.id.buttonLogin)
        progressBar = findViewById(R.id.progressBar)

        buttonLogin.setOnClickListener {
            val username = editTextUsername.text.toString().trim()
            val password = editTextPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Введите логин и пароль", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            performLogin(username, password)
        }
    }

    private fun performLogin(username: String, password: String) {
        buttonLogin.isEnabled = false
        progressBar.visibility = android.view.View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = LoginRequest(username = username, password = password)
                val response = authService.login(request)

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val token = response.body()?.access_token
                        if (!token.isNullOrEmpty()) {
                            prefs.accessToken = token
                            prefs.username = username
                            prefs.password = password

                            Toast.makeText(this@LoginActivity, "✅ Успешный вход!", Toast.LENGTH_SHORT).show()
                            navigateToMain()
                        } else {
                            showError("Не удалось получить токен")
                        }
                    } else {
                        showError("Неверный логин или пароль")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showError("Ошибка сети: ${e.message}")
                }
            } finally {
                withContext(Dispatchers.Main) {
                    buttonLogin.isEnabled = true
                    progressBar.visibility = android.view.View.GONE
                }
            }
        }
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