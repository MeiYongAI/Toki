package io.github.meiyongai.toki.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import io.github.meiyongai.toki.ui.component.stableDialogHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.meiyongai.toki.R
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 开发者赞助渠道详情弹窗。
 *
 * 展示 Ko-fi 链接、支付宝收款码及 USDT (TRC20) 地址，提供跳转、原图保存及复制功能。
 *
 * @param onDismiss 弹窗关闭回调。
 * @return Unit。
 *
 * Callers:
 * - `io.github.meiyongai.toki.ui.screen.HomeScreen`: 首页点击「支持开发」卡片时弹出。
 */
@Composable
fun SponsorDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val strings = context.resources
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    val saveImage = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
        if (uri != null) scope.launch {
            saving = true
            exportMessage = null
            try {
                withContext(Dispatchers.IO) {
                    exportOriginalImage(
                        openInput = {
                            // Resources 的公开契约支持文件型 drawable；直接读取 JPEG 字节，不解码或重压缩。
                            @SuppressLint("ResourceType")
                            val input = context.resources.openRawResource(R.drawable.alipay)
                            input
                        },
                        openOutput = { context.contentResolver.openOutputStream(uri, "w") }
                    )
                }
                exportMessage = strings.getString(R.string.sponsor_image_saved)
            } catch (error: IOException) {
                Log.e("TokiImageExport", "支付宝图片保存失败", error)
                exportMessage = strings.getString(R.string.sponsor_error_io)
            } catch (error: SecurityException) {
                Log.e("TokiImageExport", "所选图片保存位置不可访问", error)
                exportMessage = strings.getString(R.string.sponsor_error_permission)
            } finally {
                saving = false
            }
        }
    }
    val koFiUrl = "https://ko-fi.com/meiyongai"
    val usdtAddress = "TXoTeZLpbQdn4wZF51858bC3zCwS822HbB"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(strings.getString(R.string.sponsor_title))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .stableDialogHeight(520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Ko-fi
                Text(
                    text = strings.getString(R.string.sponsor_kofi),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ko-fi.com/meiyongai",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(koFiUrl)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    ) {
                        Text(strings.getString(R.string.common_open))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // 支付宝
                Text(
                    text = strings.getString(R.string.sponsor_alipay),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Image(
                    painter = painterResource(R.drawable.alipay),
                    contentDescription = strings.getString(R.string.sponsor_alipay_qr),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )

                TextButton(
                    enabled = !saving,
                    onClick = { saveImage.launch("Toki-Alipay.jpg") },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(if (saving) strings.getString(R.string.sponsor_saving) else strings.getString(R.string.sponsor_save_image))
                }
                exportMessage?.let { message ->
                    Text(message, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = strings.getString(R.string.sponsor_image_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // USDT
                Text(
                    text = "USDT (TRC20 / Tron)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = usdtAddress,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard?.setPrimaryClip(
                                ClipData.newPlainText("USDT_TRC20", usdtAddress)
                            )
                            Toast.makeText(context, strings.getString(R.string.sponsor_address_copied), Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text(strings.getString(R.string.common_copy))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.getString(R.string.common_close))
            }
        }
    )
}
