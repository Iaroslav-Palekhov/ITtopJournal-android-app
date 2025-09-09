package ru.termux.topacademy.model

data class Profile(
    val ful_name: String?,
    val email: String?,
    val date_birth: String?,
    val address: String?,
    val study: String?,
    val phones: List<Phone>?,
    val links: List<Link>?,
    val fill_percentage: Int?,
    val photo_path: String?
)

data class Phone(
    val phone_number: String?
)

data class Link(
    val name: String?,
    val value: String?
)