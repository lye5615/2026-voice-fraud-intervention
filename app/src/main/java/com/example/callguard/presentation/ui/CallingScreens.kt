package com.example.callguard.presentation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.callguard.domain.interfaces.RiskLevel
import com.example.callguard.domain.interfaces.RiskScore
import com.example.callguard.domain.service.CallService
import com.example.callguard.presentation.viewmodel.CallViewModel

// Premium Sleek Dark Theme Colors
val ThemeBackground = Color(0xFF0F0E17)
val ThemeCardBg = Color(0xFF1E1B29)
val PrimaryCyan = Color(0xFF00F2FE)
val PrimaryPurple = Color(0xFF4FACFE)
val AccentRed = Color(0xFFFF3B30)
val WarningAmber = Color(0xFFFFCC00)
val SafeGreen = Color(0xFF34C759)
val TextLight = Color(0xFFE2E1E6)
val TextDark = Color(0xFF9F9BA8)

@Composable
fun CallGuardApp(viewModel: CallViewModel) {
    val callState by viewModel.callState.collectAsState()
    val showOverlay by viewModel.showWarningOverlay.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ThemeBackground
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (callState) {
                CallService.CallState.IDLE -> DialScreen(viewModel)
                CallService.CallState.RINGING -> RingingScreen(viewModel)
                CallService.CallState.CONNECTED -> ActiveCallScreen(viewModel)
                CallService.CallState.DISCONNECTED -> DisconnectedScreen()
            }

            // Elegant scam alert overlay
            if (showOverlay) {
                InterventionOverlay(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialScreen(viewModel: CallViewModel) {
    val context = LocalContext.current
    var phoneNumber by remember { mutableStateOf("") }
    var presetPhraseInput by remember { mutableStateOf("") }

    val presetPhrases = listOf(
        "안녕하세요 최검사입니다.",
        "계좌에서 안전자금으로 송금하십시오.",
        "비밀번호와 주민등록번호를 입력하세요."
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // App Branding / Header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 40.dp)
        ) {
            Text(
                text = "CallGuard",
                style = TextStyle(
                    brush = Brush.horizontalGradient(listOf(PrimaryCyan, PrimaryPurple)),
                    fontSize = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Voice Phishing Detection & Intervention Research App",
                fontSize = 12.sp,
                color = TextDark,
                textAlign = TextAlign.Center
            )
        }

        // Dial Field
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("전화번호 입력", color = TextDark) },
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = PrimaryCyan,
                    unfocusedBorderColor = ThemeCardBg,
                    containerColor = ThemeCardBg
                ),
                textStyle = LocalTextStyle.current.copy(color = TextLight, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Big Gradient Call Button
            Button(
                onClick = { viewModel.initiateCall(context) },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.horizontalGradient(listOf(PrimaryCyan, PrimaryPurple))),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Call, contentDescription = "Call", tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "시험 통화 시작 (Loopback)",
                            color = Color.Black,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Test Simulation panel
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ThemeCardBg),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "AI 파이프라인 시뮬레이션 도구",
                    color = PrimaryCyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                presetPhrases.forEach { phrase ->
                    OutlinedButton(
                        onClick = { presetPhraseInput = phrase },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextLight)
                    ) {
                        Text(phrase, fontSize = 12.sp, maxLines = 1)
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = presetPhraseInput,
                        onValueChange = { presetPhraseInput = it },
                        placeholder = { Text("테스트 대사 입력", color = TextDark) },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = PrimaryPurple,
                            unfocusedBorderColor = ThemeBackground,
                            containerColor = ThemeBackground
                        ),
                        textStyle = LocalTextStyle.current.copy(color = TextLight, fontSize = 12.sp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            viewModel.initiateCall(context)
                            viewModel.simulateRemoteSpeech(presetPhraseInput)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                    ) {
                        Text("통화 후 전송", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun RingingScreen(viewModel: CallViewModel) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceAround
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("안전 연결 호출 중", color = TextDark, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text("시험 루프백 단말기", color = TextLight, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }

        // Pulsing Circle Avatar
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(200.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(PrimaryPurple.copy(alpha = 0.2f))
            )
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(PrimaryCyan, PrimaryPurple)))
            ) {
                Icon(
                    Icons.Filled.Call,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.Center)
                )
            }
        }

        // Decline Button
        FloatingActionButton(
            onClick = { viewModel.endCall() },
            containerColor = AccentRed,
            shape = CircleShape,
            modifier = Modifier.size(72.dp)
        ) {
            Icon(Icons.Filled.CallEnd, contentDescription = "Decline", tint = Color.White, modifier = Modifier.size(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveCallScreen(viewModel: CallViewModel) {
    val transcripts by viewModel.transcripts.collectAsState()
    val riskScore by viewModel.riskScore.collectAsState()
    val isLocalMuted by viewModel.isLocalMuted.collectAsState()
    val isRemoteMuted by viewModel.isRemoteMuted.collectAsState()

    var customSimText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top: Call State & Risk Status Panel
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
        ) {
            Text("루프백 수신 통화 연결됨", color = TextDark, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(12.dp))

            // dynamic scam meter
            RiskMeter(riskScore)
        }

        // Mid-Upper: Scrolling Transcript
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ThemeCardBg),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "실시간 통화 녹취록 (STT)",
                    color = PrimaryCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                if (transcripts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("목소리를 감지하면 여기에 실시간 텍스트가 표시됩니다.", color = TextDark, fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(transcripts) { (speaker, text) ->
                            val isLocal = speaker == "LOCAL"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isLocal) Arrangement.End else Arrangement.Start
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(
                                            RoundedCornerShape(
                                                topStart = 12.dp,
                                                topEnd = 12.dp,
                                                bottomStart = if (isLocal) 12.dp else 0.dp,
                                                bottomEnd = if (isLocal) 0.dp else 12.dp
                                            )
                                        )
                                        .background(if (isLocal) PrimaryPurple.copy(alpha = 0.2f) else ThemeBackground)
                                        .padding(10.dp)
                                        .widthIn(max = 240.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = if (isLocal) "나 (송신)" else "상대방 (수신)",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isLocal) PrimaryCyan else PrimaryPurple
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = text, color = TextLight, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Simulation control inline
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = customSimText,
                onValueChange = { customSimText = it },
                placeholder = { Text("대사 시뮬레이션", color = TextDark, fontSize = 11.sp) },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = PrimaryPurple,
                    unfocusedBorderColor = ThemeCardBg,
                    containerColor = ThemeCardBg
                ),
                textStyle = LocalTextStyle.current.copy(color = TextLight, fontSize = 12.sp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (customSimText.isNotBlank()) {
                        viewModel.simulateRemoteSpeech(customSimText)
                        customSimText = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
            ) {
                Text("송출", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Bottom Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mute mic button
            IconButton(
                onClick = { viewModel.toggleLocalMute() },
                modifier = Modifier
                    .size(54.dp)
                    .background(if (isLocalMuted) AccentRed.copy(alpha = 0.2f) else ThemeCardBg, CircleShape)
                    .border(1.dp, if (isLocalMuted) AccentRed else Color.Transparent, CircleShape)
            ) {
                Icon(
                    if (isLocalMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = "Mute Mic",
                    tint = if (isLocalMuted) AccentRed else TextLight
                )
            }

            // Crimson Hang up button
            IconButton(
                onClick = { viewModel.endCall() },
                modifier = Modifier
                    .size(68.dp)
                    .background(AccentRed, CircleShape)
            ) {
                Icon(Icons.Filled.CallEnd, contentDescription = "End Call", tint = Color.White, modifier = Modifier.size(28.dp))
            }

            // Remote audio mute (Intervention simulator)
            IconButton(
                onClick = { viewModel.toggleRemoteMute() },
                modifier = Modifier
                    .size(54.dp)
                    .background(if (isRemoteMuted) WarningAmber.copy(alpha = 0.2f) else ThemeCardBg, CircleShape)
                    .border(1.dp, if (isRemoteMuted) WarningAmber else Color.Transparent, CircleShape)
            ) {
                Icon(
                    if (isRemoteMuted) Icons.Filled.VolumeMute else Icons.Filled.VolumeUp,
                    contentDescription = "Mute Remote (Intervention)",
                    tint = if (isRemoteMuted) WarningAmber else TextLight
                )
            }
        }
    }
}

@Composable
fun RiskMeter(riskScore: RiskScore) {
    val levelColor = when (riskScore.level) {
        RiskLevel.SAFE -> SafeGreen
        RiskLevel.SUSPICIOUS -> WarningAmber
        RiskLevel.SCAM -> AccentRed
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "borderAlpha"
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ThemeCardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.5.dp,
                if (riskScore.level == RiskLevel.SCAM) levelColor.copy(alpha = borderAlpha) else Color.Transparent,
                RoundedCornerShape(20.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("실시간 대화 피싱 위험지수", fontSize = 11.sp, color = TextDark)
                Text(
                    text = "${(riskScore.probability * 100).toInt()}%",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = levelColor
                )
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            LinearProgressIndicator(
                progress = riskScore.probability,
                color = levelColor,
                trackColor = ThemeBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            
            Spacer(modifier = Modifier.height(12.dp))

            // Keywords match tags
            if (riskScore.matchedKeywords.isNotEmpty()) {
                Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
                    Text("검출된 의심 단어:", fontSize = 10.sp, color = TextDark)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        riskScore.matchedKeywords.forEach { keyword ->
                            Box(
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(levelColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(keyword, color = levelColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                Text("통화 데이터 분석 중...", fontSize = 10.sp, color = TextDark)
            }
        }
    }
}

@Composable
fun InterventionOverlay(viewModel: CallViewModel) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xD22E0D11)), // Dark red glassmorphism style
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .border(2.dp, AccentRed, RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "⚠️ 보이스피싱 강력 위험 경고!",
                color = AccentRed,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Text(
                "통화 중 금융 사기 패턴이 감지되었습니다.\n사기 예방을 위해 상대방 음성이 3초간 차단되었으며, 피해를 보지 않기 위해 전화를 바로 끊으십시오.",
                color = TextLight,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            Button(
                onClick = { viewModel.endCall() },
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("통화 강제 끊기", color = Color.White, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            OutlinedButton(
                onClick = { viewModel.dismissOverlay() },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextLight),
                border = BorderStroke(1.dp, TextDark),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("경고 무시 및 통화 계속", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun DisconnectedScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("통화가 정상 종료되었습니다.", color = TextLight, fontSize = 16.sp)
    }
}
