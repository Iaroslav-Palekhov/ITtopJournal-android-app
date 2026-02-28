// CreateHomeworkRequest.kt
package ru.termux.topacademy.model

data class CreateHomeworkRequest(
    val id: Int,  // ID домашнего задания
    val answerText: String? = null,
    val spentTimeHour: Int? = null,
    val spentTimeMin: Int? = null
)

data class SaveEvaluationRequest(
    val EvaluationHomeworkForm: EvaluationHomeworkForm
)

data class EvaluationHomeworkForm(
    val id: Int? = null,
    val idDomZad: Int,  // ID домашнего задания
    val idStud: Int? = null,
    val mark: Int,  // Оценка (от 1 до 5)
    val comment: String? = null,
    val tags: List<String> = emptyList()
)

data class EvaluationResponse(
    val id: Int,
    val id_dom_zad: Int,
    val id_stud: Int,
    val mark: Int,
    val comment: String?,
    val tags: List<String>
)