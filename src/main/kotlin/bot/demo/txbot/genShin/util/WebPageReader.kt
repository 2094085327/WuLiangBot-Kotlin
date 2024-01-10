package bot.demo.txbot.genShin.util

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.nio.charset.StandardCharsets


object WebPageReader {
//    @JvmStatic
//    fun main(args: Array<String>) {
//        val url = "https://genshin.honeyhunterworld.com/fam_chars/?lang=CHS"
//        try {
//            val htmlContent = getHTML(url)
//            println(htmlContent)
//        } catch (e: IOException) {
//            e.printStackTrace()
//        }
//    }

    @Throws(IOException::class)
    private fun getHTML(urlString: String): String {
        val htmlContent = StringBuilder()
        val url = URL(urlString)
        val connection = url.openConnection()
        BufferedReader(InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                htmlContent.append(line).append("\n")
            }
        }
        return htmlContent.toString()
    }
}