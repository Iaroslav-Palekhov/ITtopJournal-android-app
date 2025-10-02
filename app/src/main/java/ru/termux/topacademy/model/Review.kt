package ru.termux.topacademy.model

import com.google.gson.annotations.SerializedName

data class Review(
    @SerializedName("date") val date: String,
    @SerializedName("message") val message: String,
    @SerializedName("spec") val spec: String,
    @SerializedName("full_spec") val fullSpec: String,
    @SerializedName("teacher") val teacher: String
)