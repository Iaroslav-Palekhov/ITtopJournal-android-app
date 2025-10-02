package ru.termux.topacademy

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import ru.termux.topacademy.api.ApiClient
import ru.termux.topacademy.api.ReviewService
import ru.termux.topacademy.model.Review
import ru.termux.topacademy.utils.SharedPreferencesHelper

class ReviewsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferencesHelper
    private lateinit var reviewService: ReviewService

    private lateinit var container: LinearLayout
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reviews)

        prefs = SharedPreferencesHelper(this)
        val retrofit = ApiClient.provideRetrofit(prefs)
        reviewService = retrofit.create(ReviewService::class.java)

        container = findViewById(R.id.container)
        progressBar = findViewById(R.id.progressBar)

        loadReviews()
    }

    private fun loadReviews() {
        container.removeAllViews()
        progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = reviewService.getReviews()

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE

                    if (response.isSuccessful) {
                        val reviews = response.body() ?: emptyList()
                        if (reviews.isEmpty()) {
                            showEmptyMessage()
                        } else {
                            // Показываем в хронологическом порядке: старые сверху → новые снизу
                            reviews.reversed().forEach { review ->
                                addReviewView(review)
                            }
                        }
                    } else if (response.code() == 401) {
                        handleUnauthorized()
                    } else {
                        Toast.makeText(this@ReviewsActivity, "Ошибка: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@ReviewsActivity, "Ошибка сети: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun addReviewView(review: Review) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.review_card_background) // см. шаг 4
            setPadding(24, 24, 24, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 16, 16, 16)
            }
        }

        val dateView = TextView(this).apply {
            text = "📅 ${review.date}"
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@ReviewsActivity, android.R.color.darker_gray))
        }

        val subjectView = TextView(this).apply {
            text = "📚 ${review.fullSpec}"
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@ReviewsActivity, android.R.color.black))
        }

        val teacherView = TextView(this).apply {
            text = "👨‍🏫 ${review.teacher}"
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@ReviewsActivity, android.R.color.darker_gray))
        }

        val messageView = TextView(this).apply {
            text = review.message
            textSize = 15f
            setTextColor(ContextCompat.getColor(this@ReviewsActivity, android.R.color.black))
        }

        card.addView(dateView)
        card.addView(subjectView)
        card.addView(teacherView)
        card.addView(messageView)

        container.addView(card)
    }

    private fun showEmptyMessage() {
        val emptyView = TextView(this).apply {
            text = "Нет отзывов"
            textSize = 18f
            setTextColor(ContextCompat.getColor(this@ReviewsActivity, android.R.color.darker_gray))
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 64, 0, 0) }
        }
        container.addView(emptyView)
    }

    private fun handleUnauthorized() {
        Toast.makeText(this, "Сессия истекла. Войдите снова.", Toast.LENGTH_LONG).show()
        prefs.clear()
        finish()
    }
}