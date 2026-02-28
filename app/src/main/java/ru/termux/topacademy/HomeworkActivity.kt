// HomeworkActivity.kt
package ru.termux.topacademy

import android.app.ProgressDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.Retrofit
import ru.termux.topacademy.api.ApiClient
import ru.termux.topacademy.api.HomeworkService
import ru.termux.topacademy.api.UserService
import ru.termux.topacademy.model.HomeworkItem
import ru.termux.topacademy.utils.SharedPreferencesHelper
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class HomeworkActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferencesHelper
    private lateinit var retrofit: Retrofit
    private lateinit var homeworkService: HomeworkService

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var buttonBack: Button
    private lateinit var textViewTitle: TextView
    private lateinit var spinnerStatus: Spinner
    private lateinit var textViewEmpty: TextView

    // =============================================
    // НАСТРОЙКИ YANDEX AI — вставь свои данные
    // =============================================
    private val YANDEX_API_KEY = "your yandex api key"
    private val YANDEX_ASSISTANT_ID = "your yandex assistant id"
    private val YANDEX_API_URL = "https://rest-assistant.api.cloud.yandex.net/v1/responses"

    private val homeworkStatuses = listOf(
        "Проверенные" to 1,
        "Непроверенные" to 2,
        "Не сданные" to 3,
        "Просроченные" to 0
    )

    private var currentStatus = 1
    private var currentPage = 1
    private var isLoading = false
    private var isLastPage = false
    private val homeworkList = mutableListOf<HomeworkItem>()
    private lateinit var adapter: HomeworkAdapter
    private lateinit var layoutManager: LinearLayoutManager

    private val PICK_FILE_REQUEST_CODE = 1001
    private var currentHomeworkId: Int = -1
    private var selectedFileUri: Uri? = null
    private var selectedFileName: String? = null

    // OkHttp для скачивания файлов и Yandex AI
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_homework)

        prefs = SharedPreferencesHelper(this)
        retrofit = ApiClient.provideRetrofit(prefs)
        homeworkService = retrofit.create(HomeworkService::class.java)

        if (prefs.accessToken.isNullOrEmpty()) { navigateToLogin(); return }

        initViews()
        setupRecyclerView()
        setupSpinner()
        setupListeners()

        if (prefs.groupId == 0) loadUserInfoAndThenHomework()
        else loadHomework(currentPage, true)
    }

    private fun initViews() {
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        recyclerView = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
        buttonBack = findViewById(R.id.buttonBack)
        textViewTitle = findViewById(R.id.textViewTitle)
        spinnerStatus = findViewById(R.id.spinnerStatus)
        textViewEmpty = findViewById(R.id.textViewEmpty)
        textViewTitle.text = " Домашние задания"
    }

    private fun setupRecyclerView() {
        layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager
        adapter = HomeworkAdapter(homeworkList) { hw -> showHomeworkOptionsDialog(hw) }
        recyclerView.adapter = adapter

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val last = layoutManager.findLastVisibleItemPosition()
                if (!isLoading && !isLastPage && last >= layoutManager.itemCount - 3 && layoutManager.itemCount > 0)
                    loadHomework(currentPage + 1, false)
            }
        })
    }

    private fun setupSpinner() {
        val sa = ArrayAdapter(this, android.R.layout.simple_spinner_item, homeworkStatuses.map { it.first })
        sa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerStatus.adapter = sa
        spinnerStatus.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentStatus = homeworkStatuses[position].second
                currentPage = 1; isLastPage = false; homeworkList.clear()
                adapter.notifyDataSetChanged(); textViewEmpty.visibility = View.GONE
                loadHomework(1, true)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupListeners() {
        buttonBack.setOnClickListener { finish() }
        swipeRefreshLayout.setOnRefreshListener { refreshHomework() }
    }

    private fun loadUserInfoAndThenHomework() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val r = retrofit.create(UserService::class.java).getUserInfo()
                withContext(Dispatchers.Main) {
                    if (r.isSuccessful) r.body()?.let { prefs.saveUserInfo(it) }
                    else if (r.code() == 401) { handleUnauthorized(); return@withContext }
                    loadHomework(currentPage, true)
                }
            } catch (e: Exception) { withContext(Dispatchers.Main) { loadHomework(currentPage, true) } }
        }
    }

    private fun loadHomework(page: Int, isFirstLoad: Boolean) {
        if (isLoading) return
        isLoading = true
        if (isFirstLoad) { progressBar.visibility = View.VISIBLE; textViewEmpty.visibility = View.GONE }
        else adapter.showLoading()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val gid = if (prefs.groupId > 0) prefs.groupId else 12
                val r = homeworkService.getHomeworkList(page = page, status = currentStatus, groupId = gid)
                withContext(Dispatchers.Main) { handleHomeworkResponse(r, page, isFirstLoad) }
            } catch (e: Exception) { withContext(Dispatchers.Main) { handleError(e, isFirstLoad) } }
        }
    }

    private fun handleHomeworkResponse(response: retrofit2.Response<List<HomeworkItem>>, page: Int, isFirstLoad: Boolean) {
        isLoading = false; swipeRefreshLayout.isRefreshing = false
        if (isFirstLoad) progressBar.visibility = View.GONE else adapter.hideLoading()

        when {
            response.isSuccessful -> {
                val list = response.body() ?: emptyList()
                if (page == 1) homeworkList.clear()
                if (list.isNotEmpty()) {
                    homeworkList.addAll(list); adapter.notifyDataSetChanged()
                    currentPage = page; isLastPage = list.size < 6
                    textViewEmpty.visibility = View.GONE
                } else {
                    isLastPage = true
                    if (page == 1) { textViewEmpty.visibility = View.VISIBLE; textViewEmpty.text = "📭 Нет домашних заданий" }
                }
            }
            response.code() == 401 -> handleUnauthorized()
            else -> {
                if (page == 1) { textViewEmpty.visibility = View.VISIBLE; textViewEmpty.text = "⚠️ Ошибка загрузки" }
                else isLastPage = true
            }
        }
    }

    private fun handleError(e: Exception, isFirstLoad: Boolean) {
        isLoading = false; swipeRefreshLayout.isRefreshing = false
        if (isFirstLoad) { progressBar.visibility = View.GONE; textViewEmpty.visibility = View.VISIBLE; textViewEmpty.text = "⚠️ Ошибка загрузки" }
        else isLastPage = true
    }

    private fun refreshHomework() {
        currentPage = 1; isLastPage = false; homeworkList.clear()
        adapter.notifyDataSetChanged(); textViewEmpty.visibility = View.GONE
        if (prefs.groupId == 0) loadUserInfoAndThenHomework() else loadHomework(1, true)
    }

    fun showHomeworkOptionsDialog(homework: HomeworkItem) {
        val options = mutableListOf(" Детальная информация")
        if (canSubmitHomework(homework)) options.add(" Отправить решение")
        if (homework.homework_stud != null) options.add("✏ Изменить решение")
        if (!homework.file_path.isNullOrEmpty()) options.add(" Сделать с AI и сдать")

        AlertDialog.Builder(this).setTitle(" ${homework.theme}")
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    " Отправить решение" -> showSubmitHomeworkDialog(homework)
                    " Детальная информация" -> showHomeworkDetailsDialog(homework)
                    "✏Изменить решение" -> if (homework.homework_stud != null) showEditSolutionDialog(homework)
                    " Сделать с AI и сдать" -> solveWithAiAndSubmit(homework)
                }
            }
            .setNegativeButton("Отмена", null).create().show()
    }

    // =============================================
    // AI ДВИЖОК — полный порт python логики
    // =============================================

    private fun solveWithAiAndSubmit(homework: HomeworkItem) {
        val fileUrl = homework.file_path ?: run {
            Toast.makeText(this, " Нет файла задания", Toast.LENGTH_SHORT).show()
            return
        }

        val pd = ProgressDialog(this).apply {
            setMessage(" Запускаю AI...")
            setCancelable(false)
            show()
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // ШАГ 1: Скачиваем файл задания
                withContext(Dispatchers.Main) { pd.setMessage(" Скачиваю файл задания...") }
                val downloadResult = downloadHomeworkFile(fileUrl)
                if (downloadResult == null) {
                    withContext(Dispatchers.Main) { pd.dismiss(); Toast.makeText(this@HomeworkActivity, "❌ Не удалось скачать файл задания", Toast.LENGTH_LONG).show() }
                    return@launch
                }
                val (fileBytes, filename, extension) = downloadResult

                // ШАГ 2: Извлекаем текст из файла
                withContext(Dispatchers.Main) { pd.setMessage(" Читаю задание...") }
                val taskText = extractTextFromBytes(fileBytes, extension)
                if (taskText.length < 10) {
                    withContext(Dispatchers.Main) { pd.dismiss(); Toast.makeText(this@HomeworkActivity, "❌ Не удалось прочитать текст задания (файл: $filename, тип: $extension)", Toast.LENGTH_LONG).show() }
                    return@launch
                }
                Log.d("AI", "Текст задания (${taskText.length} символов): ${taskText.take(300)}")

                // ШАГ 3: Отправляем в Yandex AI
                withContext(Dispatchers.Main) { pd.setMessage(" AI решает задание...\n(1-2 минуты)") }
                val aiAnswer = callYandexAi(taskText, filename)
                if (aiAnswer == null) {
                    withContext(Dispatchers.Main) { pd.dismiss(); Toast.makeText(this@HomeworkActivity, "❌ AI не ответил. Проверьте YANDEX_API_KEY в коде.", Toast.LENGTH_LONG).show() }
                    return@launch
                }
                Log.d("AI", "Ответ AI (${aiAnswer.length} символов): ${aiAnswer.take(300)}")

                // ШАГ 4: Создаём .docx с ответом
                withContext(Dispatchers.Main) { pd.setMessage(" Создаю документ...") }
                val docxFile = buildDocx(aiAnswer, "ДЗ_${filename.substringBeforeLast('.')}")

                // ШАГ 5: Сдаём задание
                withContext(Dispatchers.Main) { pd.setMessage(" Сдаю домашнее задание...") }
                val ok = submitDocxFile(homework.id, docxFile)

                withContext(Dispatchers.Main) {
                    pd.dismiss()
                    if (ok) {
                        Toast.makeText(this@HomeworkActivity, " AI сделал и сдал задание!", Toast.LENGTH_LONG).show()
                        try { docxFile.delete() } catch (_: Exception) {}
                        refreshHomework()
                    } else {
                        // Файл НЕ удаляем — он нужен для ручной сдачи
                        selectedFileUri = Uri.fromFile(docxFile)
                        selectedFileName = docxFile.name
                        Toast.makeText(this@HomeworkActivity, " AI решил! Авто-сдача не прошла — сдайте вручную", Toast.LENGTH_LONG).show()
                        showSubmitHomeworkDialog(homework)
                    }
                }
            } catch (e: Exception) {
                Log.e("AI", "Ошибка AI", e)
                withContext(Dispatchers.Main) { pd.dismiss(); Toast.makeText(this@HomeworkActivity, "❌ Ошибка AI: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    /** Скачивает файл задания с авторизацией */
    private suspend fun downloadHomeworkFile(url: String): Triple<ByteArray, String, String>? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Authorization", "Bearer ${prefs.accessToken}")
                .header("Referer", "https://journal.top-academy.ru/")
                .build()

            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) { Log.e("AI", "Скачивание failed: ${resp.code} url=$url"); return@withContext null }

            val cd = resp.header("Content-Disposition", "") ?: ""
            val ct = resp.header("Content-Type", "") ?: ""

            var filename = extractFilenameFromCd(cd)
                ?: url.substringAfterLast('/').substringBefore('?').takeIf { it.isNotBlank() }
                ?: "homework.docx"

            var ext = filename.substringAfterLast('.', "").lowercase().take(5)
            if (ext.isEmpty()) {
                ext = when {
                    "pdf" in ct -> "pdf"; "word" in ct || "docx" in ct -> "docx"
                    "msword" in ct -> "doc"; "text/plain" in ct -> "txt"; else -> "docx"
                }
                filename += ".$ext"
            }

            val bytes = resp.body?.bytes() ?: return@withContext null
            Log.d("AI", "Скачан файл: $filename ($ext), ${bytes.size} байт")
            Triple(bytes, filename, ext)
        } catch (e: Exception) {
            Log.e("AI", "downloadHomeworkFile error: ${e.message}")
            null
        }
    }

    private fun extractFilenameFromCd(cd: String): String? {
        if (cd.isBlank()) return null
        return Regex("""filename[^;=\n]*=(['"]?)([^'";\n]+)\1""", RegexOption.IGNORE_CASE)
            .find(cd)?.groupValues?.get(2)?.trim()
    }

    /** Извлекает текст из байтов файла */
    private fun extractTextFromBytes(bytes: ByteArray, ext: String): String {
        return try {
            when (ext.lowercase()) {
                "docx" -> extractFromDocx(bytes)
                "pdf" -> extractFromPdf(bytes)
                "doc" -> extractReadable(bytes)
                "txt", "md", "rtf" -> {
                    for (cs in listOf("UTF-8", "Windows-1251", "ISO-8859-1")) {
                        try {
                            val s = bytes.toString(charset(cs))
                            if (s.count { it.isLetter() } > 20) return s.take(20000)
                        } catch (_: Exception) {}
                    }
                    extractReadable(bytes)
                }
                else -> extractReadable(bytes)
            }
        } catch (e: Exception) {
            Log.e("AI", "extractText error: ${e.message}")
            ""
        }
    }

    /** Читает .docx (ZIP → word/document.xml → убираем XML теги) */
    private fun extractFromDocx(bytes: ByteArray): String {
        return try {
            val zis = ZipInputStream(bytes.inputStream())
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val xml = zis.readBytes().toString(Charsets.UTF_8)
                    zis.close()
                    return xml.replace(Regex("<[^>]+>"), " ")
                        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                        .replace("&quot;", "\"").replace("&apos;", "'")
                        .replace(Regex("\\s+"), " ").trim().take(20000)
                }
                entry = zis.nextEntry
            }
            zis.close()
            extractReadable(bytes)
        } catch (e: Exception) { extractReadable(bytes) }
    }

    /** Простое извлечение текста из PDF (ищем строки в скобках) */
    private fun extractFromPdf(bytes: ByteArray): String {
        return try {
            val raw = bytes.toString(Charsets.ISO_8859_1)
            val sb = StringBuilder()
            // Текст в PDF хранится в скобках: (текст)
            Regex("\\(([^)]{1,300})\\)").findAll(raw).forEach { m ->
                val t = m.groupValues[1]
                    .replace("\\(", "(").replace("\\)", ")").replace("\\n", "\n").replace("\\r", "")
                if (t.count { it.isLetterOrDigit() } > 3) sb.append(t).append(" ")
            }
            val result = sb.toString().replace(Regex("\\s+"), " ").trim()
            if (result.length > 50) result.take(15000)
            else extractReadable(bytes)
        } catch (e: Exception) { extractReadable(bytes) }
    }

    /** Универсальный метод — вытаскивает читаемые символы */
    private fun extractReadable(bytes: ByteArray): String {
        return try {
            // Пробуем Windows-1251 (часто используется в российских файлах)
            val cp1251 = try {
                bytes.toString(charset("Windows-1251"))
                    .filter { it.isLetterOrDigit() || it.isWhitespace() || it in ".,!?:;()\"-" }
                    .take(20000)
            } catch (_: Exception) { "" }

            // Пробуем UTF-8
            val utf8 = try {
                bytes.toString(Charsets.UTF_8)
                    .filter { it.code in 32..65535 || it == '\n' || it == '\t' }
                    .take(20000)
            } catch (_: Exception) { "" }

            // Возвращаем то, в чём больше букв
            val best = if (cp1251.count { it.isLetter() } > utf8.count { it.isLetter() }) cp1251 else utf8
            best.replace(Regex("\\s+"), " ").trim()
        } catch (e: Exception) { "" }
    }

    /** Отправляет запрос к Yandex AI Assistant */
    private suspend fun callYandexAi(taskText: String, filename: String): String? = withContext(Dispatchers.IO) {
        try {
            val prompt = """Ты - опытный студент. Выполни домашнее задание полностью и качественно. Дай развёрнутый ответ.

Файл задания: $filename

Содержание задания:
${taskText.take(3000)}

Выполни задание полностью:"""

            val body = JSONObject().apply {
                put("prompt", JSONObject().apply { put("id", YANDEX_ASSISTANT_ID) })
                put("input", prompt)
            }.toString()

            val req = Request.Builder()
                .url(YANDEX_API_URL)
                .post(body.toRequestBody("application/json".toMediaTypeOrNull()))
                .header("Authorization", "Api-Key $YANDEX_API_KEY")
                .header("Content-Type", "application/json")
                .build()

            val resp = httpClient.newCall(req).execute()
            val respBody = resp.body?.string() ?: return@withContext null

            Log.d("AI", "Yandex HTTP ${resp.code}: ${respBody.take(500)}")

            if (!resp.isSuccessful) { Log.e("AI", "Yandex error: ${resp.code} $respBody"); return@withContext null }

            parseYandexResponse(JSONObject(respBody))
        } catch (e: Exception) {
            Log.e("AI", "callYandexAi error: ${e.message}", e)
            null
        }
    }

    /** Парсит ответ Yandex AI — пробует все возможные форматы */
    private fun parseYandexResponse(json: JSONObject): String? {
        // Формат 1: output_text (новый API)
        json.optString("output_text").takeIf { it.isNotEmpty() }?.let { return it }

        // Формат 2: output -> [] -> text / content -> [] -> text
        json.optJSONArray("output")?.let { out ->
            val sb = StringBuilder()
            for (i in 0 until out.length()) {
                val item = out.optJSONObject(i) ?: continue
                item.optString("text").takeIf { it.isNotEmpty() }?.let { sb.append(it) }
                item.optJSONArray("content")?.let { c ->
                    for (j in 0 until c.length())
                        c.optJSONObject(j)?.optString("text")?.takeIf { it.isNotEmpty() }?.let { sb.append(it) }
                }
            }
            if (sb.isNotEmpty()) return sb.toString()
        }

        // Формат 3: result.alternatives[0].message.text (YandexGPT)
        json.optJSONObject("result")?.optJSONArray("alternatives")
            ?.optJSONObject(0)?.optJSONObject("message")
            ?.optString("text")?.takeIf { it.isNotEmpty() }?.let { return it }

        // Формат 4: choices[0].message.content (OpenAI совместимый)
        json.optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")?.optString("content")
            ?.takeIf { it.isNotEmpty() }?.let { return it }

        Log.e("AI", "Не удалось распарсить ответ: $json")
        return null
    }

    /** Создаёт валидный .docx файл (OOXML = ZIP с XML) */
    private fun buildDocx(content: String, baseName: String): File {
        val file = File(cacheDir, "${baseName}_${System.currentTimeMillis()}.docx")

        // Каждую строку — в отдельный абзац
        val paragraphs = content.split("\n").joinToString("\n") { line ->
            val esc = line.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
            "<w:p><w:r><w:rPr>" +
                    "<w:rFonts w:ascii=\"Times New Roman\" w:hAnsi=\"Times New Roman\"/>" +
                    "<w:sz w:val=\"24\"/>" +
                    "</w:rPr><w:t xml:space=\"preserve\">${esc}</w:t></w:r></w:p>"
        }

        ZipOutputStream(FileOutputStream(file)).use { zip ->
            fun add(name: String, data: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(data.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            add("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
    <Default Extension="xml" ContentType="application/xml"/>
    <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
    <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>""")

            add("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>""")

            add("word/_rels/document.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>""")

            add("word/document.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
    <w:body>
        $paragraphs
        <w:sectPr>
            <w:pgSz w:w="11906" w:h="16838"/>
            <w:pgMar w:top="1134" w:right="850" w:bottom="1134" w:left="1701"/>
        </w:sectPr>
    </w:body>
</w:document>""")

            add("word/styles.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
    <w:docDefaults>
        <w:rPrDefault><w:rPr>
            <w:rFonts w:ascii="Times New Roman" w:hAnsi="Times New Roman"/>
            <w:sz w:val="24"/>
        </w:rPr></w:rPrDefault>
    </w:docDefaults>
</w:styles>""")
        }

        return file
    }

    /** Сдаёт .docx как домашнее задание */
    private suspend fun submitDocxFile(homeworkId: Int, file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val mime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            val response = homeworkService.submitHomework(
                id = homeworkId.toString().toRequestBody("text/plain".toMediaTypeOrNull()),
                file = MultipartBody.Part.createFormData("file", file.name, file.asRequestBody(mime.toMediaTypeOrNull())),
                answerText = "Выполнено с помощью AI-ассистента".toRequestBody("text/plain".toMediaTypeOrNull()),
                spentTimeHour = "0".toRequestBody("text/plain".toMediaTypeOrNull()),
                spentTimeMin = "0".toRequestBody("text/plain".toMediaTypeOrNull())
            )
            Log.d("AI", "submit response: ${response.code()}")
            response.isSuccessful
        } catch (e: Exception) {
            Log.e("AI", "submitDocx error: ${e.message}")
            false
        }
    }

    // =============================================
    // СТАНДАРТНЫЕ ФУНКЦИИ
    // =============================================

    private fun canSubmitHomework(hw: HomeworkItem): Boolean = hw.status in 2..4

    fun showSubmitHomeworkDialog(homework: HomeworkItem) {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_submit_homework, null)
        val ans = v.findViewById<EditText>(R.id.editTextAnswer)
        val h = v.findViewById<EditText>(R.id.editTextHours)
        val m = v.findViewById<EditText>(R.id.editTextMinutes)
        val fn = v.findViewById<TextView>(R.id.textViewFileName)
        val bSel = v.findViewById<Button>(R.id.buttonSelectFile)
        val bRem = v.findViewById<Button>(R.id.buttonRemoveFile)

        currentHomeworkId = homework.id
        homework.homework_stud?.let { ans.setText(it.stud_answer ?: "") }
        bSel.setOnClickListener { openFilePicker() }
        bRem.setOnClickListener { selectedFileUri = null; selectedFileName = null; fn.text = "Файл не выбран"; bRem.isVisible = false }
        selectedFileName?.let { fn.text = "Выбран: $it"; bRem.isVisible = true }

        AlertDialog.Builder(this).setTitle(" Отправить решение").setView(v)
            .setPositiveButton("Отправить") { _, _ ->
                val a = ans.text.toString(); val hr = h.text.toString().toIntOrNull() ?: 0; val mn = m.text.toString().toIntOrNull() ?: 0
                if (selectedFileUri != null || a.isNotEmpty()) submitHomework(homework.id, a, hr, mn, selectedFileUri)
                else Toast.makeText(this, "Добавьте файл или текстовый ответ", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Отмена", null).create().show()
    }

    fun showEditSolutionDialog(homework: HomeworkItem) {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_submit_homework, null)
        val ans = v.findViewById<EditText>(R.id.editTextAnswer)
        val h = v.findViewById<EditText>(R.id.editTextHours)
        val m = v.findViewById<EditText>(R.id.editTextMinutes)
        val fn = v.findViewById<TextView>(R.id.textViewFileName)
        val bSel = v.findViewById<Button>(R.id.buttonSelectFile)
        val bRem = v.findViewById<Button>(R.id.buttonRemoveFile)

        currentHomeworkId = homework.id
        homework.homework_stud?.let { s -> ans.setText(s.stud_answer ?: ""); if (!s.file_path.isNullOrEmpty()) fn.text = "Текущий: ${s.filename ?: "Файл"}" }
        bSel.setOnClickListener { openFilePicker() }
        bRem.isVisible = true; bRem.setOnClickListener { selectedFileUri = null; selectedFileName = null; fn.text = "Выберите новый файл" }
        selectedFileName?.let { fn.text = "Выбран новый: $it" }

        AlertDialog.Builder(this).setTitle("✏ Изменить решение").setView(v)
            .setPositiveButton("Обновить") { _, _ ->
                val a = ans.text.toString(); val hr = h.text.toString().toIntOrNull() ?: 0; val mn = m.text.toString().toIntOrNull() ?: 0
                if (selectedFileUri != null || a.isNotEmpty() || homework.homework_stud?.file_path != null) submitHomework(homework.id, a, hr, mn, selectedFileUri)
                else Toast.makeText(this, "Добавьте файл или ответ", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Отмена", null).create().show()
    }

    fun showHomeworkDetailsDialog(hw: HomeworkItem) {
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val ddf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val st = when (hw.status) { 1 -> " Проверено"; 2 -> " На проверке"; 3 -> "Не сдано"; 4 -> " Просрочено"; else -> " Неизвестно" }
        val msg = """📚 ${hw.name_spec}
 Тема: ${hw.theme}
 ${hw.fio_teach}

 Задано: ${try { ddf.format(df.parse(hw.creation_time)!!) } catch (_: Exception) { hw.creation_time }}
 Сдать до: ${try { ddf.format(df.parse(hw.completion_time)!!) } catch (_: Exception) { hw.completion_time }}
 Статус: $st

${hw.comment?.takeIf { it.isNotEmpty() }?.let { " Комментарий:\n$it\n\n" } ?: ""}${hw.homework_stud?.let { s ->
            buildString {
                if (!s.stud_answer.isNullOrEmpty()) append(" Ваш ответ:\n${s.stud_answer}\n")
                if (!s.file_path.isNullOrEmpty()) append(" Файл: ${s.filename ?: "Файл"}\n")
                s.mark?.let { append(" Оценка: $it") }
            }
        } ?: " Решение не отправлено"}""".trimIndent()
        AlertDialog.Builder(this).setTitle(" Информация").setMessage(msg).setPositiveButton("OK", null).show()
    }

    private fun openFilePicker() {
        startActivityForResult(
            Intent.createChooser(Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE) }, "Выберите файл"),
            PICK_FILE_REQUEST_CODE
        )
    }

    private fun submitHomework(homeworkId: Int, answerText: String, hours: Int, minutes: Int, fileUri: Uri?) {
        val pd = ProgressDialog(this).apply { setMessage("Отправка..."); setCancelable(false); show() }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                var filePart: MultipartBody.Part? = null
                fileUri?.let { uri ->
                    val f = uriToFile(uri)
                    Log.d("SUBMIT", "File: ${f.absolutePath}, exists=${f.exists()}, size=${f.length()}, uri_scheme=${uri.scheme}")
                    if (f.exists() && f.length() > 0) {
                        filePart = MultipartBody.Part.createFormData("file", f.name, f.asRequestBody("application/octet-stream".toMediaTypeOrNull()))
                    } else {
                        // Последняя попытка — читаем напрямую из потока
                        try {
                            val bytes = contentResolver.openInputStream(uri)?.readBytes()
                            if (bytes != null && bytes.isNotEmpty()) {
                                f.writeBytes(bytes)
                                filePart = MultipartBody.Part.createFormData("file", f.name, f.asRequestBody("application/octet-stream".toMediaTypeOrNull()))
                            } else {
                                withContext(Dispatchers.Main) { pd.dismiss(); Toast.makeText(this@HomeworkActivity, "❌ Файл пустой или недоступен", Toast.LENGTH_SHORT).show() }
                                return@launch
                            }
                        } catch (ex: Exception) {
                            Log.e("SUBMIT", "Fallback read error: ${ex.message}")
                            withContext(Dispatchers.Main) { pd.dismiss(); Toast.makeText(this@HomeworkActivity, "❌ Не удалось прочитать файл: ${ex.message}", Toast.LENGTH_LONG).show() }
                            return@launch
                        }
                    }
                }
                val r = homeworkService.submitHomework(
                    id = homeworkId.toString().toRequestBody("text/plain".toMediaTypeOrNull()),
                    file = filePart,
                    answerText = answerText.toRequestBody("text/plain".toMediaTypeOrNull()),
                    spentTimeHour = hours.toString().toRequestBody("text/plain".toMediaTypeOrNull()),
                    spentTimeMin = minutes.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                )
                withContext(Dispatchers.Main) {
                    pd.dismiss()
                    if (r.isSuccessful) { Toast.makeText(this@HomeworkActivity, "✅ Решение отправлено!", Toast.LENGTH_SHORT).show(); selectedFileUri = null; selectedFileName = null; refreshHomework() }
                    else Toast.makeText(this@HomeworkActivity, " Ошибка: ${r.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) { withContext(Dispatchers.Main) { pd.dismiss(); Toast.makeText(this@HomeworkActivity, "⚠️ ${e.message}", Toast.LENGTH_LONG).show() } }
        }
    }

    private fun uriToFile(uri: Uri): File {
        return try {
            // file:// URI — файл уже на диске, просто возвращаем его
            if (uri.scheme == "file") {
                val file = File(uri.path!!)
                if (file.exists() && file.length() > 0) return file
            }

            // content:// URI — копируем через contentResolver
            val filename = getFileName(uri).ifBlank { "tmp_${System.currentTimeMillis()}" }
            val f = File(cacheDir, filename)
            val input = contentResolver.openInputStream(uri)
            if (input != null) {
                FileOutputStream(f).use { input.copyTo(it) }
                input.close()
            }
            f
        } catch (e: Exception) {
            Log.e("AI", "uriToFile error: ${e.message}")
            File(cacheDir, "tmp_${System.currentTimeMillis()}")
        }
    }

    private fun getFileName(uri: Uri): String {
        var r: String? = null
        if (uri.scheme == "content") contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) { val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (i != -1) r = c.getString(i) }
        }
        return r ?: uri.path?.substringAfterLast('/') ?: "file_${System.currentTimeMillis()}"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FILE_REQUEST_CODE && resultCode == RESULT_OK)
            data?.data?.let { uri -> selectedFileUri = uri; selectedFileName = getFileName(uri); Toast.makeText(this, "✅ Файл: $selectedFileName", Toast.LENGTH_SHORT).show() }
    }

    private fun handleUnauthorized() { prefs.clear(); navigateToLogin() }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK })
        finish()
    }

    fun downloadFile(url: String, fileName: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: Exception) { Toast.makeText(this, "⚠️ Не удалось открыть файл", Toast.LENGTH_SHORT).show() }
    }

    // =============================================
    // АДАПТЕР
    // =============================================

    private inner class HomeworkAdapter(
        private val items: MutableList<HomeworkItem>,
        private val onClick: (HomeworkItem) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val TYPE_ITEM = 0; private val TYPE_LOADING = 1
        private var isLoading = false

        inner class HW(v: View) : RecyclerView.ViewHolder(v) {
            val container: LinearLayout = v.findViewById(R.id.container)
            val subject: TextView = v.findViewById(R.id.textSubject)
            val theme: TextView = v.findViewById(R.id.textTheme)
            val teacher: TextView = v.findViewById(R.id.textTeacher)
            val dates: TextView = v.findViewById(R.id.textDates)
            val status: TextView = v.findViewById(R.id.textStatus)
            val mark: TextView = v.findViewById(R.id.textMark)
            val btnTask: Button = v.findViewById(R.id.buttonDownloadTask)
            val btnSol: Button = v.findViewById(R.id.buttonDownloadSolution)
            val btnsCont: LinearLayout = v.findViewById(R.id.buttonsContainer)
            val btnSubmit: Button = v.findViewById(R.id.buttonSubmit)
        }
        inner class Loading(v: View) : RecyclerView.ViewHolder(v) { val pb: ProgressBar = v.findViewById(R.id.progressBar) }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            if (viewType == TYPE_ITEM) HW(LayoutInflater.from(parent.context).inflate(R.layout.item_homework, parent, false))
            else Loading(LayoutInflater.from(parent.context).inflate(R.layout.item_loading, parent, false))

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder !is HW || position >= items.size) return
            val hw = items[position]

            holder.subject.text = " ${hw.name_spec}"
            holder.theme.text = " ${hw.theme}"
            holder.teacher.text = " ${hw.fio_teach}"

            val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val ddf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            holder.dates.text = buildString {
                try { df.parse(hw.creation_time)?.let { append("Задано: ${ddf.format(it)}\n") } } catch (_: Exception) {}
                try { df.parse(hw.completion_time)?.let { append("Сдать до: ${ddf.format(it)}") } } catch (_: Exception) {}
            }

            val (st, sc) = when (hw.status) {
                1 -> "Проверено" to "#388E3C"; 2 -> "На проверке" to "#F57C00"
                3 -> "Не сдано" to "#D32F2F"; 4 -> "Просрочено" to "#FF9800"
                else -> "Неизвестно" to "#757575"
            }
            holder.status.text = st; holder.status.setTextColor(Color.parseColor(sc))

            hw.homework_stud?.mark?.let { holder.mark.text = " Оценка: $it"; holder.mark.visibility = View.VISIBLE }
                ?: run { holder.mark.visibility = View.GONE }

            holder.btnsCont.removeAllViews()
            if (!hw.file_path.isNullOrEmpty()) {
                holder.btnTask.visibility = View.VISIBLE
                holder.btnTask.setOnClickListener { downloadFile(hw.file_path, hw.filename ?: "hw_${hw.id}") }
                holder.btnsCont.addView(holder.btnTask)
            } else holder.btnTask.visibility = View.GONE

            hw.homework_stud?.file_path?.let { path ->
                holder.btnSol.visibility = View.VISIBLE
                holder.btnSol.setOnClickListener { downloadFile(path, "solution_${hw.id}") }
                holder.btnsCont.addView(holder.btnSol)
            } ?: run { holder.btnSol.visibility = View.GONE }

            if (hw.status in listOf(2, 3, 4)) {
                holder.btnSubmit.visibility = View.VISIBLE
                holder.btnSubmit.text = when {
                    hw.homework_stud != null -> "✏️ Изменить решение"
                    hw.status == 4 -> "📤 Сдать (просрочено)"
                    hw.status == 3 -> "📤 Сдать (не сдано)"
                    else -> "📤 Отправить решение"
                }
                holder.btnSubmit.setOnClickListener { if (hw.homework_stud != null) showEditSolutionDialog(hw) else showSubmitHomeworkDialog(hw) }
                holder.btnSubmit.setBackgroundColor(Color.parseColor(when (hw.status) { 4 -> "#FF9800"; 3 -> "#D32F2F"; else -> "#4CAF50" }))
            } else holder.btnSubmit.visibility = View.GONE

            holder.container.setOnClickListener { onClick(hw) }
        }

        override fun getItemViewType(position: Int) = if (isLoading && position == items.size) TYPE_LOADING else TYPE_ITEM
        override fun getItemCount() = items.size + if (isLoading) 1 else 0
        fun showLoading() { if (!isLoading) { isLoading = true; notifyItemInserted(items.size) } }
        fun hideLoading() { if (isLoading) { isLoading = false; notifyItemRemoved(items.size) } }
    }
}