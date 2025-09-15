package ru.termux.topacademy

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import ru.termux.topacademy.api.ApiClient
import ru.termux.topacademy.api.MarketService
import ru.termux.topacademy.model.MarketItem
import ru.termux.topacademy.utils.SharedPreferencesHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import android.widget.TextView

class MarketActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferencesHelper
    private lateinit var retrofit: Retrofit
    private lateinit var marketService: MarketService
    private lateinit var recyclerViewMarket: RecyclerView
    private lateinit var progressBarMarket: ProgressBar
    private lateinit var marketAdapter: MarketAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_market)

        prefs = SharedPreferencesHelper(this)
        retrofit = ApiClient.provideRetrofit(prefs)
        marketService = retrofit.create(MarketService::class.java)

        recyclerViewMarket = findViewById(R.id.recyclerViewMarket)
        progressBarMarket = findViewById(R.id.progressBarMarket)

        // Настройка RecyclerView
        marketAdapter = MarketAdapter(emptyList())
        recyclerViewMarket.layoutManager = LinearLayoutManager(this)
        recyclerViewMarket.adapter = marketAdapter

        loadMarketItems()
    }

    private fun loadMarketItems() {
        progressBarMarket.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val allItems = mutableListOf<MarketItem>()
                var currentPage = 1
                var hasMoreItems = true

                while (hasMoreItems) {
                    val response = marketService.getProducts(page = currentPage)
                    if (!response.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MarketActivity, "❌ Ошибка сервера: ${response.code()}", Toast.LENGTH_SHORT).show()
                        }
                        break
                    }

                    val marketResponse = response.body()
                    val currentPageItems = marketResponse?.products_list.orEmpty()

                    // Если на текущей странице есть товары, добавляем их
                    if (currentPageItems.isNotEmpty()) {
                        allItems.addAll(currentPageItems)
                        currentPage++ // Переходим к следующей странице
                    } else {
                        // Если список пуст, значит, больше товаров нет
                        hasMoreItems = false
                    }

                    // Дополнительная защита от бесконечного цикла
                    if (currentPage > 100) {
                        hasMoreItems = false
                    }
                }

                withContext(Dispatchers.Main) {
                    progressBarMarket.visibility = View.GONE
                    marketAdapter.updateItems(allItems)
                    if (allItems.isEmpty()) {
                        Toast.makeText(this@MarketActivity, "📭 Товары в маркете отсутствуют", Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBarMarket.visibility = View.GONE
                    Toast.makeText(this@MarketActivity, "⚠️ Ошибка сети: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

// Адаптер для RecyclerView
class MarketAdapter(private var items: List<MarketItem>) :
    RecyclerView.Adapter<MarketAdapter.MarketViewHolder>() {

    fun updateItems(newItems: List<MarketItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MarketViewHolder {
        val view = parent.inflate(R.layout.item_market, false)
        return MarketViewHolder(view)
    }

    override fun onBindViewHolder(holder: MarketViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    class MarketViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageViewProduct = itemView.findViewById<androidx.appcompat.widget.AppCompatImageView>(R.id.imageViewProduct)
        private val textViewTitle = itemView.findViewById<TextView>(R.id.textViewTitle)
        private val textViewDescription = itemView.findViewById<TextView>(R.id.textViewDescription)
        private val textViewPrice = itemView.findViewById<TextView>(R.id.textViewPrice)
        private val textViewQuantity = itemView.findViewById<TextView>(R.id.textViewQuantity)

        fun bind(item: MarketItem) {
            textViewTitle.text = item.title
            textViewDescription.text = item.description

            // Получаем стоимость в баллах (берем первый тип, если есть)
            val priceText = item.prices?.firstOrNull()?.points_sum?.let { "$it баллов" } ?: "Цена не указана"
            textViewPrice.text = priceText

            textViewQuantity.text = "В наличии: ${item.quantity}"

            // Загружаем изображение с помощью Glide
            val imageUrl = item.file_name?.trim()
            if (!imageUrl.isNullOrEmpty()) {
                Glide.with(itemView.context)                    .load(imageUrl)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.stat_notify_error)
                    .into(imageViewProduct)
            } else {
                imageViewProduct.setImageResource(android.R.drawable.ic_menu_gallery)
            }
        }
    }
}

// Extension function для удобного инфлейта
fun ViewGroup.inflate(layoutRes: Int, attachToRoot: Boolean = false): View {
    return android.view.LayoutInflater.from(context).inflate(layoutRes, this, attachToRoot)
}