package com.shreenathji.sales.ui.screens.party

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shreenathji.sales.ui.theme.*

fun Modifier.clickableNoIndication(onClick: () -> Unit): Modifier =
    this.then(
        Modifier.pointerInput(onClick) {
            detectTapGestures(onTap = { onClick() })
        }
    )

@Composable
fun StoreChatMessageBubble(
    msg: ChatMessageItem,
    playingUrl: String?,
    onPlayVoice: (String) -> Unit
) {
    val isManager = msg.isFromManager
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isManager) Alignment.Start else Alignment.End
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = msg.senderLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isManager) AmberWarningText else EmeraldSuccessText
            )
            if (!msg.time.isNullOrBlank()) {
                Text(
                    text = "• ${msg.time}",
                    fontSize = 9.sp,
                    color = Slate400
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (isManager) 4.dp else 14.dp,
                bottomEnd = if (isManager) 14.dp else 4.dp
            ),
            color = if (isManager) AmberWarningBg else EmeraldSuccessBg,
            border = BorderStroke(
                1.dp,
                if (isManager) AmberWarningBorder else EmeraldSuccessBorder
            ),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            if (msg.voiceUrl != null) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .clickableNoIndication { onPlayVoice(msg.voiceUrl) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (playingUrl == msg.voiceUrl) {
                            Icons.Default.Stop
                        } else {
                            Icons.Default.PlayArrow
                        },
                        contentDescription = "Play voice",
                        tint = if (isManager) AmberWarningText else EmeraldSuccessText,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (playingUrl == msg.voiceUrl) "Playing…" else "Voice message",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isManager) Color(0xFF78350F) else EmeraldSuccessText
                    )
                }
            } else {
                Text(
                    text = msg.text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = if (isManager) Color(0xFF78350F) else EmeraldSuccessText,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}
