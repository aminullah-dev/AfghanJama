@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.afghanjama.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * اجزای مشترکِ ظاهرِ اپ. هدف: هر صفحه به‌جای ساختنِ دوبارهٔ کارت و
 * نوارِ بالا و حالتِ خالی، از این‌ها استفاده کند تا کلِ اپ یک زبانِ
 * بصریِ واحد داشته باشد و تغییرِ ظاهر از یک نقطه انجام شود.
 */

/** فاصلهٔ داخلیِ استانداردِ کارت‌ها. */
val CardPadding = 14.dp

/** فاصلهٔ استانداردِ لبهٔ صفحه. */
val ScreenPadding = 16.dp

/**
 * کارتِ استانداردِ اپ.
 *
 * سطحِ سفید روی زمینهٔ گرمِ روشن (و در تاریکی، سطحِ تیره روی زمینهٔ
 * تیره‌تر) اختلافِ روشناییِ کمی دارد؛ بدونِ سایه و بدونِ خط، لبهٔ کارت
 * عملاً دیده نمی‌شد. یک خطِ مویی به‌جای سایه، کارت را بدونِ شلوغی
 * تعریف می‌کند و در هر دو حالتِ روشن و تاریک درست کار می‌کند.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surface,
    bordered: Boolean = true,
    onClick: (() -> Unit)? = null,
    contentPadding: androidx.compose.ui.unit.Dp = CardPadding,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = CardDefaults.cardColors(containerColor = container)
    val elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    val border = if (bordered) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            colors = colors,
            elevation = elevation,
            border = border
        ) {
            Column(
                Modifier.padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                content = content
            )
        }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = colors,
            elevation = elevation,
            border = border
        ) {
            Column(
                Modifier.padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                content = content
            )
        }
    }
}

/**
 * کارتِ تأکیددار (خلاصه، هشدار، وضعیت). چون زمینه‌اش رنگی است
 * خودش دیده می‌شود و خط لازم ندارد.
 */
@Composable
fun AccentCard(
    container: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) = AppCard(
    modifier = modifier,
    container = container,
    bordered = false,
    onClick = onClick,
    content = content
)

/** عنوانِ یک بخش در صفحه. */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    hint: String? = null
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        if (!hint.isNullOrBlank()) {
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** یک سطرِ «برچسب — مقدار». */
@Composable
fun InfoRow(
    label: String,
    value: String,
    strong: Boolean = false,
    valueColor: Color? = null
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.SemiBold,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * حالتِ خالی. به‌جای یک خطِ متنِ خاکستری، می‌گوید «چه خبر است» و
 * «قدمِ بعدی چیست» — تفاوتِ اپِ حرفه‌ای و اپِ نیمه‌کاره معمولاً همین‌جاست.
 */
@Composable
fun EmptyState(
    icon: String,
    title: String,
    hint: String? = null,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(icon, fontSize = 40.sp)
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            if (!hint.isNullOrBlank()) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** نوارِ بالای استاندارد با دکمهٔ برگشت. */
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "برگشت")
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

/** اسکلتِ استانداردِ یک صفحهٔ داخلی: نوارِ بالا + محتوا. */
@Composable
fun AppScreen(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit
) {
    Scaffold(
        topBar = { AppTopBar(title = title, onBack = onBack, actions = actions) },
        snackbarHost = snackbarHost,
        content = content
    )
}
