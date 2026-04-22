package bot.wuliang.adapter.message

sealed class ImageSource {
    /**
     * 图片的 URL
     */
    data class Url(val url: String) : ImageSource()

    /**
     * 图片的 本地文件路径
     */
    data class File(val path: String) : ImageSource()

    /**
     * 图片的 二进制数据
     */
    data class Bytes(val bytes: ByteArray) : ImageSource() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Bytes

            return bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int {
            return bytes.contentHashCode()
        }
    }
}