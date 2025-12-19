package com.example.itrsproject


import android.R.attr.x
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleCoroutineScope
import com.example.itrsproject.gigachatapi.ApiBuilder
import androidx.lifecycle.lifecycleScope
import com.example.itrsproject.models.TextEntry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import okhttp3.Dispatcher
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Objects
import java.util.UUID
import kotlin.math.PI
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainScreen()
            }
        }
    }
}

@SuppressLint("CoroutineCreationDuringComposition")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var selectedTab by remember { mutableStateOf("saved") }
    var fileIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var context: Context = LocalContext.current
    var extractedText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val auth = FirebaseAuth.getInstance()
    var isLoading by remember { mutableStateOf(false) }
    var savedList by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(auth.currentUser) {
        if (auth.currentUser == null) {
            selectedTab = "login"
        }
    }

    val showBottomBar = selectedTab != "login" && selectedTab != "register"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    if (showBottomBar) {
                        IconButton(onClick = { selectedTab = "infoHelp" }) {
                            Icon(
                                imageVector = Icons.Default.HelpOutline,
                                contentDescription = "Информация и помощь",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                },
                actions = {
                    if (auth.currentUser != null && showBottomBar) {
                        IconButton(onClick = {
                            auth.signOut()
                            selectedTab = "login"
                        }) {
                            Icon(Icons.Default.Logout, contentDescription = "Выход")
                        }
                    } else if (showBottomBar) {
                        IconButton(onClick = { }) {
                            Icon(Icons.Default.Settings, contentDescription = "Настройки")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    val context = LocalContext.current
                    val imagePickerLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.GetMultipleContents()
                    ) { uris ->
                        if (uris.isNotEmpty()) {
                            scope.launch {
                                isLoading = true
                                try {
                                    val builder = ApiBuilder()
                                    extractedText = builder.extractTextFromPhoto(uris, context)
                                    selectedTab = "imageToText"
                                } catch (e: Exception) {
                                    extractedText = ""
                                    selectedTab = "error"
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    }

                    NavigationBarItem(
                        selected = false,
                        onClick = { selectedTab = "saved" },
                        icon = { Icon(Icons.Default.Storage, contentDescription = "Созданные файлы") }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { imagePickerLauncher.launch("image/*") },
                        icon = { Icon(Icons.Default.CameraAlt, contentDescription = "Загрузить фото") }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = {
                            val text = extractedText.trim().ifEmpty { "Текст отсутствует" }
                            shareText(context, text, "Поделиться текстом")
                        },
                        icon = { Icon(Icons.Default.Share, contentDescription = "Поделиться") }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                LoadingScreen()
            } else {
                when (selectedTab) {
                    "home" -> HomeContent(
                        onImagesPicked = { uris ->
                            scope.launch {
                                isLoading = true
                                try {
                                    val builder = ApiBuilder()
                                    extractedText = builder.extractTextFromPhoto(uris, context)
                                    selectedTab = "imageToText"
                                } catch (e: Exception) {
                                    extractedText = ""
                                    selectedTab = "error"
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        onOpenSaved = { selectedTab = "saved" }
                    )
                    "info" -> InfoContent()
                    "help" -> HelpContent()
                    "imageToText" -> transformedImageToTextContext(
                        extractedText = extractedText,
                        onSave = { text ->
                            val db = DatabaseService()
                            val userId = auth.currentUser?.uid ?: return@transformedImageToTextContext
                            val entryId = UUID.randomUUID().toString()
                            db.saveText(text, entryId, auth.currentUser?.email ?: "")
                            selectedTab = "home"
                        }
                    )
                    "login" -> LoginContent(
                        onNavigateToRegister = { selectedTab = "register" },
                        onLoginSuccess = { selectedTab = "home" }
                    )
                    "register" -> RegisterContent(
                        onNavigateToLogin = { selectedTab = "login" },
                        onRegisterSuccess = { selectedTab = "home" }
                    )
                    "saved" -> SavedTextsScreen()
                    "infoHelp" -> InfoHelpScreen()
                }
            }
        }
    }
}

@Composable
fun LoginContent(
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val auth = FirebaseAuth.getInstance()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Вход", fontSize = 22.sp, fontWeight = FontWeight.Bold)

        errorMessage?.let { message ->
            Text(
                text = message,
                color = Color.Red,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }

        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                errorMessage = null
            },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            enabled = !isLoading
        )

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                errorMessage = null
            },
            label = { Text("Пароль") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            visualTransformation = PasswordVisualTransformation(),
            enabled = !isLoading
        )

        Button(
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    errorMessage = "Заполните все поля"
                    return@Button
                }

                isLoading = true
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        isLoading = false
                        if (task.isSuccessful) {
                            onLoginSuccess()
                        } else {
                            errorMessage = task.exception?.message ?: "Ошибка входа"
                        }
                    }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Войти", fontSize = 18.sp)
            }
        }

        TextButton(
            onClick = onNavigateToRegister,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Text("Нет аккаунта? Зарегистрируйтесь", fontSize = 16.sp)
        }
    }
}

@Composable
fun RegisterContent(
    onNavigateToLogin: () -> Unit,
    onRegisterSuccess: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val auth = FirebaseAuth.getInstance()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Регистрация", fontSize = 22.sp, fontWeight = FontWeight.Bold)

        errorMessage?.let { message ->
            Text(
                text = message,
                color = Color.Red,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }

        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                errorMessage = null
            },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            enabled = !isLoading
        )

        OutlinedTextField(
            value = phone,
            onValueChange = {
                val newValue = it.filter { char -> char.isDigit() || char == '+' }
                phone = newValue
                errorMessage = null
            },
            label = { Text("Номер телефона") },
            placeholder = { Text("+79991234567") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
        )

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                errorMessage = null
            },
            label = { Text("Пароль") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            visualTransformation = PasswordVisualTransformation(),
            enabled = !isLoading
        )

        OutlinedTextField(
            value = repeat,
            onValueChange = {
                repeat = it
                errorMessage = null
            },
            label = { Text("Повторите пароль") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            visualTransformation = PasswordVisualTransformation(),
            enabled = !isLoading
        )

        Button(
            onClick = {
                when {
                    email.isBlank() || password.isBlank() || phone.isBlank() -> {
                        errorMessage = "Заполните все поля"
                        return@Button
                    }
                    password != repeat -> {
                        errorMessage = "Пароли не совпадают"
                        return@Button
                    }
                    password.length < 6 -> {
                        errorMessage = "Пароль должен содержать минимум 6 символов"
                        return@Button
                    }
                    phone.length < 10 -> {
                        errorMessage = "Введите корректный номер телефона"
                        return@Button
                    }
                }

                isLoading = true
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val user = auth.currentUser
                            user?.let {
                                saveUserPhoneNumber(it.uid, phone, email) { success ->
                                    isLoading = false
                                    if (success) {
                                        onRegisterSuccess()
                                    } else {
                                        errorMessage = "Ошибка сохранения данных"
                                    }
                                }
                            }
                        } else {
                            isLoading = false
                            errorMessage = task.exception?.message ?: "Ошибка регистрации"
                        }
                    }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Создать аккаунт", fontSize = 18.sp)
            }
        }

        TextButton(
            onClick = onNavigateToLogin,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Text("Уже есть аккаунт? Войдите", fontSize = 16.sp)
        }
    }
}

private fun saveUserPhoneNumber(
    userId: String,
    phone: String,
    email: String,
    onComplete: (Boolean) -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val userData = hashMapOf(
        "phone" to phone,
        "email" to email,
        "createdAt" to FieldValue.serverTimestamp()
    )

    db.collection("users")
        .document(userId)
        .set(userData)
        .addOnSuccessListener {
            onComplete(true)
        }
        .addOnFailureListener {
            onComplete(false)
        }
}

@Composable
fun HomeContent(
    onImagesPicked: (List<Uri>) -> Unit,
    onOpenSaved: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "Добро пожаловать, ${auth.currentUser?.email ?: "Пользователь"}!",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
@Composable
fun transformedImageToTextContext(
    extractedText: String,
    onSave: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Распознанный текст:",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.LightGray.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            val lines = extractedText.ifEmpty { "Текст не распознан" }.lines()
            items(lines) { line ->
                Text(
                    text = line,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onSave(extractedText) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Сохранить", fontSize = 18.sp)
        }
    }
}


@Composable
fun InfoContent() {
    Text(
        text = "Приложение преобразует рукописный текст с фото в аудиофайлы, " +
                "позволяет структурировать материал и сохранять в виде книги.",
        fontSize = 16.sp,
        lineHeight = 24.sp
    )
}

@Composable
fun HelpContent() {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Инструкция по использованию:",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text("1. Загрузите фото.", fontSize = 16.sp)
        Text("2. Дождитесь распознавания текста.", fontSize = 16.sp)
        Text("3. Сгенерируйте аудио.", fontSize = 16.sp)
        Text("4. Сохраните или поделитесь результатом.", fontSize = 16.sp)
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun SavedTextsScreen() {
    val auth = FirebaseAuth.getInstance()
    val dbService = remember { DatabaseService() }
    var items by remember { mutableStateOf<List<TextEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedEntry by remember { mutableStateOf<TextEntry?>(null) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(key1 = auth.currentUser?.email) {
        isLoading = true
        error = null
        val email = auth.currentUser?.email ?: ""
        dbService.getEntriesForUser(
            email,
            onSuccess = { list ->
                items = list
                isLoading = false
            },
            onFailure = { e ->
                error = e.message ?: "Ошибка загрузки"
                items = emptyList()
                isLoading = false
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(8.dp)
        ) {
            Text(
                "Сохранённые записи",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(8.dp)
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            error?.let {
                Text("Ошибка: $it", color = Color.Red, modifier = Modifier.padding(8.dp))
            }

            if (items.isEmpty()) {
                Text("Записи отсутствуют", modifier = Modifier.padding(8.dp))
                return@Column
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items) { entry ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedEntry = entry }
                            .padding(horizontal = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = entry.text.take(120).ifEmpty { "(Пустая запись)" },
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = entry.createdAt?.toDate()?.toString() ?: "",
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        selectedEntry?.let { entry ->
            val scrollState = rememberScrollState()
            val context = LocalContext.current
            var isPlaying by remember { mutableStateOf(false) }
            var isExporting by remember { mutableStateOf(false) }

            val tts = remember {
                TextToSpeech(context) { status ->
                    if (status != TextToSpeech.SUCCESS) {
                        Log.e("TTS", "Ошибка инициализации")
                    }
                }
            }

            DisposableEffect(entry.id) {
                onDispose {
                    tts.stop()
                    tts.shutdown()
                }
            }

            AlertDialog(
                onDismissRequest = {
                    if (isPlaying) tts.stop()
                    selectedEntry = null
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (isPlaying) tts.stop()
                        selectedEntry = null
                    }) { Text("Закрыть") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        if (isPlaying) tts.stop()
                        selectedEntry = null
                        scope.launch {
                            dbService.deleteEntry(entry.id) { ok ->
                                if (ok) {
                                    items = items.filterNot { it.id == entry.id }
                                    scope.launch { snackbarHostState.showSnackbar("Запись успешно удалена") }
                                }
                            }
                        }
                    }) { Text("Удалить") }
                },
                title = { Text("Запись") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val entryText = entry.text.ifEmpty { "(Пустая запись)" }

                        Text(text = entryText)
                        Text(
                            text = entry.createdAt?.toDate()?.toString() ?: "",
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (isPlaying) {
                                    tts.stop()
                                    isPlaying = false
                                } else {
                                    tts.speak(
                                        entryText,
                                        TextToSpeech.QUEUE_FLUSH,
                                        null,
                                        entry.id
                                    )
                                    isPlaying = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            val icon = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow
                            Icon(icon, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isPlaying) "Остановить" else "Проиграть")
                        }

                        Button(
                            onClick = {
                                if (isExporting) return@Button
                                isExporting = true
                                scope.launch {
                                    try {
                                        SpeechifyApiService.instance.init(context)
                                        val audioFile = SpeechifyApiService.instance.synthesize(entryText)
                                        shareAudioFile(context, audioFile, "Поделиться аудио")
                                    } finally {
                                        isExporting = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isExporting
                        ) {
                            if (isExporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Подготовка аудио…")
                            } else {
                                Icon(Icons.Default.Share, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Поделиться аудио")
                            }
                        }
                    }
                }
            )
        }
    }
}


@Composable
fun InfoHelpScreen() {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        item {
            Text(
                text = "О проекте",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Приложение преобразует рукописный текст с фото в аудиофайлы, " +
                        "позволяет структурировать материал и сохранять в виде книги.",
                fontSize = 16.sp,
                lineHeight = 24.sp
            )
        }
        item {
            Text(
                text = "Помощь",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text("1. Загрузите фото.", fontSize = 16.sp)
            Text("2. Дождитесь распознавания текста.", fontSize = 16.sp)
            Text("3. Сгенерируйте аудио.", fontSize = 16.sp)
            Text("4. Сохраните или поделитесь результатом.", fontSize = 16.sp)
        }
    }
}

fun shareText(context: Context, text: String, title: String = "Поделиться") {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, title))
}

fun shareAudioFile(context: Context, file: File, title: String = "Поделиться аудио") {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )

    val mime = when (file.extension.lowercase()) {
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        "aac" -> "audio/aac"
        "m4a" -> "audio/mp4"
        else -> "audio/*"
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, title))
}