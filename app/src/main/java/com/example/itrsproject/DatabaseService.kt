package com.example.itrsproject

import com.example.itrsproject.models.TextEntry
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class DatabaseService {
    private val database = FirebaseFirestore.getInstance()

    fun saveText(text: String, id: String, user: String) {
        val map = hashMapOf(
            "id" to id,
            "email" to user,
            "text" to text,
            "createdAt" to FieldValue.serverTimestamp()
        )

        database.collection("entries")
            .document(id)
            .set(map)
    }

    fun getEntriesForUser(
        email: String,
        onSuccess: (List<TextEntry>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (email.isBlank()) {
            onSuccess(emptyList())
            return
        }

        database.collection("entries")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { snapshot ->
                val list = snapshot.documents
                    .mapNotNull { doc ->
                        try {
                            TextEntry(
                                id = doc.getString("id") ?: doc.id,
                                email = doc.getString("email") ?: "",
                                text = doc.getString("text") ?: "",
                                createdAt = doc.getTimestamp("createdAt")
                            )
                        } catch (_: Exception) {
                            null
                        }
                    }
                    .sortedByDescending { it.createdAt?.toDate() }

                onSuccess(list)
            }
            .addOnFailureListener(onFailure)
    }

    fun deleteEntry(id: String, onComplete: (Boolean) -> Unit) {
        database.collection("entries")
            .document(id)
            .delete()
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }
}
