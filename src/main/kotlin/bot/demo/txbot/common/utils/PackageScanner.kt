package bot.demo.txbot.common.utils

import java.io.File
import java.io.IOException
import java.net.JarURLConnection
import java.net.URISyntaxException
import java.net.URL
import java.util.*


/**
 * @description: 包扫描工具类
 * @author Nature Zero
 * @date 2024/8/27 22:17
 */
abstract class PackageScanner {
    // scanPackage方法的重载
    fun scanPackage(klass: Class<*>) {
        scanPackage(klass.getPackage().name)
    }

    fun scanPackage(packageName: String) {
        // 将包名称转换为路径名称的形式
        val packagePath = packageName.replace(".", "/")

        try {
            // 由类加载器得到URL的枚举
            val resources = Thread.currentThread()
                .contextClassLoader
                .getResources(packagePath)

            while (resources.hasMoreElements()) {
                val url = resources.nextElement()

                // 处理jar包
                if (url.protocol == "jar") {
                    parse(url)
                } else {
                    val file = File(url.toURI())

                    if (file.exists()) {
                        // 处理普通包
                        parse(file, packageName)
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: URISyntaxException) {
            e.printStackTrace()
        }
    }

    // 抽象方法，由用户自行处理扫描到的类
    abstract fun dealClass(klass: Class<*>)

    // jar包的扫描
    @Throws(IOException::class)
    private fun parse(url: URL) {
        val jarEntries = (url.openConnection() as JarURLConnection)
            .jarFile.entries()

        while (jarEntries.hasMoreElements()) {
            val jarEntry = jarEntries.nextElement()
            val jarName = jarEntry.name

            if (!jarEntry.isDirectory && jarName.endsWith(".class")) {
                // 将文件路径名转换为包名称的形式
                dealClassName(jarName.replace("/", ".").replace(".class", ""))
            }
        }
    }

    // 普通包的扫描
    private fun parse(curFile: File, packageName: String) {
        val fileList = curFile.listFiles { pathname ->
            // 筛选文件夹和class文件，其余文件不处理
            pathname.isDirectory || pathname.name.endsWith(".class")
        }

        // 目录就是一颗树，对树进行递归，找到class文件
        fileList?.forEach { file ->
            val fileName = file.name
            if (file.isDirectory) parse(file, "$packageName.$fileName")
            else {
                val className = packageName + "." + fileName.replace(".class", "")
                dealClassName(className)
            }

        }
    }

    // 将找到的class文件生成类对象
    private fun dealClassName(className: String) {
        try {
            val klass = Class.forName(className)

            // 注解、接口、枚举、原始类型不做处理
            if (!klass.isAnnotation
                && !klass.isInterface
                && !klass.isEnum
                && !klass.isPrimitive
            ) {
                dealClass(klass)
            }
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
        }
    }
}