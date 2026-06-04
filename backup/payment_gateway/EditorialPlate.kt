package com.ballade.hwaran.ui.screens.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em

data class AtmosphereData(
    val id: String,
    val num: String,
    val title: String,
    val label: String,
    val desc: String,
    val price: String,
    val align: String
)

@Composable
fun EditorialPlate(
    data: AtmosphereData,
    scrollValue: Float,
    onHover: (String?) -> Unit,
    onSelect: (AtmosphereData) -> Unit
) {
    val isRight = data.align == "right"
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 128.dp)
    ) {
        Text(
            text = data.num,
            fontSize = 120.sp,
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Black,
            color = Color.White.copy(alpha = 0.03f),
            modifier = Modifier
                .align(if (isRight) Alignment.TopStart else Alignment.TopEnd)
                .offset(x = if (isRight) (-16).dp else 16.dp, y = (-48).dp)
        )
        
        val shape = if (isRight) RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp) else RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
        
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .align(if (isRight) Alignment.CenterEnd else Alignment.CenterStart)
                .clip(shape)
                .background(Brush.horizontalGradient(
                    colors = if (isRight) listOf(Color(0xFF111111).copy(alpha = 0.9f), Color(0xFF0A0A0A).copy(alpha = 0.95f)) 
                             else listOf(Color(0xFF0A0A0A).copy(alpha = 0.95f), Color(0xFF111111).copy(alpha = 0.9f))
                ))
                .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onSelect(data) }
                )
                .padding(horizontal = 40.dp, vertical = 48.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = if (isRight) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isRight) {
                        Box(modifier = Modifier.width(32.dp).height(1.dp).background(Color.White.copy(alpha = 0.2f)))
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    Text(
                        text = data.label.uppercase(),
                        fontSize = 11.sp,
                        letterSpacing = 0.3.em,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                    if (isRight) {
                        Spacer(modifier = Modifier.width(16.dp))
                        Box(modifier = Modifier.width(32.dp).height(1.dp).background(Color.White.copy(alpha = 0.2f)))
                    }
                }
                
                Text(
                    text = data.title,
                    fontSize = 36.sp,
                    fontFamily = FontFamily.Serif,
                    letterSpacing = 0.05.em,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = if (isRight) TextAlign.End else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                )
                
                Text(
                    text = data.desc,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Light,
                    color = Color.White.copy(alpha = 0.5f),
                    lineHeight = 20.sp,
                    textAlign = if (isRight) TextAlign.End else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(0.85f).align(if (isRight) Alignment.End else Alignment.Start)
                )
                
                Spacer(modifier = Modifier.height(40.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isRight) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isRight) {
                        Text(text = data.price, fontSize = 18.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, color = Color.White.copy(alpha = 0.7f))
                        Spacer(modifier = Modifier.width(24.dp))
                    }
                    
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.White.copy(alpha = 0.05f), androidx.compose.foundation.shape.CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.2f), androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Select",
                            tint = Color.White,
                            modifier = Modifier.graphicsLayer { rotationZ = if (isRight) 180f else 0f }
                        )
                    }
                    
                    if (isRight) {
                        Spacer(modifier = Modifier.width(24.dp))
                        Text(text = data.price, fontSize = 18.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}