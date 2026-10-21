@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.afghanjama.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afghanjama.AppInfo
import com.afghanjama.prefs.CompanyPrefs
import com.afghanjama.prefs.LocalSettings
import com.afghanjama.ui.components.BrandButton
import com.afghanjama.ui.components.CardPadding
import com.afghanjama.ui.components.Pill
import com.afghanjama.ui.components.PillTone
import com.afghanjama.ui.components.Space
import com.afghanjama.ui.components.glass
import com.afghanjama.ui.components.GlassTokens
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.afghanjama.ui.format.digitsOnly
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.format.toPersianDigits
import com.afghanjama.ui.theme.Brand
import com.afghanjama.ui.theme.CoralBrush
import com.afghanjama.ui.theme.PetrolBrush
import com.afghanjama.ui.theme.Radius
import com.afghanjama.ui.vm.AuthUi
import com.afghanjama.ui.vm.AuthViewModel
import com.afghanjama.ui.vm.LoginPerson
import com.afghanjama.util.nowMillis
import kotlinx.coroutines.delay

/**
 * یک خطِ ساده دربارهٔ نقش، زیرِ کاشی‌ها و بالای رمز — یک جا تعریف، چون
 * آزمونِ صفحه‌ها هم دنبالش می‌گردد.
 */
const val ROLE_NOTE = "نقشِ هر کس مالِ خودِ اوست و فقط مدیر آن را در «کاربران» عوض می‌کند."

/**
 * صفحهٔ ورود — کاشیِ هر آدمِ کارگاه، بعد رمزِ خودش.
 *
 * **چرا کاشی.** تا ۱.۱۱ صفحه یک خانهٔ رمز بود و سرِ اولین ورود نقش هم
 * انتخاب می‌شد و دیگر عوض نمی‌شد. برای کارگرِ افغان که اولین بار برنامه
 * را می‌بیند، «شما چه کسی هستید؟» با فهرستِ «مدیریتِ دوخت، مسئولِ خرید…»
 * پرسشِ گیج‌کننده‌ای بود، و جوابِ غلطش دستگاهی بی مدیر می‌ساخت. حالا:
 *
 *  - **بارِ اول:** نخستین کسی که رمز می‌گذارد مدیر است — نام و رمز، همین.
 *  - **هر روز:** کاشی‌ها با نام و قرصِ نقش؛ نامِ خود را می‌زنید و رمزِ
 *    چهاررقمیِ خودتان را. رمز فقط با همان نفر سنجیده می‌شود.
 *  - **«خروج»** به همین فهرست برمی‌گردد تا نفرِ بعدی وارد شود.
 *  - رمزِ اشتباهِ پیاپی کاشیِ همان نفر را چند لحظه می‌بندد (`PinLockout`).
 *
 * زمینه همیشه نفتی است، در حالتِ روشن و تاریک: این صفحه هویتِ اپ است، نه
 * داده‌ای که باید خوانده شود؛ یک هویت باید همیشه یک شکل باشد.
 */
@Composable
fun LoginScreen(
    vm: AuthViewModel,
    onLoggedIn: () -> Unit
) {
    val ui by vm.ui.collectAsState()
    val settings = LocalSettings.current
    val coName = remember { CompanyPrefs.name(settings) }

    LaunchedEffect(ui.isLoggedIn) {
        if (ui.isLoggedIn) onLoggedIn()
    }
    if (ui.isLoggedIn) return

    Box(Modifier.fillMaxSize().background(PetrolBrush)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.xl, vertical = Space.xxl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BrandMark(coName)
            Spacer(Modifier.height(Space.xl))
            Column(
                Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.lg),
            ) {
                val selected = ui.selected
                when {
                    !ui.isSetupDone -> FirstManager(vm, ui)
                    selected != null -> PinStep(vm, ui, selected)
                    else -> PeopleStep(vm, ui)
                }
            }
        }
    }
}

/** نشانِ اپ، نام و نامِ کارگاه — بالای هر سه حالت. */
@Composable
private fun BrandMark(coName: String) {
    Box(
        Modifier.size(64.dp).clip(CircleShape).background(CoralBrush),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Checkroom, contentDescription = null, tint = Brand.OnCoral, modifier = Modifier.size(32.dp))
    }
    Spacer(Modifier.height(Space.md))
    Text(
        AppInfo.NAME,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = Brand.OnPetrol
    )
    Text(
        coName.ifBlank { "سامانهٔ مدیریتِ کارگاهِ خیاطی" },
        style = MaterialTheme.typography.bodyMedium,
        color = Brand.OnPetrolMuted,
        textAlign = TextAlign.Center
    )
}

/** کاشی‌ها: «نامِ خود را بزنید». */
@Composable
private fun PeopleStep(vm: AuthViewModel, ui: AuthUi) {
    Text(
        "نامِ خود را بزنید",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = Brand.OnPetrol,
    )
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.md, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        ui.people.forEach { p -> PersonTile(p, onClick = { vm.select(p.id) }) }
    }
    Text(
        ROLE_NOTE,
        style = MaterialTheme.typography.bodySmall,
        color = Brand.OnPetrolMuted,
        textAlign = TextAlign.Center,
    )
}

/** یک کاشی: حرفِ اولِ نام، نام، و قرصِ نقش. */
@Composable
private fun PersonTile(p: LoginPerson, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.xl)
    Column(
        Modifier
            .width(168.dp)
            .glass(shape, elevation = 4.dp, fillAlpha = GlassTokens.ON_DARK_ALPHA)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "${p.name}، ${p.role}" }
            .padding(vertical = Space.lg, horizontal = Space.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Avatar(p.name, big = true)
        Text(
            p.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Pill(p.role, tone = if (p.isManager) PillTone.Coral else PillTone.Brand)
    }
}

@Composable
private fun Avatar(name: String, big: Boolean) {
    val size = if (big) 56.dp else 44.dp
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.trim().take(1),
            style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/**
 * رمزِ همان نفر: نقطه‌ها، صفحه‌کلیدِ عددی، و کادرِ پنهانِ صفحه‌کلیدِ
 * واقعی (کامپیوتر و گوشی با صفحه‌کلیدِ سخت‌افزاری). «Enter» وارد می‌کند.
 */
@Composable
private fun PinStep(vm: AuthViewModel, ui: AuthUi, person: LoginPerson) {
    var pin by remember(person.id) { mutableStateOf("") }
    val locked = rememberLockLeft(ui.lockedUntil)
    val submit = {
        if (pin.length >= MIN_PIN && locked <= 0L) {
            vm.loginAs(person.id, pin)
            pin = ""
        }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(person.id) { runCatching { focus.requestFocus() } }

    Column(
        Modifier.widthIn(max = 380.dp).fillMaxWidth().glass(RoundedCornerShape(Radius.xxl), fillAlpha = GlassTokens.ON_DARK_ALPHA).padding(CardPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.select(null) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Space.xs))
                Text("نفرِ دیگر")
            }
        }
        Avatar(person.name, big = true)
        Text(person.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Pill(person.role, tone = if (person.isManager) PillTone.Coral else PillTone.Brand)
        Text(
            "رمزِ خود را بزنید",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // صفحه‌کلیدِ واقعی هم کار کند: همان رمز، همان دکمهٔ «ورود».
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.digitsOnly().take(MAX_PIN) },
            label = { Text("رمز") },
            singleLine = true,
            enabled = locked <= 0L,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier
                .fillMaxWidth()
                // «Enter» همان «ورود» است (`ImeAction.Done`). رویدادِ کلیدِ خام
                // اینجا خوانده نمی‌شود: کلاسش روی کامپیوتر و گوشی فرق دارد
                // (`platformcall`).
                .focusRequester(focus),
        )

        PinPad(
            enabled = locked <= 0L,
            onDigit = { d -> if (pin.length < MAX_PIN) pin += d },
            onBack = { pin = pin.dropLast(1) },
        )

        BrandButton(
            text = "ورود",
            onClick = { submit() },
            enabled = pin.length >= MIN_PIN && locked <= 0L,
            modifier = Modifier.fillMaxWidth(),
            leading = { Icon(Icons.Default.Lock, contentDescription = null, tint = Brand.OnCoral) },
        )

        val msg = when {
            locked > 0L -> "چند بار رمزِ اشتباه زده شد. ${waitText(locked)} صبر کنید و دوباره بزنید."
            else -> ui.message
        }
        msg?.let { Notice(it, isError = ui.isError || locked > 0L) }
        Text(
            ROLE_NOTE,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * صفحه‌کلیدِ عددیِ ۳×۴ با عددهای فارسی؛ هر دکمه دست‌کم ۵۶ نقطه.
 *
 * **چپ‌به‌راست، مثلِ شماره‌گیرِ گوشی.** در چیدمانِ راست‌به‌چپ «۱» به راست
 * می‌رفت؛ صفحه‌کلیدِ تلفن در گوشی‌های دری هم ۱ را چپ دارد و دست همان را
 * می‌شناسد.
 */
@Composable
private fun PinPad(enabled: Boolean, onDigit: (Char) -> Unit, onBack: () -> Unit) =
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { PinPadLtr(enabled, onDigit, onBack) }

@Composable
private fun PinPadLtr(enabled: Boolean, onDigit: (Char) -> Unit, onBack: () -> Unit) {
    val rows = listOf("123", "456", "789")
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm), horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                r.forEach { d -> PadKey(d.toString().toPersianDigits(), enabled) { onDigit(d) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Spacer(Modifier.size(PAD_KEY))
            PadKey("۰", enabled) { onDigit('0') }
            Box(
                Modifier.size(PAD_KEY).clip(CircleShape)
                    .clickable(enabled = enabled, role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = "پاک کردنِ یک رقم" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PadKey(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(PAD_KEY)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * بارِ اول: «نخستین مدیرِ این دستگاه» — نام و رمز. نقش پرسیده نمی‌شود؛
 * نخستین نفر مدیر است و نقشِ بقیه را خودش در «کاربران» می‌گذارد.
 */
@Composable
private fun FirstManager(vm: AuthViewModel, ui: AuthUi) {
    var pin by remember { mutableStateOf("") }
    Column(
        Modifier.widthIn(max = 420.dp).fillMaxWidth().glass(RoundedCornerShape(Radius.xxl), fillAlpha = GlassTokens.ON_DARK_ALPHA).padding(CardPadding),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Text("نخستین مدیرِ این دستگاه", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "نام و یک رمزِ چهاررقمی بگذارید. نخستین کسی که اینجا رمز می‌گذارد مدیرِ کارگاه است؛ " +
                "بقیه را مدیر در «کاربران» با نام، رمز و نقشِ خودشان می‌سازد.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = ui.userName,
            onValueChange = vm::setUserName,
            label = { Text("نامِ شما") },
            supportingText = { Text("روی کاشیِ ورود و رویدادهایی که ثبت می‌کنید می‌نشیند") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.digitsOnly().take(MAX_PIN) },
            label = { Text("رمز (دست‌کم ${MIN_PIN.fa()} رقم)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.clearMessage(); vm.setupPin(pin) }),
            visualTransformation = PasswordVisualTransformation()
        )
        BrandButton(
            text = "ثبت و ورود",
            onClick = {
                vm.clearMessage()
                vm.setupPin(pin)
            },
            modifier = Modifier.fillMaxWidth(),
            leading = { Icon(Icons.Default.Lock, contentDescription = null, tint = Brand.OnCoral) }
        )
        ui.message?.let { Notice(it, ui.isError) }
    }
}

@Composable
private fun Notice(msg: String, isError: Boolean) {
    val c = MaterialTheme.colorScheme
    val bg = if (isError) c.errorContainer else c.primaryContainer
    val fg = if (isError) c.onErrorContainer else c.onPrimaryContainer
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(bg).padding(Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        Icon(if (isError) Icons.Default.ErrorOutline else Icons.Default.Lock, contentDescription = null, tint = fg)
        Text(msg, style = MaterialTheme.typography.bodySmall, color = fg)
    }
}

/** چند میلی‌ثانیه از قفل مانده — هر ثانیه تازه، تا شمارش جلوی چشم برود. */
@Composable
private fun rememberLockLeft(until: Long): Long {
    var left by remember(until) { mutableLongStateOf((until - nowMillis()).coerceAtLeast(0L)) }
    LaunchedEffect(until) {
        while (true) {
            left = (until - nowMillis()).coerceAtLeast(0L)
            if (left <= 0L) break
            delay(1_000)
        }
    }
    return left
}

private fun waitText(ms: Long): String {
    val sec = ((ms + 999) / 1000).coerceAtLeast(1)
    return if (sec >= 60) "${((sec + 59) / 60).fa()} دقیقه" else "${sec.fa()} ثانیه"
}

private const val MIN_PIN = 4
private const val MAX_PIN = 8
private val PAD_KEY = 56.dp
