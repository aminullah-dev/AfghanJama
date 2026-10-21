package com.afghanjama.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.afghanjama.ui.vm.Access
import com.afghanjama.ui.vm.HubClientViewModel
import com.afghanjama.ui.vm.HubDeepLink
import com.afghanjama.ui.vm.WorkshopLinkViewModel

/**
 * «اشتراکِ کارگاه» روی گوشی: صفحهٔ قدیم (دستگاهِ اصلی، یا گوشیِ کارگر با رمزِ
 * شش‌رقمی) — یا، وقتی گوشی با کامپیوترِ ۱.۱۱ جفت است یا دارد جفت می‌شود،
 * صفحهٔ «کارگاهِ وصل» ([HubClientScreen]).
 *
 * [hubVm] روی کامپیوتر `null` است: کامپیوتر مرکز است، نه مشتری.
 */
@Composable
fun WorkshopLinkRoute(
    vm: WorkshopLinkViewModel,
    hubVm: HubClientViewModel?,
    access: Access,
    onBack: () -> Unit,
) {
    var wantV2 by remember { mutableStateOf(false) }
    val deep by HubDeepLink.pending.collectAsState()
    val hub = hubVm?.state?.collectAsState()?.value
    val showV2 = hubVm != null && (hub?.paired == true || hub?.waitingApproval == true || wantV2 || deep != null)
    if (showV2) {
        HubClientScreen(
            vm = hubVm!!,
            onBack = { if (hub?.paired != true && wantV2) wantV2 = false else onBack() },
        )
    } else {
        WorkshopLinkScreen(
            vm = vm,
            access = access,
            onBack = onBack,
            onUseHub = if (hubVm == null) null else { raw ->
                raw?.let { HubDeepLink.offer(it) }
                wantV2 = true
            },
        )
    }
}
