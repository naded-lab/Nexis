package com.nadidstudio.nexis.ui.screens.login

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.nadidstudio.nexis.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// Animated login (approved "Teal" prototype): fixed teal accent, five characters that
// rotate every 3 seconds. The characters stand in for the future assistants — all five
// are kept ready in the UI even though only some assistants exist so far.
private val Accent = Color(0xFF14B8A6)
private val BgDark = Color(0xFF07110F)
private val CardBg = Color(0xE60D1716)
private val TextLight = Color(0xFFE8F5F3)
private val Muted = Color(0xFF8AA7A3)
private val FieldBg = Color(0xFF081311)
private val OnAccent = Color(0xFF04101A)
private val Hairline = Color(0x1AFFFFFF)

// Hero: mascot in the middle + six assistant bubbles on one circle, 60 degrees apart.
// Clockwise from the top. Layers were cut from the approved hero_one_assistant image.
private class HeroBubble(val res: Int)
private val HeroBubbles = listOf(
    HeroBubble(R.drawable.login_b_idea),
    HeroBubble(R.drawable.login_b_code),
    HeroBubble(R.drawable.login_b_window),
    HeroBubble(R.drawable.login_b_cloud),
    HeroBubble(R.drawable.login_b_palette),
    HeroBubble(R.drawable.login_b_grad),
)
private const val RING_R = 0.355f      // circle radius, fraction of hero width
private const val LINE_IN = 0.21f      // connector starts near the mascot
private const val LINE_OUT = 0.255f    // connector ends just before the bubble
private const val BUBBLE_W = 0.175f    // bubble width, fraction of hero width
private const val MASCOT_W = 0.35f

/**
 * @param onEmailAuth TEMPORARY simulation (no backend yet): called after a short fake delay
 *   with (name, email, isSignup); the caller signs the user in locally.
 */
@Composable
fun NexisLoginScreen(
    onGoogleSignIn: () -> Unit,
    onEmailAuth: (name: String, email: String, signup: Boolean) -> Unit = { _, _, _ -> }
) {
    // The app root is RTL; the approved login design is LTR English.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        LoginContent(onGoogleSignIn, onEmailAuth)
    }
}

@Composable
private fun LoginContent(
    onGoogleSignIn: () -> Unit,
    onEmailAuth: (String, String, Boolean) -> Unit
) {
    // Dark screen: keep system bar icons light while here, restore on leave.
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val ctrl = window?.let { WindowCompat.getInsetsController(it, view) }
        val prevStatus = ctrl?.isAppearanceLightStatusBars
        val prevNav = ctrl?.isAppearanceLightNavigationBars
        ctrl?.isAppearanceLightStatusBars = false
        ctrl?.isAppearanceLightNavigationBars = false
        onDispose {
            if (prevStatus != null) ctrl?.isAppearanceLightStatusBars = prevStatus
            if (prevNav != null) ctrl?.isAppearanceLightNavigationBars = prevNav
        }
    }

    val scope = rememberCoroutineScope()
    var signup by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var focus by remember { mutableIntStateOf(0) } // 0 none, 1 email, 2 password

    val screenAlpha by animateFloatAsState(if (leaving) 0f else 1f, tween(280), label = "leave")

    fun submit() {
        if (busy) return
        scope.launch {
            busy = true
            delay(900) // TEMPORARY: replace with real email auth later
            leaving = true
            delay(300)
            onEmailAuth(name, email, signup)
            busy = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(BgDark)
            .alpha(screenAlpha)
    ) {
        AmbientBackground()
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Hero(focus = focus, loading = busy)
            Spacer(Modifier.height(6.dp))

            Column(
                Modifier
                    .widthIn(max = 380.dp)
                    .fillMaxWidth()
                    .background(CardBg, RoundedCornerShape(22.dp))
                    .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(22.dp))
                    .padding(22.dp)
            ) {
                Text(
                    if (signup) "Create account" else "Welcome back",
                    color = TextLight, fontSize = 24.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))

                AnimatedVisibility(visible = signup) {
                    Column {
                        FieldLabel("Name")
                        AuthField(
                            value = name, onChange = { name = it }, placeholder = "Your name",
                            keyboardType = KeyboardType.Text, ime = ImeAction.Next
                        )
                    }
                }

                FieldLabel("Email")
                AuthField(
                    value = email, onChange = { email = it }, placeholder = "you@example.com",
                    keyboardType = KeyboardType.Email, ime = ImeAction.Next,
                    modifier = Modifier.onFocusChanged {
                        if (it.isFocused) focus = 1 else if (focus == 1) focus = 0
                    }
                )

                FieldLabel("Password")
                AuthField(
                    value = pass, onChange = { pass = it }, placeholder = "••••••••",
                    keyboardType = KeyboardType.Password, ime = ImeAction.Done,
                    visual = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    onDone = { submit() },
                    modifier = Modifier.onFocusChanged {
                        if (it.isFocused) focus = 2 else if (focus == 2) focus = 0
                    },
                    trailing = {
                        IconButton(onClick = { showPass = !showPass }) {
                            Icon(
                                imageVector = if (showPass) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showPass) "Hide password" else "Show password",
                                tint = Muted
                            )
                        }
                    }
                )

                Spacer(Modifier.height(20.dp))
                val btnShape = RoundedCornerShape(12.dp)
                Button(
                    onClick = { submit() },
                    enabled = !busy,
                    shape = btnShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent, contentColor = OnAccent,
                        disabledContainerColor = Accent.copy(alpha = 0.8f), disabledContentColor = OnAccent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .shadow(10.dp, btnShape, ambientColor = Accent.copy(alpha = 0.18f), spotColor = Accent.copy(alpha = 0.18f))
                ) {
                    Text(if (signup) "Sign up" else "Login", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    if (busy) {
                        Spacer(Modifier.width(8.dp))
                        CircularProgressIndicator(Modifier.size(16.dp), color = OnAccent, strokeWidth = 2.dp)
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f).height(1.dp).background(Color(0x1FFFFFFF)))
                    Text("or", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp))
                    Box(Modifier.weight(1f).height(1.dp).background(Color(0x1FFFFFFF)))
                }

                SocialButton(
                    label = if (signup) "Sign up with Google" else "Continue with Google",
                    iconRes = R.drawable.ic_google, tintIcon = false, onClick = onGoogleSignIn
                )

                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        if (signup) "Already have an account? " else "Don't have an account? ",
                        color = Muted, fontSize = 14.sp
                    )
                    Text(
                        if (signup) "Login" else "Sign up",
                        color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { signup = !signup }
                    )
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
}

@Composable
private fun AuthField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    ime: ImeAction,
    modifier: Modifier = Modifier,
    visual: VisualTransformation = VisualTransformation.None,
    onDone: () -> Unit = {},
    trailing: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text(placeholder, color = Muted.copy(alpha = 0.7f)) },
        visualTransformation = visual,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ime),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        trailingIcon = trailing,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextLight, unfocusedTextColor = TextLight,
            focusedContainerColor = FieldBg, unfocusedContainerColor = FieldBg,
            focusedBorderColor = Accent, unfocusedBorderColor = Hairline,
            cursorColor = Accent
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun SocialButton(label: String, iconRes: Int, tintIcon: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0x26FFFFFF)),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0x0DFFFFFF), contentColor = TextLight),
        modifier = Modifier.fillMaxWidth().height(50.dp)
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            colorFilter = if (tintIcon) ColorFilter.tint(TextLight) else null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 15.sp)
    }
}

/** Soft drifting glows + twinkling sparks behind everything. */
@Composable
private fun AmbientBackground() {
    val t = rememberInfiniteTransition(label = "bg")
    val drift1 by t.animateFloat(0f, 1f, infiniteRepeatable(tween(10000, easing = LinearEasing), RepeatMode.Reverse), label = "d1")
    val drift2 by t.animateFloat(0f, 1f, infiniteRepeatable(tween(13000, easing = LinearEasing), RepeatMode.Reverse), label = "d2")
    val twinkle by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "tw")
    val sparks = remember { List(14) { Triple(Random.nextFloat(), Random.nextFloat(), Random.nextFloat()) } }

    Canvas(Modifier.fillMaxSize()) {
        val d = density
        val c1 = Offset(size.width * 0.1f + 50f * d * drift1, size.height * 0.05f + 40f * d * drift1)
        drawCircle(
            brush = Brush.radialGradient(listOf(Accent.copy(alpha = 0.22f), Color.Transparent), c1, 240f * d),
            radius = 240f * d, center = c1
        )
        val c2 = Offset(size.width * 0.95f - 50f * d * drift2, size.height * 0.97f - 40f * d * drift2)
        drawCircle(
            brush = Brush.radialGradient(listOf(Accent.copy(alpha = 0.15f), Color.Transparent), c2, 200f * d),
            radius = 200f * d, center = c2
        )
        sparks.forEach { (sx, sy, phase) ->
            val a = 0.05f + 0.45f * abs(sin(((twinkle + phase) * PI).toFloat()))
            val s = 5f * d
            val p = Offset(sx * size.width, sy * size.height)
            rotate(45f, p) {
                drawRect(Accent.copy(alpha = a), p, Size(s, s))
            }
        }
    }
}

/** Mascot + 6 bubbles on an even hexagon, slow dashed ring, field-focus reactions. */
@Composable
private fun Hero(focus: Int, loading: Boolean) {
    val t = rememberInfiniteTransition(label = "hero")
    val breathe by t.animateFloat(1f, 1.03f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "breathe")
    val ring by t.animateFloat(0f, 360f, infiniteRepeatable(tween(43000, easing = LinearEasing)), label = "ring")

    val tgtY = when { loading -> -8f; focus == 1 -> -3f; focus == 2 -> 4f; else -> 0f }
    val tgtScale = when { loading -> 1.07f; focus == 1 -> 1.03f; focus == 2 -> 0.98f; else -> 1f }
    val tgtRot = when (focus) { 1 -> -1f; 2 -> 1f; else -> 0f }
    val y by animateFloatAsState(tgtY, tween(500), label = "fy")
    val sc by animateFloatAsState(tgtScale, tween(500), label = "fs")
    val rot by animateFloatAsState(tgtRot, tween(500), label = "fr")

    BoxWithConstraints(
        Modifier
            .widthIn(max = 340.dp)
            .fillMaxWidth(0.88f)
            .aspectRatio(1f)
    ) {
        val w = maxWidth
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = y.dp.toPx()
                    scaleX = sc
                    scaleY = sc
                    rotationZ = rot
                }
        ) {
            // dashed ring + connector lines (drawn, so spacing is exactly even)
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val u = size.width
                val dash = PathEffect.dashPathEffect(floatArrayOf(0.016f * u, 0.014f * u))
                rotate(ring, c) {
                    drawCircle(Accent.copy(alpha = 0.28f), RING_R * u, c, style = Stroke(1.dp.toPx(), pathEffect = dash))
                }
                HeroBubbles.indices.forEach { i ->
                    val a = Math.toRadians(i * 60.0)
                    val sx = sin(a).toFloat(); val cy = cos(a).toFloat()
                    val p1 = Offset(c.x + LINE_IN * u * sx, c.y - LINE_IN * u * cy)
                    val p2 = Offset(c.x + LINE_OUT * u * sx, c.y - LINE_OUT * u * cy)
                    drawLine(Accent.copy(alpha = 0.5f), p1, p2, strokeWidth = 1.dp.toPx())
                    drawCircle(Accent.copy(alpha = 0.8f), 2.5.dp.toPx(), p1)
                }
            }

            Image(
                painter = painterResource(id = R.drawable.login_hero_mascot),
                contentDescription = "Nexis",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(w * MASCOT_W)
                    .aspectRatio(387f / 392f)
                    .graphicsLayer { scaleX = breathe; scaleY = breathe }
            )

            HeroBubbles.forEachIndexed { i, b ->
                val a = Math.toRadians(i * 60.0)
                val size = w * BUBBLE_W
                val cx = w * (0.5f + RING_R * sin(a).toFloat())
                val cyy = w * (0.5f - RING_R * cos(a).toFloat())
                HeroBubbleView(
                    res = b.res, index = i,
                    modifier = Modifier
                        .offset(x = cx - size / 2, y = cyy - size / 2)
                        .width(size)
                )
            }
        }
    }
}

@Composable
private fun HeroBubbleView(res: Int, index: Int, modifier: Modifier) {
    // each bubble floats on its own phase so they never move in lockstep
    val t = rememberInfiniteTransition(label = "bub$index")
    val bob by t.animateFloat(
        0f, -0.06f,
        infiniteRepeatable(tween(3400), RepeatMode.Reverse, initialStartOffset = StartOffset(index * 550, StartOffsetType.FastForward)),
        label = "bob"
    )
    var pop by remember { mutableStateOf(false) }
    val sc by animateFloatAsState(if (pop) 1.15f else 1f, tween(200), label = "pop")
    LaunchedEffect(pop) { if (pop) { delay(200); pop = false } }
    Image(
        painter = painterResource(id = res),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .aspectRatio(1f)
            .graphicsLayer { translationY = bob * size.height; scaleX = sc; scaleY = sc }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { pop = true }
    )
}
