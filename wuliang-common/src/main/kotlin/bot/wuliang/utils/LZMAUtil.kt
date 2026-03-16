package bot.wuliang.utils

import org.tukaani.xz.LZMAInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

object LZMAUtil {

    fun lzmaDecompress(compressedData: ByteArrayInputStream): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        LZMAInputStream(compressedData).use { xzInputStream ->
            val buffer = ByteArray(8192)
            var len: Int
            while (xzInputStream.read(buffer).also { len = it } != -1) {
                byteArrayOutputStream.write(buffer, 0, len)
            }
        }
        return byteArrayOutputStream.toByteArray()
    }

    fun lzmaDecompress(compressedData: ByteArray): ByteArray {
        val byteArrayInputStream = ByteArrayInputStream(compressedData)
        return lzmaDecompress(byteArrayInputStream)
    }

    fun lzmaDecompress(compressedData: String): String {
        return String(lzmaDecompress(compressedData.toByteArray()))
    }
}