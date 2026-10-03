package io.github.meiyongai.toki.ui

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * 原样复制图片并关闭两个流；只有写入和关闭均成功才确认完成。
 * @param openInput 打开图片原始资源的函数。
 * @param openOutput 打开用户选择目标的函数；空流表示目标提供器无法写入。
 * @return 完整复制的字节数；读取、写入、权限和关闭错误交由调用界面明确报告。
 * Callers: SponsorDialog 的文档选择回调、OriginalImageExportTest。
 */
internal fun exportOriginalImage(openInput: () -> InputStream, openOutput: () -> OutputStream?): Long =
    openInput().use { input ->
        (openOutput() ?: throw IOException("无法打开所选保存位置")).use { output -> input.copyTo(output) }
    }
