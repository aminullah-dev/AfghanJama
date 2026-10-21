package com.afghanjama.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afghanjama.data.repo.Repo
import com.afghanjama.ui.components.Pill
import com.afghanjama.ui.components.PillTone
import com.afghanjama.ui.components.Space
import com.afghanjama.ui.components.glass
import com.afghanjama.ui.nav.allowedFor
import com.afghanjama.ui.theme.Radius
import com.afghanjama.ui.vm.Access
import com.afghanjama.util.NameMatch
import kotlinx.coroutines.flow.flowOf

/*
 * جست‌وجوی همه‌جا (⌘K روی مک، Ctrl+K روی ویندوز) — طرح، C.5.
 *
 * یک خانه که سه چیز را می‌گردد: **بخش‌های نوار** (به نام)، **سفارش‌ها** (کد،
 * نامِ طرح، مشتری) و **مشتری‌ها** (نام، شماره). «Enter» اولی را باز می‌کند، و
 * «Esc» می‌بندد. هر نتیجه همان دسترسیِ نوار را می‌خواهد ([allowedFor]).
 */

/** یک نتیجه: چه نوشته شود و چه کند. */
internal data class PaletteHit(val title: String, val kind: String, val open: () -> Unit)

/** «فارسی‌سازیِ» جست‌وجو: ی/ک عربی، فاصله، و رقم‌های فارسی همه یکی. */
internal fun paletteMatch(text: String, query: String): Boolean {
    val q = NameMatch.normalize(query).trim()
    if (q.isEmpty()) return false
    return NameMatch.normalize(text).contains(q)
}

/**
 * میان‌برهای پنجره — فقط وقتی کسی وارد است. `true` یعنی کلید مصرف شد.
 *
 * ⌘/Ctrl+K جست‌وجو، ⌘/Ctrl+D داشبورد. «Esc» را خودِ جست‌وجو می‌گیرد.
 */
internal fun deskShortcut(e: KeyEvent, nav: DeskNav): Boolean {
    if (!nav.signedIn || e.type != KeyEventType.KeyDown) return false
    if (!(e.isCtrlPressed || e.isMetaPressed)) return false
    return when (e.key) {
        Key.K -> {
            nav.palette = true
            true
        }
        Key.D -> {
            nav.palette = false
            nav.go(Section.Dashboard)
            true
        }
        else -> false
    }
}

@Composable
internal fun CommandPalette(repo: Repo, access: Access, nav: DeskNav) {
    var query by remember { mutableStateOf("") }
    val orders by remember { repo.observeAllOrders() }.collectAsState(emptyList())
    val customers by remember {
        if (Section.Customers.allowedFor(access)) repo.observeCustomers() else flowOf(emptyList())
    }.collectAsState(emptyList())
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val close = { nav.palette = false }
    val hits = buildList {
        Section.entries.filter { it.allowedFor(access) && paletteMatch(it.title, query) }.take(6).forEach { s ->
            add(PaletteHit(s.title, "بخش") { close(); nav.go(s) })
        }
        orders.asSequence()
            .filter { o -> paletteMatch(o.orderCode, query) || paletteMatch(o.designTitle, query) || paletteMatch(o.customerName, query) }
            .take(6)
            .forEach { o ->
                val title = listOf(o.orderCode, o.designTitle, o.customerName).filter { it.isNotBlank() }.joinToString(" — ")
                add(PaletteHit(title, "سفارش") { close(); nav.openOrder = o.id.toString() })
            }
        customers.asSequence()
            .filter { c -> paletteMatch(c.name, query) || paletteMatch(c.phone.orEmpty(), query) }
            .take(6)
            .forEach { c ->
                add(PaletteHit(c.name, "مشتری") { close(); nav.go(Section.Customers); nav.openCustomer = c.id })
            }
    }

    // پردهٔ کم‌رنگ؛ کلیک بیرون می‌بندد.
    Box(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.18f))
            .clickable(onClick = close),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.padding(top = 80.dp).widthIn(max = 640.dp).fillMaxWidth()
                .glass(RoundedCornerShape(Radius.xl), fillAlpha = 0.97f)
                .clickable(enabled = false) {}
                .padding(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("جست‌وجو در سفارش، مشتری و بخش‌ها…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { hits.firstOrNull()?.open?.invoke() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { e ->
                    when {
                        e.type == KeyEventType.KeyDown && e.key == Key.Escape -> { close(); true }
                        e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter) -> {
                            hits.firstOrNull()?.open?.invoke(); true
                        }
                        else -> false
                    }
                },
            )
            if (query.isNotBlank() && hits.isEmpty()) {
                Text("چیزی پیدا نشد.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            hits.forEach { h ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).clickable { h.open() }
                        .padding(horizontal = Space.sm, vertical = Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    Pill(h.kind, tone = PillTone.Neutral)
                    Text(h.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(
                "باز کردنِ اولین نتیجه: Enter • بستن: Esc • داشبورد: ⌘D یا Ctrl+D",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
