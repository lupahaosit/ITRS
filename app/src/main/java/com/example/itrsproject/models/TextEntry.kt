package com.example.itrsproject.models

data class TextEntry(
    val id: String = "",
    val email: String = "",
    val text: String = "",
    val createdAt: com.google.firebase.Timestamp? = null
)