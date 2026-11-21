package com.example.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.*
import com.example.ui.settings.*
import com.example.ui.theme.*

@Composable
fun FilesBlockContainer(code: String) {
    val lines = remember(code) { code.split("\n").filter { it.trim().isNotEmpty() } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (lines.isEmpty()) {
            Surface(
                color = AppSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, AppStroke)
            ) {
                Text(
                    text = "Directorio vacío o inaccesible.",
                    style = BodyStyle.copy(color = AppTextMuted),
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = AppSurface.copy(alpha = 0.95f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, AppStroke),
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Header inside card
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = AppCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Archivos encontrados",
                            style = TitleStyle.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppText
                            )
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "${lines.size} ítems",
                            style = MetaStyle.copy(
                                fontSize = 11.sp,
                                color = AppTextMuted
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // List of files
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        lines.forEachIndexed { index, raw ->
                            val ansiStripped = raw.replace("\u001B\\[[;0-9]*[a-zA-Z]".toRegex(), "")
                            val cleaned = ansiStripped
                                .trim()
                                .replace("""^\d+\.\s*""".toRegex(), "")
                                .replace("📁", "")
                                .replace("📄", "")
                                .replace("⚙️", "")
                                .trim()

                            val isDir = raw.contains("📁") || cleaned.endsWith("/") || raw.contains("<DIR>")
                            val isZip = cleaned.endsWith(".zip", ignoreCase = true) || 
                                        cleaned.endsWith(".tar", ignoreCase = true) || 
                                        cleaned.endsWith(".gz", ignoreCase = true) || 
                                        cleaned.endsWith(".rar", ignoreCase = true) || 
                                        cleaned.endsWith(".7z", ignoreCase = true)
                            val isImage = cleaned.endsWith(".png", ignoreCase = true) || 
                                          cleaned.endsWith(".jpg", ignoreCase = true) || 
                                          cleaned.endsWith(".jpeg", ignoreCase = true) || 
                                          cleaned.endsWith(".webp", ignoreCase = true) || 
                                          cleaned.endsWith(".gif", ignoreCase = true) || 
                                          cleaned.endsWith(".svg", ignoreCase = true)
                            val isVideo = cleaned.endsWith(".mp4", ignoreCase = true) || 
                                          cleaned.endsWith(".mkv", ignoreCase = true) || 
                                          cleaned.endsWith(".avi", ignoreCase = true) || 
                                          cleaned.endsWith(".mov", ignoreCase = true) || 
                                          cleaned.endsWith(".webm", ignoreCase = true)
                            val isEnv = cleaned.contains(".env", ignoreCase = true) || cleaned.endsWith(".properties")

                            val rowBg = if (index % 2 == 0) AppSurface2.copy(alpha = 0.4f) else Color.Transparent

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(rowBg, shape = RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Item number (fixed width)
                                Text(
                                    text = String.format("%02d", index + 1),
                                    style = MetaStyle.copy(
                                        color = AppTextMuted.copy(alpha = 0.5f),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    ),
                                    modifier = Modifier.width(28.dp)
                                )

                                // 2. Icon according to file type
                                val iconColor = when {
                                    isDir -> AppCyan
                                    isZip -> AppPink
                                    isImage -> AppGreen
                                    isVideo -> Color(0xFFFFB74D) // Soft warm orange for videos
                                    isEnv -> AppAccentSoft
                                    else -> AppTextMuted
                                }
                                val iconVector = when {
                                    isDir -> Icons.Default.Folder
                                    isZip -> Icons.Default.FolderZip
                                    isImage -> Icons.Default.Image
                                    isVideo -> Icons.Default.Movie
                                    isEnv -> Icons.Default.Key
                                    else -> Icons.Default.Description
                                }

                                Icon(
                                    imageVector = iconVector,
                                    contentDescription = null,
                                    tint = iconColor,
                                    modifier = Modifier.size(18.dp)
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                // 3. Filename
                                Text(
                                    text = cleaned,
                                    style = BodyStyle.copy(
                                        fontSize = 13.5.sp,
                                        color = AppText,
                                        fontFamily = UiSans
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
