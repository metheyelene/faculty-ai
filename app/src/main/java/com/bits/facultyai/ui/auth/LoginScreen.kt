package com.bits.facultyai.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bits.facultyai.ui.components.GlassSurface
import com.bits.facultyai.ui.components.GlassTextField
import com.bits.facultyai.ui.components.KineticButton
import com.bits.facultyai.ui.components.KineticGhostButton
import com.bits.facultyai.ui.components.KineticOutlinedButton
import com.bits.facultyai.ui.theme.KineticBorder
import com.bits.facultyai.ui.theme.KineticMotion
import com.bits.facultyai.ui.theme.KineticSpacing
import com.bits.facultyai.ui.theme.KineticType
import com.bits.facultyai.ui.theme.LocalKineticColors

/**
 * Acadora entry point: email + password, sign-up with verification, password
 * reset, Google sign-in, and guest mode. Built entirely from the existing
 * glass/kinetic primitives — no one-off styling.
 */
@Composable
fun LoginScreen(
    vm: AuthViewModel,
    onGuest: () -> Unit,
) {
    val k = LocalKineticColors.current
    val ui by vm.ui.collectAsStateWithLifecycle()
    val authState by vm.authState.collectAsStateWithLifecycle()

    val unconfigured = authState is com.bits.facultyai.data.auth.AuthState.Unconfigured

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(k.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = KineticSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))

            // Wordmark
            Text(
                text = "ACADORA",
                style = KineticType.display,
                color = k.foreground,
            )
            Text(
                text = "FACULTY AI",
                style = KineticType.labelBold.copy(letterSpacing = 4.sp),
                color = k.accent,
            )

            Spacer(Modifier.height(32.dp))

            GlassSurface(
                modifier = Modifier.widthIn(max = 460.dp),
                strength = com.bits.facultyai.ui.components.GlassStrength.REGULAR,
            ) {
                Column(Modifier.padding(KineticSpacing.lg)) {
                    if (unconfigured) {
                        Notice(text = "Sign-in isn't configured on this build — you can continue as a guest.")
                        Spacer(Modifier.height(KineticSpacing.md))
                    }

                    if (ui.mode != AuthMode.RESET_PASSWORD) {
                        ModeSwitcher(ui.mode, vm::setMode)
                        Spacer(Modifier.height(KineticSpacing.lg))
                    } else {
                        Text(
                            text = "RESET PASSWORD",
                            style = KineticType.labelBold.copy(letterSpacing = 2.sp),
                            color = k.foreground,
                        )
                        Spacer(Modifier.height(KineticSpacing.xs))
                        Text(
                            text = "We'll email you a reset link.",
                            style = KineticType.label,
                            color = k.mutedForeground,
                        )
                        Spacer(Modifier.height(KineticSpacing.lg))
                    }

                    AnimatedContent(
                        targetState = ui.mode,
                        transitionSpec = {
                            (fadeIn(tween(KineticMotion.FAST_MS)) +
                                slideInVertically(tween(KineticMotion.FAST_MS)) { it / 6 })
                                .togetherWith(
                                    fadeOut(tween(KineticMotion.FAST_MS)) +
                                        slideOutVertically(tween(KineticMotion.FAST_MS)) { -it / 6 },
                                )
                        },
                        label = "authMode",
                    ) { mode ->
                        Column {
                            if (mode == AuthMode.SIGN_UP) {
                                GlassTextField(
                                    value = ui.name,
                                    onValueChange = vm::setName,
                                    hint = "Full name",
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(KineticSpacing.md))
                            }

                            GlassTextField(
                                value = ui.email,
                                onValueChange = vm::setEmail,
                                hint = "Email",
                                keyboardType = KeyboardType.Email,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(KineticSpacing.md))

                            if (mode != AuthMode.RESET_PASSWORD) {
                                GlassPasswordField(
                                    value = ui.password,
                                    onValueChange = vm::setPassword,
                                    onDone = vm::submitEmail,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    ui.error?.let { error ->
                        Spacer(Modifier.height(KineticSpacing.sm))
                        Text(
                            text = error,
                            style = KineticType.label,
                            color = k.statusError,
                        )
                    }
                    ui.notice?.let { notice ->
                        Spacer(Modifier.height(KineticSpacing.sm))
                        Text(
                            text = notice,
                            style = KineticType.label,
                            color = k.accent,
                        )
                    }

                    Spacer(Modifier.height(KineticSpacing.lg))

                    KineticButton(
                        text = when (ui.mode) {
                            AuthMode.SIGN_IN -> "Sign in"
                            AuthMode.SIGN_UP -> "Create account"
                            AuthMode.RESET_PASSWORD -> "Send reset link"
                        },
                        onClick = vm::submitEmail,
                        enabled = !ui.busy,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (ui.busy) {
                        Spacer(Modifier.height(KineticSpacing.md))
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = k.accent,
                        )
                    }

                    Spacer(Modifier.height(KineticSpacing.md))

                    when (ui.mode) {
                        AuthMode.SIGN_IN -> Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            KineticGhostButton(
                                text = "Forgot password?",
                                onClick = { vm.setMode(AuthMode.RESET_PASSWORD) },
                            )
                        }
                        AuthMode.SIGN_UP -> Unit
                        AuthMode.RESET_PASSWORD -> Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            KineticGhostButton(
                                text = "Back to sign in",
                                onClick = { vm.setMode(AuthMode.SIGN_IN) },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(KineticSpacing.lg))

            // Divider
            Row(
                modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f).height(KineticBorder.hair).background(k.border))
                Text(
                    text = "  OR  ",
                    style = KineticType.label,
                    color = k.mutedForeground,
                )
                Box(Modifier.weight(1f).height(KineticBorder.hair).background(k.border))
            }

            Spacer(Modifier.height(KineticSpacing.lg))

            KineticOutlinedButton(
                text = "Continue with Google",
                onClick = vm::signInWithGoogle,
                enabled = !ui.busy && !unconfigured,
                modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(),
            )

            Spacer(Modifier.height(KineticSpacing.md))

            KineticGhostButton(
                text = "Continue as guest",
                onClick = onGuest,
                modifier = Modifier.widthIn(max = 460.dp),
            )

            Spacer(Modifier.height(KineticSpacing.lg))

            Text(
                text = "Your account keeps your data private and separate. " +
                    "Google sign-in only shares your name and email — no Gmail, " +
                    "Drive, or Contacts access is requested.",
                style = KineticType.label,
                color = k.mutedForeground,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp),
            )

            Spacer(Modifier.height(48.dp))
        }
    }
}

/** SIGN IN / CREATE ACCOUNT segmented switch. */
@Composable
private fun ModeSwitcher(mode: AuthMode, onSelect: (AuthMode) -> Unit) {
    val k = LocalKineticColors.current
    Row(modifier = Modifier.fillMaxWidth()) {
        ModeTab("SIGN IN", mode == AuthMode.SIGN_IN, Modifier.weight(1f)) {
            onSelect(AuthMode.SIGN_IN)
        }
        ModeTab("CREATE ACCOUNT", mode == AuthMode.SIGN_UP, Modifier.weight(1f)) {
            onSelect(AuthMode.SIGN_UP)
        }
    }
}

@Composable
private fun ModeTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val k = LocalKineticColors.current
    Column(
        modifier = modifier
            .background(if (selected) k.accent.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
            .padding(bottom = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.TextButton(onClick = onClick) {
            Text(
                text = label,
                style = KineticType.labelBold.copy(letterSpacing = 1.sp),
                color = if (selected) k.accent else k.mutedForeground,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (selected) 2.dp else KineticBorder.hair)
                .background(if (selected) k.accent else k.border),
        )
    }
}

/** Glass password input with a SHOW/HIDE text toggle (kinetic editorial style). */
@Composable
private fun GlassPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val k = LocalKineticColors.current
    var visible by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }

    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        strength = com.bits.facultyai.ui.components.GlassStrength.REGULAR,
        borderColor = if (focused) k.accent else k.glassBorder,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = KineticSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = KineticType.body.copy(fontSize = 16.sp, color = k.foreground),
                cursorBrush = SolidColor(k.accent),
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text("Password", style = KineticType.body.copy(fontSize = 16.sp), color = k.mutedForeground)
                        }
                        inner()
                    }
                },
            )
            androidx.compose.material3.TextButton(onClick = { visible = !visible }) {
                Text(
                    text = if (visible) "HIDE" else "SHOW",
                    style = KineticType.labelBold,
                    color = k.accent,
                )
            }
        }
    }
}

/** Inline informational banner. */
@Composable
private fun Notice(text: String) {
    val k = LocalKineticColors.current
    Text(
        text = text,
        style = KineticType.label,
        color = k.mutedForeground,
        modifier = Modifier
            .fillMaxWidth()
            .background(k.accent.copy(alpha = 0.08f))
            .padding(KineticSpacing.md),
    )
}
