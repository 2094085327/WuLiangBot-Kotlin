package bot.wuliang.utils

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.awt.image.BufferedImage
import java.io.OutputStream
import javax.imageio.ImageIO

/**
 * 二维码生成工具类
 */
object QrCodeUtils {
    private const val CHARSET = "utf-8"
    private const val DEFAULT_FORMAT = "JPG"
    private const val DEFAULT_QRCODE_SIZE = 300 // 二维码尺寸

    /**
     * 生成二维码图片
     * @param content 二维码内容
     * @param size 二维码尺寸，默认300
     * @param errorCorrectionLevel 错误修正级别，默认为H（高级）
     * @return BufferedImage 图片对象
     */
    @Throws(Exception::class)
    fun createImage(
        content: String,
        size: Int = DEFAULT_QRCODE_SIZE,
        errorCorrectionLevel: ErrorCorrectionLevel = ErrorCorrectionLevel.H
    ): BufferedImage {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to errorCorrectionLevel, // 错误修正级别
            EncodeHintType.CHARACTER_SET to CHARSET,                 // 字符集
            EncodeHintType.MARGIN to 1                               // 边距
        )

        // 创建二维码矩阵
        val bitMatrix: BitMatrix = MultiFormatWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            size,
            size,
            hints
        )

        val width = bitMatrix.width
        val height = bitMatrix.height
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

        // 将矩阵数据转化为图片
        for (x in 0 until width) {
            for (y in 0 until height) {
                image.setRGB(x, y, if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        return image
    }

    /**
     * 将二维码写入输出流
     * @param content 二维码内容
     * @param output 输出流
     * @param format 图片格式，默认JPG
     * @param size 二维码尺寸，默认300
     * @param errorCorrectionLevel 错误修正级别，默认为H（高级）
     */
    @Throws(Exception::class)
    fun encode(
        content: String,
        output: OutputStream,
        format: String = DEFAULT_FORMAT,
        size: Int = DEFAULT_QRCODE_SIZE,
        errorCorrectionLevel: ErrorCorrectionLevel = ErrorCorrectionLevel.H
    ) {
        val image = createImage(content, size, errorCorrectionLevel)
        ImageIO.write(image, format, output)
    }

    /**
     * 生成二维码图片（使用默认参数）
     * @param content 二维码内容
     * @return BufferedImage 图片对象
     */
    @Throws(Exception::class)
    fun createImage(content: String): BufferedImage {
        return createImage(content, DEFAULT_QRCODE_SIZE, ErrorCorrectionLevel.H)
    }

    /**
     * 将二维码写入输出流（使用默认参数）
     * @param content 二维码内容
     * @param output 输出流
     */
    @Throws(Exception::class)
    fun encode(content: String, output: OutputStream) {
        encode(content, output, DEFAULT_FORMAT, DEFAULT_QRCODE_SIZE, ErrorCorrectionLevel.H)
    }
}
