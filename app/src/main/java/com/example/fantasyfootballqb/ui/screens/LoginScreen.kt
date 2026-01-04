package com.example.fantasyfootballqb.ui.screens

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.example.fantasyfootballqb.R



@Composable
fun LoginScreen(
    onLoginNavigate: (isAdmin: Boolean) -> Unit,
    onRegister: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "App logo",
                    modifier = Modifier
                        .size(140.dp),
                    contentScale = ContentScale.Fit
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(text = "Accedi", style = MaterialTheme.typography.titleLarge)

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (passwordVisible)
                        VisualTransformation.None
                    else
                        PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = {
                            passwordVisible = !passwordVisible
                        }) {
                            Icon(
                                imageVector = if (passwordVisible)
                                    Icons.Default.VisibilityOff
                                else
                                    Icons.Default.Visibility,
                                contentDescription = if (passwordVisible)
                                    "Nascondi password"
                                else
                                    "Mostra password"
                            )
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = {
                    scope.launch {
                        try {
                            loading = true
                            val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
                            val user = auth.currentUser
                            if (user == null) {
                                snackbarHostState.showSnackbar("Login non riuscito")
                                loading = false
                                return@launch
                            }

                            val uid = user.uid
                            val doc = db.collection("users").document(uid).get().await()
                            val isAdmin = doc.exists() && (doc.getBoolean("isAdmin") == true)
                            // naviga in base al valore isAdmin
                            onLoginNavigate(isAdmin)
                        } catch (e: Exception) {
                            Log.e("LoginScreen", "login error: ${e.message}", e)
                            snackbarHostState.showSnackbar(e.message ?: "Errore login")
                        } finally {
                            loading = false
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Accedi")
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onRegister) {
                    Text("Non sei registrato? Registrati adesso")
                }
            }

            if (loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}
