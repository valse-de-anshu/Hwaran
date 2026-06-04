package com.ballade.hwaran.ui.screens.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em

val ATMOSPHERES = listOf(
    AtmosphereData("lifetime", "01", "Omnipresence", "THE COMPLETE SANCTUARY", "Unlimited access to all your media. A seamless, boundless local environment with every feature unlocked.", "FREE", "left"),
    AtmosphereData("ads", "02", "Isolation", "ABSOLUTE SILENCE", "Remove all ads. Enjoy your manga, anime, and audio without any interruptions.", "FREE", "right"),
    AtmosphereData("themes", "03", "Morphosis", "AESTHETIC OVERWRITE", "Unlock custom themes. Personalize your sanctuary with beautiful colors and interface layers.", "FREE", "left")
)

@Composable
fun ArchiveScreen(onNavigateBack: () -> Unit = {}) {
    val scrollState = rememberScrollState()
    var activeTier by remember { mutableStateOf<String?>(null) }
    var selectedPlan by remember { mutableStateOf<AtmosphereData?>(null) }
    var unlocked by remember { mutableStateOf<AtmosphereData?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF020202))
    ) {
        if (unlocked != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EtherealBackground(scrollState = scrollState, activeHover = unlocked!!.id)
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(Color.White.copy(alpha = 0.05f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, tint = Color.White, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    Text("PROTOCOL ACCEPTED", fontSize = 10.sp, letterSpacing = 0.4.em, color = Color.White.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(unlocked!!.title, fontSize = 32.sp, fontFamily = FontFamily.Serif, color = Color.White)
                    Spacer(modifier = Modifier.height(40.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                            .clickable { 
                                unlocked = null
                                onNavigateBack() 
                            }
                            .padding(horizontal = 40.dp, vertical = 16.dp)
                    ) {
                        Text("ENTER SANCTUARY", fontSize = 11.sp, letterSpacing = 0.2.em, color = Color.White)
                    }
                }
            }
        } else {
            EtherealBackground(scrollState = scrollState, activeHover = activeTier)
            
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 180.dp, start = 40.dp, end = 40.dp, bottom = 100.dp)
                    ) {
                        Column {
                            Box(modifier = Modifier.width(48.dp).height(1.dp).background(Color.White.copy(alpha = 0.3f)))
                            Spacer(modifier = Modifier.height(32.dp))
                            Text(
                                text = "The\nSanctuary",
                                fontSize = 56.sp,
                                fontFamily = FontFamily.Serif,
                                lineHeight = 64.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "PERSONAL MEDIA SPACE",
                                fontSize = 11.sp,
                                letterSpacing = 0.3.em,
                                color = Color.White.copy(alpha = 0.4f)
                            )
                        }
                    }

                    ATMOSPHERES.forEach { frag ->
                        EditorialPlate(
                            data = frag,
                            scrollValue = scrollState.value.toFloat(),
                            onHover = { activeTier = it },
                            onSelect = { selectedPlan = it }
                        )
                    }

                    Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp, vertical = 128.dp)
                        .border(1.dp, Color.Transparent)
                        .background(Color.Transparent)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
                    Spacer(modifier = Modifier.height(48.dp))
                    Box(modifier = Modifier.width(32.dp).height(1.dp).background(Color.White.copy(alpha = 0.2f)))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Sanctuary Features", fontSize = 20.sp, fontFamily = FontFamily.Serif, color = Color.White.copy(alpha = 0.8f), letterSpacing = 0.05.em)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("DETAILED FEATURE LIST", fontSize = 10.sp, letterSpacing = 0.3.em, color = Color.White.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(40.dp))
                    
                    val details = listOf(
                        Triple("01", "Omnipresence", "Unlimited access to all your media. A seamless, boundless local environment with every feature unlocked."),
                        Triple("02", "Isolation", "Remove all ads. Enjoy your manga, anime, and audio without any interruptions."),
                        Triple("03", "Morphosis", "Unlock custom themes. Personalize your sanctuary with beautiful colors and interface layers.")
                    )
                    
                    details.forEach { (num, title, desc) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(num, fontSize = 11.sp, letterSpacing = 0.2.em, color = Color.White.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(title.uppercase(), fontSize = 11.sp, letterSpacing = 0.2.em, color = Color.White.copy(alpha = 0.6f))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(desc, fontSize = 14.sp, fontFamily = FontFamily.Serif, color = Color.White.copy(alpha = 0.5f), lineHeight = 24.sp)
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.02f))
                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                            .padding(32.dp)
                    ) {
                        Box(modifier = Modifier.align(Alignment.CenterStart).width(4.dp).fillMaxHeight().offset(x = (-32).dp).background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.4f), Color.Transparent))))
                        Column {
                            Text("A MESSAGE", fontSize = 9.sp, letterSpacing = 0.4.em, color = Color.White.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("\"You are not just subscribing to an app; you are building your personal sanctuary. Everything stays offline and private. Thank you for supporting our work.\"", fontSize = 14.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, color = Color.White.copy(alpha = 0.7f), lineHeight = 24.sp)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(48.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.05f)))
                    Spacer(modifier = Modifier.height(32.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row {
                            Text("TERMS OF SERVICE", fontSize = 9.sp, letterSpacing = 0.2.em, color = Color.White.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.width(24.dp))
                            Text("PRIVACY POLICY", fontSize = 9.sp, letterSpacing = 0.2.em, color = Color.White.copy(alpha = 0.3f))
                        }
                        Text("© 2026 SANCTUARY", fontSize = 9.sp, letterSpacing = 0.2.em, color = Color.White.copy(alpha = 0.2f))
                    }
                }
                }
                
                Box(
                    modifier = Modifier
                        .padding(top = 48.dp, start = 40.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                        .clickable { onNavigateBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("<", color = Color.White, fontSize = 16.sp)
                }
            }

            PaymentSheet(
                selectedPlan = selectedPlan,
                onClose = { selectedPlan = null },
                onConfirm = {
                    unlocked = selectedPlan
                    selectedPlan = null
                }
            )
        }
    }
}