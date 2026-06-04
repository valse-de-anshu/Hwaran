package com.ballade.hwaran.ui.screens.archive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em

@Composable
fun PaymentSheet(
    selectedPlan: AtmosphereData?,
    onClose: () -> Unit,
    onConfirm: () -> Unit
) {
    var method by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(selectedPlan) {
        if (selectedPlan == null) method = null
    }

    AnimatedVisibility(
        visible = selectedPlan != null,
        enter = fadeIn(tween(500)),
        exit = fadeOut(tween(500))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = selectedPlan != null,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 200f)
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 200f)
                )
            ) {
                if (selectedPlan != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(elevation = 40.dp, spotColor = Color.Black)
                            .clip(RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp))
                            .background(Color(0xFF030303).copy(alpha = 0.8f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {} 
                            )
                            .padding(horizontal = 32.dp, vertical = 24.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.2f), Color.Transparent))).align(Alignment.TopCenter))
                        Box(modifier = Modifier.align(Alignment.TopCenter).offset(y = (-20).dp).size(120.dp).blur(60.dp).background(Color.White.copy(alpha = 0.1f), CircleShape))
                        
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Box(modifier = Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(2.dp).background(Color.White.copy(alpha = 0.2f), CircleShape).padding(bottom = 40.dp))
                            Spacer(modifier = Modifier.height(40.dp))
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(16.dp).border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape), contentAlignment = Alignment.Center) {
                                    val infiniteTransition = rememberInfiniteTransition(label = "")
                                    val alpha by infiniteTransition.animateFloat(
                                        initialValue = 0.2f, targetValue = 0.8f,
                                        animationSpec = infiniteRepeatable(animation = tween(1000), repeatMode = RepeatMode.Reverse),
                                        label = ""
                                    )
                                    Box(modifier = Modifier.size(6.dp).background(Color.White.copy(alpha = alpha), CircleShape))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("SECURE LINK ESTABLISHED", fontSize = 9.sp, letterSpacing = 0.4.em, color = Color.White.copy(alpha = 0.5f))
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth().border(1.dp, Color.Transparent).padding(bottom = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                                Text(selectedPlan.title, fontSize = 32.sp, fontFamily = FontFamily.Serif, color = Color.White.copy(alpha = 0.9f), letterSpacing = 0.05.em)
                                Text(selectedPlan.price, fontSize = 20.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, color = Color.White.copy(alpha = 0.7f))
                            }
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.05f)))
                            Spacer(modifier = Modifier.height(40.dp))
                            
                            TenderOption("gpay", Icons.Default.PhoneAndroid, "Open Access", "No Biometric Auth Required", method) { method = it }
                            Spacer(modifier = Modifier.height(16.dp))
                            TenderOption("card", Icons.Default.CreditCard, "Free Paradigm", "No Encrypted Vault Integration", method) { method = it }
                            Spacer(modifier = Modifier.height(40.dp))
                            
                            ConfirmButton(method != null, onConfirm)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TenderOption(id: String, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, subtitle: String, selectedMethod: String?, onSelect: (String?) -> Unit) {
    val isSelected = selectedMethod == id
    val borderColor by animateColorAsState(if (isSelected) Color.White.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.05f), label = "")
    val iconColor by animateColorAsState(if (isSelected) Color.White else Color.White.copy(alpha = 0.3f), label = "")
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.02f))
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { onSelect(if (isSelected) null else id) }
    ) {
        if (isSelected) {
            Box(modifier = Modifier.align(Alignment.CenterStart).width(4.dp).matchParentSize().background(Color.White))
        }
        Row(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(20.dp))
            Column {
                Text(label.uppercase(), fontSize = 12.sp, letterSpacing = 0.2.em, color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(4.dp))
                Text(subtitle, fontSize = 9.sp, letterSpacing = 0.1.em, color = if (isSelected) Color.White.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.2f))
            }
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(if (isSelected) Color.White.copy(alpha = 0.1f) else Color.Transparent, CircleShape)
                    .border(1.dp, if (isSelected) Color.White else Color.White.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Box(modifier = Modifier.size(8.dp).background(Color.White, CircleShape))
                }
            }
        }
    }
}

@Composable
fun ConfirmButton(isActive: Boolean, onConfirm: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed && isActive) 0.98f else 1f, label = "")
    val haptic = LocalHapticFeedback.current
    
    val infiniteTransition = rememberInfiniteTransition(label = "")
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = -1000f,
        targetValue = 2000f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = ""
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(if (isActive) Color.White else Color.White.copy(alpha = 0.03f))
            .border(1.dp, if (isActive) Color.Transparent else Color.White.copy(alpha = 0.05f), CircleShape)
            .pointerInput(isActive) {
                if (isActive) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            tryAwaitRelease()
                            isPressed = false
                        },
                        onTap = { onConfirm() }
                    )
                }
            }
            .drawWithContent {
                drawContent()
                if (isActive) {
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.1f), Color.Transparent),
                            start = Offset(shimmerTranslate, 0f),
                            end = Offset(shimmerTranslate + 400f, size.height)
                        )
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Fingerprint, contentDescription = null, tint = if (isActive) Color.Black else Color.White.copy(alpha = 0.2f), modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = if (isActive) "CLAIM ACCESS" else "SELECT PARADIGM",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.25.em,
                color = if (isActive) Color.Black else Color.White.copy(alpha = 0.2f)
            )
        }
    }
}