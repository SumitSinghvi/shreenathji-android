package com.shreenathji.sales.ui.screens.party

import android.Manifest
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shreenathji.sales.ui.theme.*

@Composable
fun StoreChatInputBar(
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    isRecording: Boolean,
    isUploadingVoice: Boolean,
    hasMicPermission: () -> Boolean,
    permissionLauncher: ManagedActivityResultLauncher<String, Boolean>,
    onStartRecording: () -> Boolean,
    onStopRecordingAndSend: () -> Unit,
    onSendMessage: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (isRecording) {
            Text(
                text = "Recording… release to send",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = RoseDestructive,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        } else if (isUploadingVoice) {
            Text(
                text = "Uploading voice note…",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Slate600,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = onMessageTextChange,
                placeholder = {
                    Text(
                        text = "Type update…",
                        fontSize = 11.sp,
                        color = Slate400
                    )
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Slate900,
                    unfocusedBorderColor = Slate300,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Slate50
                ),
                maxLines = 3,
                enabled = !isRecording && !isUploadingVoice
            )

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = when {
                            isRecording -> RoseDestructive
                            isUploadingVoice -> Slate300
                            else -> Slate100
                        },
                        shape = CircleShape
                    )
                    .pointerInput(isUploadingVoice) {
                        detectTapGestures(
                            onPress = {
                                if (isUploadingVoice) return@detectTapGestures
                                if (!hasMicPermission()) {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    return@detectTapGestures
                                }
                                val started = onStartRecording()
                                tryAwaitRelease()
                                if (started) onStopRecordingAndSend()
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Hold to record voice",
                    tint = if (isRecording) Color.White else Slate700,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = {
                    if (messageText.isNotBlank()) {
                        onSendMessage(messageText.trim())
                    }
                },
                enabled = messageText.isNotBlank() && !isRecording && !isUploadingVoice,
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = if (messageText.isNotBlank()) Slate900 else Slate200,
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = if (messageText.isNotBlank()) Color.White else Slate400,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
