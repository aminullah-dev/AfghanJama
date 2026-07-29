@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.afghanjama.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.afghanjama.data.entities.OrderPhoto
import com.afghanjama.ui.format.fa
import com.afghanjama.util.PhotoStore

/** کدِ کوچکِ اجازهٔ دوربین — عمداً زیرِ ۱۶ بیت. */
private const val REQ_CAMERA = 1102

/**
 * نوارِ عکس‌های یک سفارش: نمایش، افزودن (دوربین یا گالری) و حذف.
 *
 * خیاطی کارِ چشمی است — عکسِ طرح یا نمونه‌ای که مشتری آورده، بیشتر از
 * هر توضیحی به برشکار و خیاط کمک می‌کند.
 */
@Composable
fun OrderPhotoStrip(
    photos: List<OrderPhoto>,
    canEdit: Boolean,
    onCaptured: (fileName: String) -> Unit,
    onDelete: (OrderPhoto) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "عکس‌های سفارش"
) {
    val context = LocalContext.current
    var viewing by remember { mutableStateOf<OrderPhoto?>(null) }
    var pendingName by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }

    // دوربین: فایل را از قبل می‌سازیم و URIاش را به اپِ دوربین می‌دهیم
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        val name = pendingName
        pendingName = null
        if (ok && name != null && PhotoStore.exists(context, name)) {
            PhotoStore.shrinkInPlace(context, name)
            onCaptured(name)
        }
        else if (name != null) {
            PhotoStore.delete(context, name)      // عکسِ نیمه‌کاره نماند
            if (!ok) note = "عکسی گرفته نشد."
        }
    }

    // گالری: هیچ اجازه‌ای لازم ندارد، در همهٔ نسخه‌های اندروید
    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = PhotoStore.newFileName()
        val copied = PhotoStore.copyFrom(context, uri, name)
        if (copied) onCaptured(name) else {
            PhotoStore.delete(context, name)
            note = "عکس کپی نشد."
        }
    }

    fun openCamera() {
        // اجازهٔ دوربین با کدِ کوچک درخواست می‌شود؛ درخواستِ آن از مسیرِ
        // ActivityResult روی این اکتیویتی قبلاً باعثِ کرش شده بود.
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            (context as? android.app.Activity)?.let {
                ActivityCompat.requestPermissions(
                    it, arrayOf(Manifest.permission.CAMERA), REQ_CAMERA
                )
            }
            note = "اجازهٔ دوربین را بدهید و دوباره بزنید. " +
                "یا از «انتخاب از گالری» استفاده کنید."
            return
        }
        val name = PhotoStore.newFileName()
        pendingName = name
        runCatching { cameraLauncher.launch(PhotoStore.uriFor(context, name)) }
            .onFailure {
                pendingName = null
                PhotoStore.delete(context, name)
                note = "دوربین باز نشد؛ از گالری انتخاب کنید."
            }
    }

    // ---- نمایشِ بزرگ ----
    viewing?.let { p ->
        AlertDialog(
            onDismissRequest = { viewing = null },
            title = { Text(p.orderCode) },
            text = {
                PhotoImage(
                    fileName = p.fileName,
                    maxSide = PhotoStore.MAX_SIDE,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(320.dp)
                )
            },
            confirmButton = { TextButton(onClick = { viewing = null }) { Text("بستن") } },
            dismissButton = if (canEdit) {
                {
                    TextButton(
                        onClick = { onDelete(p); viewing = null }
                    ) {
                        Text("حذف عکس", color = MaterialTheme.colorScheme.error)
                    }
                }
            } else null
        )
    }

    note?.let { msg ->
        AlertDialog(
            onDismissRequest = { note = null },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { note = null }) { Text("باشه") } }
        )
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (photos.isEmpty()) title else "$title (${photos.size.fa()})",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )

        if (photos.isEmpty()) {
            Text(
                "عکسی ثبت نشده — عکسِ طرح یا نمونهٔ مشتری در برش و دوخت هم دیده می‌شود.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(photos, key = { it.id }) { p ->
                    Box {
                        PhotoImage(
                            fileName = p.fileName,
                            maxSide = PhotoStore.THUMB_SIDE,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { viewing = p }
                        )
                        if (canEdit) {
                            IconButton(
                                onClick = { onDelete(p) },
                                modifier = Modifier.align(Alignment.TopEnd).size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "حذف",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .background(Color(0x99000000), RoundedCornerShape(14.dp))
                                        .padding(3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (canEdit) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { openCamera() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null)
                    Box(Modifier.width(6.dp))
                    Text("دوربین")
                }
                OutlinedButton(
                    onClick = {
                        pickLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Box(Modifier.width(6.dp))
                    Text("گالری")
                }
            }
        }
    }
}

/**
 * عکس را در اندازهٔ لازم می‌خواند — رمزگشایی روی نخِ پس‌زمینه، تا اسکرول
 * فهرست نپرد و عکسِ بزرگ حافظه را نبلعد.
 */
/**
 * یک عکسِ تکی برای کالای انبار: نمایش، گرفتن/انتخاب، و برداشتن.
 *
 * کالا برخلافِ سفارش چند عکس لازم ندارد — یک عکسِ درست کافی است تا فروشنده
 * زودتر از خواندنِ نام بفهمد کدام طرح است.
 */
@Composable
fun SinglePhotoPicker(
    fileName: String,
    canEdit: Boolean,
    onPicked: (String) -> Unit,
    onCleared: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "عکس کالا"
) {
    val context = LocalContext.current
    var note by remember { mutableStateOf<String?>(null) }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = PhotoStore.newFileName()
        if (PhotoStore.copyFrom(context, uri, name)) onPicked(name)
        else {
            PhotoStore.delete(context, name)
            note = "عکس کپی نشد."
        }
    }

    note?.let { msg ->
        AlertDialog(
            onDismissRequest = { note = null },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { note = null }) { Text("باشه") } }
        )
    }

    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (fileName.isNotBlank()) {
            PhotoImage(
                fileName = fileName,
                maxSide = PhotoStore.THUMB_SIDE,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
        if (canEdit) {
            OutlinedButton(
                onClick = {
                    pickLauncher.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                }
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                Box(Modifier.width(6.dp))
                Text(if (fileName.isBlank()) label else "تعویض")
            }
            if (fileName.isNotBlank()) {
                TextButton(onClick = onCleared) { Text("برداشتن") }
            }
        } else if (fileName.isBlank()) {
            Text(
                "عکسی ثبت نشده",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PhotoImage(
    fileName: String,
    maxSide: Int,
    contentScale: ContentScale,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bmp by produceState<android.graphics.Bitmap?>(null, fileName, maxSide) {
        value = withContext(Dispatchers.IO) {
            PhotoStore.decode(PhotoStore.file(context, fileName), maxSide)
        }
    }
    val image = bmp
    if (image != null) {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier
        )
    } else {
        // فایل نیست یا هنوز باز نشده — جای خالی بماند نه کارتِ شکسته
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
