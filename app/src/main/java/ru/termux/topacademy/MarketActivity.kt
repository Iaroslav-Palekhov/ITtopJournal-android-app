package ru.termux.topacademy

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
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
import retrofit2.Response

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

        // Инициализация адаптера с лямбдой для обработки покупки
        marketAdapter = MarketAdapter(emptyList()) { selectedItem ->
            purchaseItem(selectedItem)
        }

        // Настройка RecyclerView
        recyclerViewMarket.layoutManager = LinearLayoutManager(this)
        recyclerViewMarket.adapter = marketAdapter

        // Загружаем все товары
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

                    if (currentPageItems.isNotEmpty()) {
                        allItems.addAll(currentPageItems)
                        currentPage++
                    } else {
                        hasMoreItems = false
                    }

                    // Защита от бесконечного цикла
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

    private fun purchaseItem(item: MarketItem) {
        progressBarMarket.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Создаем тело запроса в формате, ожидаемом сервером
                val purchaseRequest = PurchaseRequest(
                    cart = Cart(
                        cart_items = listOf(
                            CartItem(
                                id = item.id, // ID товара
                                count = 1     // Количество
                            )
                        )
                    )
                )

                // Выполняем запрос на покупку
                val response = marketService.purchaseProduct(purchaseRequest)

                withContext(Dispatchers.Main) {
                    progressBarMarket.visibility = View.GONE
                    if (response.isSuccessful) {
                        Toast.makeText(this@MarketActivity, "✅ Товар \"${item.title}\" успешно куплен!", Toast.LENGTH_LONG).show()
                        loadMarketItems() // Обновляем список товаров
                    } else {
                        // Более точное сообщение об ошибке
                        val errorMessage = when (response.code()) {
                            400 -> "❌ Неверный формат запроса. Обратитесь к разработчику."
                            401 -> "❌ Сессия истекла. Войдите снова."
                            else -> "❌ Ошибка покупки: ${response.code()}"
                        }
                        Toast.makeText(this@MarketActivity, errorMessage, Toast.LENGTH_LONG).show()
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

// --- Модели данных для запроса на покупку ---

// Основная модель запроса
data class PurchaseRequest(
    val cart: Cart
)

// Модель корзины
data class Cart(
    val notes: String? = null,
    val cart_items: List<CartItem>
)

// Модель элемента корзины
data class CartItem(
    val id: Int,
    val count: Int
)

// --- Адаптер для RecyclerView ---

class MarketAdapter(
    private var items: List<MarketItem>,
    private val onBuyClickListener: (MarketItem) -> Unit
) : RecyclerView.Adapter<MarketAdapter.MarketViewHolder>() {

    fun updateItems(newItems: List<MarketItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MarketViewHolder {
        val view = parent.inflate(R.layout.item_market, false)
        return MarketViewHolder(view)
    }

    override fun onBindViewHolder(holder: MarketViewHolder, position: Int) {
        holder.bind(items[position], onBuyClickListener)
    }

    override fun getItemCount() = items.size

    class MarketViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageViewProduct = itemView.findViewById<androidx.appcompat.widget.AppCompatImageView>(R.id.imageViewProduct)
        private val textViewTitle = itemView.findViewById<TextView>(R.id.textViewTitle)
        private val textViewDescription = itemView.findViewById<TextView>(R.id.textViewDescription)
        private val textViewPrice = itemView.findViewById<TextView>(R.id.textViewPrice)
        private val textViewQuantity = itemView.findViewById<TextView>(R.id.textViewQuantity)
        private val buttonBuy = itemView.findViewById<Button>(R.id.buttonBuy)

        fun bind(item: MarketItem, onBuyClick: (MarketItem) -> Unit) {
            textViewTitle.text = item.title
            textViewDescription.text = item.description

            val priceText = item.prices?.firstOrNull()?.points_sum?.let { "$it баллов" } ?: "Цена не указана"
            textViewPrice.text = priceText
            textViewQuantity.text = "В наличии: ${item.quantity}"

            val imageUrl = item.file_name?.trim()
            if (!imageUrl.isNullOrEmpty()) {
                Glide.with(itemView.context)
                    .load(imageUrl)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.stat_notify_error)
                    .into(imageViewProduct)
            } else {
                imageViewProduct.setImageResource(android.R.drawable.ic_menu_gallery)
            }

            buttonBuy.setOnClickListener {
                onBuyClick(item)
            }
        }
    }
}

// --- Вспомогательная функция ---

fun ViewGroup.inflate(layoutRes: Int, attachToRoot: Boolean = false): View {
    return android.view.LayoutInflater.from(context).inflate(layoutRes, this, attachToRoot)
}
