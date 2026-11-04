package com.shreenathji.sales.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shreenathji.sales.data.remote.dto.AppUpdateDto
import com.shreenathji.sales.ui.theme.*
import com.shreenathji.sales.util.DownloadState

@Composable
fun AppUpdateDialog(
    updateDto: AppUpdateDto,
    downloadState: DownloadState,
    onStartDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    val isMandatory = updateDto.is_mandatory
    val isDownloading = downloadState is DownloadState.Progress

    Dialog(
        onDismissRequest = {
            if (!isMandatory && !isDownloading) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !isMandatory && !isDownloading,
            dismissOnClickOutside = !isMandatory && !isDownloading
        )
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header Icon and Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isMandatory) RoseDestructiveBg else IndigoAccentBg
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = "Update Available",
                            tint = if (isMandatory) RoseDestructiveText else IndigoAccentText,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = if (isMandatory) "Mandatory Update" else "Update Available",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            text = "Version v${updateDto.version_name}",
                            style = MaterialTheme.typography.bodySmall.copy(color = Slate500)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Changelog / Description Box
                if (!updateDto.changelog.isNullOrBlank()) {
                    Text(
                        text = "What's New",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Slate700
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Slate100)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = updateDto.changelog,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Slate800,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Download Progress / Error States
                when (downloadState) {
                    is DownloadState.Idle -> {
                        if (isMandatory) {
                            Text(
                                text = "This update contains essential improvements required to continue using the application.",
                                style = MaterialTheme.typography.bodySmall.copy(color = Slate600)
                            )
                        } else {
                            Text(
                                text = "A newer version of Shreenathji Sales is ready for download.",
                                style = MaterialTheme.typography.bodySmall.copy(color = Slate600)
                            )
                        }
                    }

                    is DownloadState.Progress -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Downloading...",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Medium,
                                        color = Slate700
                                    )
                                )
                                Text(
                                    text = "${downloadState.percentage}%",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = BrandOrange
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { downloadState.percentage / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = BrandOrange,
                                trackColor = Slate200
                            )
                        }
                    }

                    is DownloadState.Success -> {
                        Text(
                            text = "Download completed successfully! Click install to update.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = EmeraldSuccessText,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }

                    is DownloadState.Error -> {
                        Text(
                            text = "Download failed: ${downloadState.message}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = RoseDestructiveText,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isMandatory && !isDownloading) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = "Later",
                                color = Slate600,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    when (downloadState) {
                        is DownloadState.Idle, is DownloadState.Error -> {
                            Button(
                                onClick = onStartDownload,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BrandOrange,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (downloadState is DownloadState.Error) "Retry Download" else "Update Now",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        is DownloadState.Progress -> {
                            Button(
                                onClick = {},
                                enabled = false,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Downloading...")
                            }
                        }

                        is DownloadState.Success -> {
                            Button(
                                onClick = onInstall,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldSuccessText,
                                    contentColor = Color.White
                                )
                            ) {
                                Text("Install Update", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
