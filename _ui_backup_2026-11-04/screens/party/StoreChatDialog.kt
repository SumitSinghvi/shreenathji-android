package com.shreenathji.sales.ui.screens.party

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.shreenathji.sales.data.local.entities.DailyVisitPlanEntity
import com.shreenathji.sales.ui.theme.*
import java.io.File

@Composable
fun StoreChatDialog(
    plan: DailyVisitPlanEntity,
    onDismiss: () -> Unit,
    onSendMessage: (String) -> Unit,
    onSendVoiceFile: (File, onResult: (Boolean) -> Unit) -> Unit = { _, cb -> cb(false) }
) {
    val context = LocalContext.current
    var messageText by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    var isUploadingVoice by remember { mutableStateOf(false) }
    var playingUrl by remember { mutableStateOf<String?>(null) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordFile by remember { mutableStateOf<File?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    val chatMessages = remember(plan.instructions, plan.remarks, plan.visitedAt) {
        parseStoreChatHistory(plan.instructions, plan.remarks, plan.salesRepName, plan.visitedAt)
    }
    val listState = rememberLazyListState()

    fun stopPlayback() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        playingUrl = null
    }

    fun playVoice(url: String) {
        if (playingUrl == url) {
            stopPlayback()
            return
        }
        stopPlayback()
        try {
            val player = MediaPlayer().apply {
                setDataSource(url)
                setOnCompletionListener {
                    playingUrl = null
                    release()
                    mediaPlayer = null
                }
                setOnErrorListener { _, _, _ ->
                    playingUrl = null
                    Toast.makeText(context, "Could not play voice note", Toast.LENGTH_SHORT).show()
                    true
                }
                prepareAsync()
                setOnPreparedListener {
                    start()
                    playingUrl = url
                }
            }
            mediaPlayer = player
        } catch (_: Exception) {
            Toast.makeText(context, "Could not play voice note", Toast.LENGTH_SHORT).show()
        }
    }

    fun startRecording(): Boolean {
        try {
            val file = File(context.cacheDir, "visit_voice_${System.currentTimeMillis()}.m4a")
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setOutputFile(file.absolutePath)
            mr.prepare()
            mr.start()
            recorder = mr
            recordFile = file
            isRecording = true
            return true
        } catch (e: Exception) {
            Toast.makeText(context, "Could not start recording", Toast.LENGTH_SHORT).show()
            isRecording = false
            return false
        }
    }

    fun stopRecordingAndSend() {
        val file = recordFile
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        isRecording = false
        recordFile = null

        if (file == null || !file.exists() || file.length() < 500) {
            file?.delete()
            Toast.makeText(context, "Hold longer to record a voice note", Toast.LENGTH_SHORT).show()
            return
        }

        isUploadingVoice = true
        onSendVoiceFile(file) { ok ->
            isUploadingVoice = false
            file.delete()
            if (!ok) {
                Toast.makeText(
                    context,
                    "Voice note could not upload. Check network and try again.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(context, "Hold the mic to record a voice note", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Microphone permission needed for voice notes", Toast.LENGTH_LONG).show()
        }
    }

    fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    DisposableEffect(Unit) {
        onDispose {
            stopPlayback()
            try {
                recorder?.release()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(22.dp),
            color = ShadcnWhite,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (plan.status == "VISITED") EmeraldSuccessBg else Slate100,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (plan.status == "VISITED") Icons.Default.CheckCircle else Icons.Default.Storefront,
                                    contentDescription = null,
                                    tint = if (plan.status == "VISITED") EmeraldSuccessText else BrandOrange,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = plan.partyName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate950,
                                maxLines = 1
                            )
                            Text(
                                text = plan.partyAddress ?: "Store notes & chat",
                                fontSize = 11.sp,
                                color = Slate500,
                                maxLines = 1
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Slate500
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(thickness = 1.dp, color = Slate200)
                Spacer(modifier = Modifier.height(8.dp))

                if (chatMessages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = Slate300
                            )
                            Text(
                                text = "No messages yet",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate600
                            )
                            Text(
                                text = "Type or hold mic for a voice note",
                                fontSize = 11.sp,
                                color = Slate400
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        items(chatMessages, key = { it.id }) { msg ->
                            StoreChatMessageBubble(
                                msg = msg,
                                playingUrl = playingUrl,
                                onPlayVoice = { url -> playVoice(url) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(thickness = 1.dp, color = Slate200)
                Spacer(modifier = Modifier.height(8.dp))

                StoreChatInputBar(
                    messageText = messageText,
                    onMessageTextChange = { messageText = it },
                    isRecording = isRecording,
                    isUploadingVoice = isUploadingVoice,
                    hasMicPermission = { hasMicPermission() },
                    permissionLauncher = permissionLauncher,
                    onStartRecording = { startRecording() },
                    onStopRecordingAndSend = { stopRecordingAndSend() },
                    onSendMessage = { text ->
                        onSendMessage(text)
                        messageText = ""
                    }
                )
            }
        }
    }
}
