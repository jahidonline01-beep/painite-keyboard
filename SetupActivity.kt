package com.painite.keyboard.ui.setup

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.painite.keyboard.ui.settings.SettingsActivity
import com.painite.keyboard.ui.theme.PremiumPIcon

class SetupActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestAudioPermissionIfNeeded()
        setContent {
            SetupScreen(
                onEnableKeyboard = {
                    val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                    startActivity(intent)
                },
                onChooseKeyboard = {
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showInputMethodPicker()
                },
                onOpenSettings = {
                    startActivity(Intent(this, SettingsActivity::class.java))
                },
                isKeyboardEnabled = {
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.enabledInputMethodList.any { it.packageName == packageName }
                }
            )
        }
    }

    private fun requestAudioPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1001)
        }
    }
}

@Composable
fun SetupScreen(
    onEnableKeyboard: () -> Unit,
    onChooseKeyboard: () -> Unit,
    onOpenSettings: () -> Unit,
    isKeyboardEnabled: () -> Boolean
) {
    val bg = Color(0xFF0A0018)
    val accent = Color(0xFFAB47BC)
    val accent2 = Color(0xFF00E5FF)
    val gradient = Brush.linearGradient(listOf(Color(0xFF7B2FBE), Color(0xFF00E5FF)))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        // Background glow blobs
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset((-80).dp, (-120).dp)
                .background(
                    Brush.radialGradient(listOf(Color(0xFF7B2FBE).copy(alpha = 0.18f), Color.Transparent)),
                    CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(200.dp)
                .offset(100.dp, 150.dp)
                .background(
                    Brush.radialGradient(listOf(Color(0xFF00E5FF).copy(alpha = 0.12f), Color.Transparent)),
                    CircleShape
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(Modifier.height(40.dp))

            // ── Premium P logo ────────────────────────────────────────────────
            PremiumPIcon(size = 96.dp, cornerRadius = 26.dp, fontSize = 56.sp)

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Painite",
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Text(
                    "Premium Keyboard",
                    color = accent.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 4.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            // Step 1
            SetupStepCard(
                step = 1,
                title = "Enable Painite",
                description = "Go to keyboard settings and enable Painite keyboard",
                icon = Icons.Default.Settings,
                accentColor = accent,
                gradientBrush = gradient,
                buttonText = "Enable Keyboard",
                onClick = onEnableKeyboard
            )

            // Step 2
            SetupStepCard(
                step = 2,
                title = "Choose Painite",
                description = "Select Painite as your active input method",
                icon = Icons.Default.Keyboard,
                accentColor = accent2,
                gradientBrush = Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFF0077FF))),
                buttonText = "Choose Keyboard",
                onClick = onChooseKeyboard
            )

            // Settings button
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open Settings", fontWeight = FontWeight.SemiBold)
            }

            // Feature badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("10 Themes", "Bangla/EN", "Voice", "Clipboard").forEach { feat ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(0.5.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(feat, color = accent.copy(alpha = 0.8f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SetupStepCard(
    step: Int,
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    gradientBrush: Brush,
    buttonText: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(gradientBrush),
                contentAlignment = Alignment.Center
            ) {
                Text("$step", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
            Column {
                Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(description, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
        }
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accentColor)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(buttonText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}
