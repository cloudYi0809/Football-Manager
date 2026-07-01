package com.greendynasty.football.data.importer.manifest

import java.io.InputStream
import java.security.MessageDigest

/**
 * SHA-256 校验工具
 *
 * 用于校验数据包文件完整性，防止传输损坏或被篡改。
 * 流式计算，支持大文件（不会一次性载入内存）。
 */
object ChecksumUtil {

    private const val BUFFER_SIZE = 8192

    /**
     * 计算输入流的 SHA-256 哈希值。
     * 流式读取，计算完成后会关闭输入流。
     *
     * @param inputStream 待计算的数据流
     * @return 小写十六进制 SHA-256 字符串（64 位）
     */
    fun calculateSha256(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        inputStream.use { stream ->
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * 校验输入流的 SHA-256 是否匹配预期值。
     *
     * @param inputStream 待校验的数据流（将被消耗并关闭）
     * @param expected 预期哈希值（可带 "sha256:" 前缀，大小写不敏感）
     * @return true 表示匹配
     */
    fun verifyChecksum(inputStream: InputStream, expected: String): Boolean {
        val actual = calculateSha256(inputStream)
        val normalizedExpected = expected.removePrefix("sha256:").trim().lowercase()
        return actual.equals(normalizedExpected, ignoreCase = true)
    }
}
