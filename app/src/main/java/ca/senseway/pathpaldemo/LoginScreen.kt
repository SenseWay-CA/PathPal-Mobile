@file:OptIn(ExperimentalMaterial3Api::class)
package ca.senseway.pathpaldemo

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoginScreen(onLogin: (String, String) -> Unit, loginError: String?) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val passwordFocus = remember { FocusRequester() }

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val isEnabled = username.isNotBlank() && password.isNotBlank()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgDeep, BgNavy, BgDeep)))
    ) {
        // Decorative glow orbs
        Box(
            Modifier
                .size(400.dp)
                .offset((-120).dp, (-150).dp)
                .background(
                    Brush.radialGradient(listOf(VioletAccent.copy(alpha = 0.18f), Color.Transparent)),
                    CircleShape
                )
        )
        Box(
            Modifier
                .size(300.dp)
                .align(Alignment.BottomEnd)
                .offset(100.dp, 80.dp)
                .background(
                    Brush.radialGradient(listOf(ElectricBlue.copy(alpha = 0.14f), Color.Transparent)),
                    CircleShape
                )
        )
        Box(
            Modifier
                .size(200.dp)
                .align(Alignment.BottomStart)
                .offset((-60).dp, 40.dp)
                .background(
                    Brush.radialGradient(listOf(VioletAccent.copy(alpha = 0.08f), Color.Transparent)),
                    CircleShape
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(80.dp))

            // Logo
            Box(
                Modifier
                    .size(84.dp)
                    .background(
                        Brush.linearGradient(listOf(ElectricBlue, VioletAccent)),
                        RoundedCornerShape(26.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("S", fontSize = 42.sp, fontWeight = FontWeight.Black, color = Color.White)
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "PathPal",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = TextWhite,
                letterSpacing = (-0.5).sp
            )
            Text(
                "by Senseway",
                fontSize = 15.sp,
                color = TextMuted,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(48.dp))

            // Login Card
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = PanelPurple,
                border = BorderStroke(1.dp, CardBorder),
                shadowElevation = 24.dp
            ) {
                Column(
                    Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "Welcome back",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    Text(
                        "Sign in to continue monitoring",
                        fontSize = 13.sp,
                        color = TextMuted
                    )

                    Spacer(Modifier.height(4.dp))

                    // Username field
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, null, tint = ElectricBlue)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricBlue,
                            unfocusedBorderColor = CardBorder,
                            focusedLabelColor = ElectricBlue,
                            unfocusedLabelColor = TextMuted,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextGray,
                            cursorColor = ElectricBlue,
                            focusedContainerColor = PanelPurple2,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
                        singleLine = true
                    )

                    // Password field
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, null, tint = ElectricBlue)
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    null,
                                    tint = TextMuted
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(passwordFocus),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricBlue,
                            unfocusedBorderColor = CardBorder,
                            focusedLabelColor = ElectricBlue,
                            unfocusedLabelColor = TextMuted,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextGray,
                            cursorColor = ElectricBlue,
                            focusedContainerColor = PanelPurple2,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            if (isEnabled) onLogin(username, password)
                        }),
                        singleLine = true
                    )

                    // Error message
                    AnimatedVisibility(
                        visible = loginError != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        loginError?.let { err ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(RedAlert.copy(alpha = 0.12f))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    null,
                                    tint = RedAlert,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(err, color = RedAlert, fontSize = 13.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // Sign In button (gradient)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isEnabled)
                                    Brush.horizontalGradient(listOf(ElectricBlue, VioletAccent))
                                else
                                    Brush.horizontalGradient(
                                        listOf(ElectricBlue.copy(alpha = 0.35f), VioletAccent.copy(alpha = 0.35f))
                                    )
                            )
                            .clickable(enabled = isEnabled) {
                                focusManager.clearFocus()
                                onLogin(username, password)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Sign In",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // Divider
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(Modifier.weight(1f), color = CardBorder)
                Text(
                    "  Don't have an account?  ",
                    color = TextMuted,
                    fontSize = 13.sp
                )
                HorizontalDivider(Modifier.weight(1f), color = CardBorder)
            }

            Spacer(Modifier.height(20.dp))

            // Register Online button — gradient border
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.horizontalGradient(listOf(ElectricBlue, VioletAccent))
                    )
                    .padding(1.5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(13.dp))
                        .background(BgDeep)
                        .clickable {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://senseway.ca/register")
                            )
                            context.startActivity(intent)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Language,
                            null,
                            tint = ElectricBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Register Online",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextWhite
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                "Registration is completed at senseway.ca",
                fontSize = 12.sp,
                color = TextMuted
            )

            Spacer(Modifier.height(48.dp))
        }
    }
}
