package com.example.fantasyfootballqb.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.AuthResult
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AuthRepository {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    suspend fun register(email: String, password: String, username: String): Result<String> {
        return try {
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = authResult.user?.uid ?: return Result.failure(Exception("UID non disponibile"))

            val userMap = mapOf(
                "email" to email,
                "username" to username,
                "nomeTeam" to "",
                "isAdmin" to false
            )

            db.collection("users").document(uid).set(userMap).await()

            Result.success(uid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    suspend fun login(email: String, password: String): Result<String> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val uid = authResult.user?.uid ?: return Result.failure(Exception("UID non disponibile"))
            Result.success(uid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() {
        auth.signOut()
    }

    fun getCurrentUid(): String? = auth.currentUser?.uid
}
