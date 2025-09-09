package ru.termux.topacademy

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import ru.termux.topacademy.api.ApiClient
import ru.termux.topacademy.api.ProfileService
import ru.termux.topacademy.utils.SharedPreferencesHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit

class ProfileActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferencesHelper
    private lateinit var retrofit: Retrofit
    private lateinit var profileService: ProfileService

    private lateinit var imageViewProfilePhoto: ImageView
    private lateinit var textViewFullName: TextView
    private lateinit var textViewEmail: TextView
    private lateinit var textViewBirthDate: TextView
    private lateinit var textViewAddress: TextView
    private lateinit var textViewStudy: TextView
    private lateinit var textViewPhones: TextView
    private lateinit var textViewSocials: TextView
    private lateinit var textViewFillPercentage: TextView
    private lateinit var progressBarProfile: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        prefs = SharedPreferencesHelper(this)
        retrofit = ApiClient.provideRetrofit(prefs)
        profileService = retrofit.create(ProfileService::class.java)

        imageViewProfilePhoto = findViewById(R.id.imageViewProfilePhoto)
        textViewFullName = findViewById(R.id.textViewFullName)
        textViewEmail = findViewById(R.id.textViewEmail)
        textViewBirthDate = findViewById(R.id.textViewBirthDate)
        textViewAddress = findViewById(R.id.textViewAddress)
        textViewStudy = findViewById(R.id.textViewStudy)
        textViewPhones = findViewById(R.id.textViewPhones)
        textViewSocials = findViewById(R.id.textViewSocials)
        textViewFillPercentage = findViewById(R.id.textViewFillPercentage)
        progressBarProfile = findViewById(R.id.progressBarProfile)

        loadProfile()
    }

    private fun loadProfile() {
        progressBarProfile.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = profileService.getProfile()

                withContext(Dispatchers.Main) {
                    progressBarProfile.visibility = View.GONE

                    if (response.isSuccessful) {
                        val profile = response.body()
                        if (profile != null) {
                            displayProfile(profile)
                        } else {
                            Toast.makeText(this@ProfileActivity, "Профиль пуст", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@ProfileActivity, "❌ Ошибка загрузки профиля", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBarProfile.visibility = View.GONE
                    Toast.makeText(this@ProfileActivity, "⚠️ ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun displayProfile(profile: ru.termux.topacademy.model.Profile) {
        textViewFullName.text = "ФИО: ${profile.ful_name ?: "Не указано"}"
        textViewEmail.text = "Email: ${profile.email ?: "Не указано"}"
        textViewBirthDate.text = "Дата рождения: ${profile.date_birth ?: "Не указано"}"
        textViewAddress.text = "Адрес: ${profile.address ?: "Не указано"}"
        textViewStudy.text = "Учебное заведение: ${profile.study ?: "Не указано"}"

        val phones = profile.phones?.map { it.phone_number ?: "Не указан" }?.joinToString("\n") ?: "—"
        textViewPhones.text = "Телефоны:\n$phones"

        val socials = profile.links
            ?.filter { it.value?.isNotEmpty() == true }
            ?.map { "${it.name ?: "Соцсеть"}: ${it.value}" }
            ?.joinToString("\n") ?: "—"
        textViewSocials.text = "Соцсети:\n$socials"

        textViewFillPercentage.text = "Заполненность: ${profile.fill_percentage ?: 0}%"

        val photoUrl = profile.photo_path?.trim()
        if (!photoUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(photoUrl)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.stat_notify_error)
                .into(imageViewProfilePhoto)
        }
    }
}