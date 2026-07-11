@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.afghanjama.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afghanjama.data.entities.WorkCost
import com.afghanjama.ui.format.digitsOnly
import com.afghanjama.ui.vm.MasterDataViewModel

@Composable
fun MasterDataScreen(
    vm: MasterDataViewModel,
    onBack: () -> Unit
) {
    val types by vm.fabricTypes.collectAsState()
    val colors by vm.fabricColors.collectAsState()
    val sizes by vm.sizes.collectAsState()
    val tailors by vm.tailors.collectAsState()
    val inspectors by vm.inspectors.collectAsState()
    val designs by vm.designs.collectAsState()
    val customers by vm.customers.collectAsState()
    val workCosts by vm.workCosts.collectAsState()
    val staff by vm.staff.collectAsState()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("نوع پارچه", "رنگ", "سایز", "خیاط", "ناظر", "طرح", "خریدار", "خرج کار", "کارکنان")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مدیریت اطلاعات پایه") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "برگشت")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            ScrollableTabRow(
                selectedTabIndex = tab,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { i, t ->
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i },
                        text = { Text(t, style = MaterialTheme.typography.labelLarge) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> SimpleListEditor(
                        title = "فهرست انواع پارچه",
                        hint = "مثلاً: کتان، برزنت، لینن…",
                        items = types.map { it.title },
                        onAdd = { vm.addFabricType(it) }
                    )

                    1 -> SimpleListEditor(
                        title = "فهرست رنگ‌های موجود",
                        hint = "مثلاً: سرمه‌ای، استخوانی…",
                        items = colors.map { it.title },
                        onAdd = { vm.addFabricColor(it, null) }
                    )

                    2 -> SimpleListEditor(
                        title = "فهرست سایزها",
                        hint = "مثلاً: 42 یا مدیوم",
                        items = sizes.map { it.title },
                        onAdd = { vm.addSize(it) }
                    )

                    3 -> TwoFieldListEditor(
                        title = "تعریف خیاط جدید",
                        hint1 = "کد شناسایی (مثلاً T10)",
                        hint2 = "نام و نام خانوادگی",
                        items = tailors.map { "[${it.code}] ${it.name}" },
                        onAdd = { code, name -> vm.addTailor(code, name, null) }
                    )

                    4 -> TwoFieldListEditor(
                        title = "تعریف ناظر کیفی",
                        hint1 = "کد پرسنلی",
                        hint2 = "نام ناظر",
                        items = inspectors.map { "[${it.code}] ${it.name}" },
                        onAdd = { code, name -> vm.addInspector(code, name, null) }
                    )

                    5 -> TwoFieldListEditor(
                        title = "طرح‌های دوخت — کدِ اختصاصیِ کارگاه را خودتان وارد کنید",
                        hint1 = "نام طرح (مثلاً یقه دیپلمات)",
                        hint2 = "کد اختصاصی طرح (مثلاً DIP-12)",
                        items = designs.map { d ->
                            (if (d.code.isNotBlank()) "[${d.code}] " else "") + d.title
                        },
                        // ثبتِ دوبارهٔ همان نام با کدِ جدید = اصلاحِ کدِ طرح
                        onAdd = { title, code -> vm.addDesign(title, code) }
                    )

                    6 -> TwoFieldListEditor(
                        title = "بانک اطلاعات مشتریان",
                        hint1 = "نام خریدار / فروشگاه",
                        hint2 = "شماره تماس (اختیاری)",
                        items = customers.map { it.name + (it.phone?.let { p -> " - $p" } ?: "") },
                        onAdd = { name, phone -> vm.addCustomer(name, phone.ifBlank { null }) }
                    )

                    // ✅ خرج کار
                    7 -> WorkCostEditor(
                        items = workCosts,
                        onAdd = { title, price -> vm.addWorkCost(title, price) },
                        onEdit = { id, title, price -> vm.updateWorkCost(id, title, price) },
                        onDelete = { id -> vm.deleteWorkCost(id) }
                    )

                    8 -> TwoFieldListEditor(
                        title = "کارکنان کارگاه (برای حضور و غیاب و حساب کارمند)",
                        hint1 = "نام کارمند",
                        hint2 = "سمت (آشپز، حسابدار، مدیر، …)",
                        items = staff.map { s ->
                            s.name + (if (s.role.isNotBlank()) " — ${s.role}" else "")
                        },
                        onAdd = { name, role -> vm.addStaff(name, role) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkCostEditor(
    items: List<WorkCost>,
    onAdd: (String, Long) -> Unit,
    onEdit: (Long, String, Long) -> Unit,
    onDelete: (Long) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }

    var editItem by remember { mutableStateOf<WorkCost?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editPriceText by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "خرج کار (WorkCost)",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("عنوان (مثلاً: دوخت یقه)") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = priceText,
            onValueChange = { priceText = it.digitsOnly() },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("قیمت (؋)") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium
        )

        Spacer(Modifier.height(10.dp))

        Button(
            onClick = {
                val t = title.trim()
                val p = priceText.toLongOrNull() ?: 0L
                if (t.isNotBlank() && p > 0L) {
                    onAdd(t, p)
                    title = ""
                    priceText = ""
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) { Text("افزودن خرج کار") }

        Spacer(Modifier.height(16.dp))

        LazyColumn(Modifier.fillMaxSize()) {
            items(items, key = { it.id }) { w ->
                ListItem(
                    headlineContent = { Text(w.title, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("${w.price} ؋", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingContent = {
                        Row {
                            IconButton(
                                onClick = {
                                    editItem = w
                                    editTitle = w.title
                                    editPriceText = w.price.toString()
                                }
                            ) { Icon(Icons.Default.Edit, contentDescription = "ویرایش") }

                            IconButton(onClick = { onDelete(w.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف")
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }

    // دیالوگ ویرایش
    if (editItem != null) {
        AlertDialog(
            onDismissRequest = { editItem = null },
            title = { Text("ویرایش خرج کار") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("عنوان") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editPriceText,
                        onValueChange = { editPriceText = it.digitsOnly() },
                        label = { Text("قیمت (؋)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val item = editItem ?: return@TextButton
                        val t = editTitle.trim()
                        val p = editPriceText.toLongOrNull() ?: 0L
                        if (t.isNotBlank() && p > 0L) {
                            onEdit(item.id, t, p)
                            editItem = null
                        }
                    }
                ) { Text("ذخیره") }
            },
            dismissButton = {
                TextButton(onClick = { editItem = null }) { Text("لغو") }
            }
        )
    }
}

@Composable
private fun SimpleListEditor(
    title: String,
    hint: String,
    items: List<String>,
    onAdd: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(Modifier.height(8.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(hint) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )
            Button(
                onClick = {
                    val v = text.trim()
                    if (v.isNotEmpty()) {
                        onAdd(v)
                        text = ""
                    }
                },
                modifier = Modifier.height(56.dp),
                shape = MaterialTheme.shapes.medium
            ) { Text("افزودن") }
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(Modifier.fillMaxSize()) {
            items(items.reversed()) { row ->
                ListItem(
                    headlineContent = { Text(row) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun TwoFieldListEditor(
    title: String,
    hint1: String,
    hint2: String,
    items: List<String>,
    onAdd: (String, String) -> Unit
) {
    var t1 by remember { mutableStateOf("") }
    var t2 by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = t1,
            onValueChange = { t1 = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(hint1) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = t2,
            onValueChange = { t2 = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(hint2) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium
        )

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                val a = t1.trim()
                val b = t2.trim()
                if (a.isNotEmpty()) {
                    onAdd(a, b)
                    t1 = ""
                    t2 = ""
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) { Text("افزودن") }

        Spacer(Modifier.height(16.dp))

        LazyColumn(Modifier.fillMaxSize()) {
            items(items.reversed()) { row ->
                ListItem(
                    headlineContent = { Text(row) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
