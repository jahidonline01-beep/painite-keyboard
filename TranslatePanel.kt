package com.painite.keyboard.ui.translate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.painite.keyboard.ui.theme.KeyboardTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder

@Composable
fun TranslatePanel(
    theme: KeyboardTheme,
    onInsertText: (String) -> Unit,
    onClose: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var outputText by remember { mutableStateOf("") }
    var fromLang by remember { mutableStateOf("en") }
    var toLang by remember { mutableStateOf("bn") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp, max = 260.dp)
            .background(theme.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.rowbarBg)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Translate", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = theme.keyTextColor, modifier = Modifier.size(16.dp))
            }
        }

        // Language selector row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LangChip(theme = theme, lang = fromLang) {
                val newFrom = if (fromLang == "en") "bn" else "en"
                fromLang = newFrom
                toLang = if (newFrom == "en") "bn" else "en"
            }
            Icon(
                Icons.Default.SyncAlt,
                contentDescription = "Swap",
                tint = theme.accentSecondary,
                modifier = Modifier
                    .size(18.dp)
                    .clickable {
                        val tmp = fromLang
                        fromLang = toLang
                        toLang = tmp
                    }
            )
            LangChip(theme = theme, lang = toLang) {
                val newTo = if (toLang == "en") "bn" else "en"
                toLang = newTo
                fromLang = if (newTo == "en") "bn" else "en"
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(theme.gradient)
                    .clickable {
                        if (inputText.isNotBlank()) {
                            scope.launch {
                                isLoading = true
                                outputText = translateText(inputText, fromLang, toLang)
                                isLoading = false
                            }
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Go", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Input field
        TextField(
            value = inputText,
            onValueChange = { inputText = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .heightIn(max = 60.dp),
            placeholder = {
                Text(
                    "Type to translate…",
                    fontSize = 13.sp,
                    color = theme.keyTextColor.copy(alpha = 0.4f)
                )
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = theme.keyBackground,
                unfocusedContainerColor = theme.keyBackground,
                focusedTextColor = theme.keyTextColor,
                unfocusedTextColor = theme.keyTextColor,
                cursorColor = theme.accentColor,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
            shape = RoundedCornerShape(8.dp),
            maxLines = 2
        )

        // Output result
        if (outputText.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(theme.keyBackground)
                    .border(0.5.dp, theme.accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    outputText,
                    color = theme.accentColor,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { onInsertText(outputText) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.ContentPaste,
                        contentDescription = "Insert",
                        tint = theme.accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LangChip(theme: KeyboardTheme, lang: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(theme.keyBackground)
            .border(0.5.dp, theme.accentColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = if (lang == "en") "English" else "বাংলা",
            color = theme.accentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private suspend fun translateText(text: String, from: String, to: String): String {
    return withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(text, "UTF-8")
            val url = java.net.URL(
                "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$from&tl=$to&dt=t&q=$encoded"
            )
            val connection = (url.openConnection() as java.net.HttpURLConnection).apply {
                setRequestProperty("User-Agent", "Mozilla/5.0")
                connectTimeout = 5000
                readTimeout = 5000
            }
            val response = connection.inputStream.bufferedReader().readText()
            // Simple parser for Google Translate free API response
            val result = StringBuilder()
            var i = 2
            while (i < response.length) {
                if (response[i] == '"') {
                    i++
                    val start = i
                    while (i < response.length && response[i] != '"') {
                        if (response[i] == '\\') i++
                        i++
                    }
                    val part = response.substring(start, i.coerceAtMost(response.length))
                    if (part.isNotBlank()) {
                        result.append(part)
                        break
                    }
                }
                i++
            }
            result.toString().ifBlank { "Translation failed" }
        } catch (e: Exception) {
            "Translation error: ${e.message?.take(40)}"
        }
    }
}
