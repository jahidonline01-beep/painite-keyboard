package com.painite.keyboard.ui.keyboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.painite.keyboard.ui.theme.KeyboardTheme

private val EMOJI_CATEGORIES = mapOf(
    "Smileys" to listOf("😀","😃","😄","😁","😆","😅","🤣","😂","🙂","🙃","😉","😊","😇","🥰","😍","🤩","😘","😗","😚","😙","😋","😛","😜","😝","🤑","🤗","🤭","🤫","🤔","🤐","🤨","😐","😑","😶","😏","😒","🙄","😬","🤥","😌","😔","😪","🤤","😴","😷","🤒","🤕","🤢","🤮","🤧","🥵","🥶","😵","🤯","🤠","🥳","😎","🤓","🧐","😕","😟","🙁","☹️","😮","😯","😲","😳","🥺","😦","😧","😨","😰","😥","😢","😭","😱","😖","😣","😞","😓","😩","😫","🥱","😤","😡","😠","🤬","😈","👿","💀","☠️","💩","🤡","👹","👺","👻","👽","👾","🤖"),
    "Hands" to listOf("👋","🤚","🖐️","✋","🖖","👌","🤌","🤏","✌️","🤞","🤟","🤘","🤙","👈","👉","👆","🖕","👇","☝️","👍","👎","✊","👊","🤛","🤜","👏","🙌","👐","🤲","🤝","🙏","✍️","💅","🤳","💪","🦾","🦿","🦵","🦶","👂","🦻","👃","🫀","🫁","🧠","🦷","🦴","👀","👁️","👅","👄"),
    "Animals" to listOf("🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐻‍❄️","🐨","🐯","🦁","🐮","🐷","🐸","🐵","🙈","🙉","🙊","🐒","🦆","🦅","🦉","🦇","🐺","🐗","🐴","🦄","🐝","🐛","🦋","🐌","🐞","🐜","🦟","🦗","🦂","🐢","🐍","🦎","🦕","🦖","🐙","🦑","🦐","🦞","🦀","🐡","🐠","🐟","🐬","🐳","🐋","🦈","🐊","🐅","🐆","🦓","🦍","🦧","🦣","🐘","🦛","🦏","🐪","🐫"),
    "Food" to listOf("🍎","🍊","🍋","🍇","🍓","🍒","🍑","🥭","🍍","🥝","🍅","🥑","🥕","🌽","🌶️","🧄","🧅","🥔","🍠","🌰","🥜","🍞","🥐","🥖","🫓","🥨","🧀","🥚","🍳","🧈","🥞","🧇","🥓","🥩","🍗","🍖","🦴","🌭","🍔","🍟","🍕","🫔","🌮","🌯","🥙","🧆","🥚","🍜","🍝","🍛","🍲","🍱","🍣","🍤","🍙","🍚","🍘","🍥","🥮","🍡","🧁","🎂","🍰","🍮","🍭","🍬","🍫","🍿","🍩","🍪","🌰","🥧","🍦","🍧","🍨"),
    "Objects" to listOf("⌚","📱","💻","⌨️","🖥️","🖨️","🖱️","💾","💿","📀","📷","📸","📹","🎥","📽️","🎞️","📞","☎️","📟","📠","📺","📻","🧭","⏱️","⏲️","⏰","🕰️","⌛","⏳","📡","🔋","🔌","💡","🔦","🕯️","🪔","🧯","🛢️","💵","💴","💶","💷","💸","💳","🪙","💰","💹","📈","📉","📊","📋","📌","📍","🗺️","🗓️","📅","📆","🗒️","📇","🗃️","📂","📁","🗂️","📰","🗞️","📓","📔","📒","📕","📗","📘","📙","📚","📖","🔖","🏷️","💰","✉️","📧","📨","📩"),
    "Hearts" to listOf("❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💔","❣️","💕","💞","💓","💗","💖","💘","💝","💟","❤️‍🔥","❤️‍🩹","💯","✨","⭐","🌟","💫","⚡","🔥","🌈","☀️","🌤️","⛅","🌦️","🌧️","❄️","💧","🌊","🎆","🎇","🎉","🎊","🎋","🎍","🎎","🎏","🎐","🎑","🎃","🎄","🎆","🎇","🎈","🎀","🎁","🎗️","🎟️","🎫")
)

@Composable
fun EmojiPanel(
    theme: KeyboardTheme,
    onEmojiSelected: (String) -> Unit,
    onClose: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf("Smileys") }
    val emojis = EMOJI_CATEGORIES[selectedCategory] ?: emptyList()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp, max = 280.dp)
            .background(theme.background)
    ) {
        // Category tabs + close
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.rowbarBg)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                EMOJI_CATEGORIES.keys.forEach { cat ->
                    val isSelected = cat == selectedCategory
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) theme.accentColor.copy(alpha = 0.3f) else theme.keyBackground)
                            .clickable { selectedCategory = cat }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = cat.take(4),
                            fontSize = 10.sp,
                            color = if (isSelected) theme.accentColor else theme.keyTextColor.copy(alpha = 0.6f),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = theme.keyTextColor, modifier = Modifier.size(16.dp))
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(40.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(emojis) { emoji ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onEmojiSelected(emoji) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = emoji, fontSize = 22.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
