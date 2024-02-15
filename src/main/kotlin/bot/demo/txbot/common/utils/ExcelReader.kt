package bot.demo.txbot.common.utils

import bot.demo.txbot.game.lifeRestart.AgeDataVO
import bot.demo.txbot.game.lifeRestart.ExcelDataVO
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.instrument.Instrumentation
import java.text.DecimalFormat
import java.util.logging.Logger


/**
 * @description: Excel读取解析类
 * @author Nature Zero
 * @date 2024/2/14 20:12
 */
class ExcelReader {
    private val logger: Logger = Logger.getLogger(ExcelReader::class.java.getName()) // 日志打印类


    private val XLS = "xls"
    private val XLSX = "xlsx"

    /**
     * 根据文件后缀名类型获取对应的工作簿对象
     * @param inputStream 读取文件的输入流
     * @param fileType 文件后缀名类型（xls或xlsx）
     * @return 包含文件数据的工作簿对象
     * @throws IOException
     */
    @Throws(IOException::class)
    fun getWorkbook(inputStream: InputStream?, fileType: String): Workbook? {
        var workbook: Workbook? = null
        if (fileType.equals(XLS, ignoreCase = true)) {
            workbook = HSSFWorkbook(inputStream)
        } else if (fileType.equals(XLSX, ignoreCase = true)) {
            workbook = XSSFWorkbook(inputStream)
        }
        return workbook
    }

    /**
     * 读取Excel文件内容
     * @param fileName 要读取的Excel文件所在路径
     * @return 读取结果列表，读取失败时返回null
     */
    fun readExcel(fileName: String, type: String): Any? {
        var workbook: Workbook? = null
        var inputStream: FileInputStream? = null

        // 获取Excel后缀名
        val fileType = fileName.substring(fileName.lastIndexOf(".") + 1, fileName.length)
        // 获取Excel文件
        val excelFile = File(fileName)
        if (!excelFile.exists()) {
            logger.warning("指定的Excel文件不存在！")
            return null
//                 null
        }

        // 获取Excel工作簿
        inputStream = FileInputStream(excelFile)
        workbook = getWorkbook(inputStream, fileType)

        // 读取excel中的数据
       return  when (type) {
            "event" -> parseExcel(workbook) { convertRowToData(it) }
            "age" -> parseExcel(workbook) { convertRowToAgeData(it) }
            else -> null
        }

//        return try  {
//            // 获取Excel后缀名
//            val fileType = fileName.substring(fileName.lastIndexOf(".") + 1, fileName.length)
//            // 获取Excel文件
//            val excelFile = File(fileName)
//            if (!excelFile.exists()) {
//                logger.warning("指定的Excel文件不存在！")
//                return null
////                 null
//            }
//
//            // 获取Excel工作簿
//            inputStream = FileInputStream(excelFile)
//            workbook = getWorkbook(inputStream, fileType)
//
//            // 读取excel中的数据
//            when (type) {
//                "event" -> parseEventExcel(workbook)
//                "age" -> parseAgeExcel(workbook)
//                else -> null
//            }
//        } catch (e: Exception) {
//            e.message
//            println(e)
//            logger.warning("解析Excel失败，文件名：" + fileName + " 错误信息：" + e.message)
////            null
//        } finally {
//            try {
//                workbook?.close()
//                inputStream?.close()
//            } catch (e: Exception) {
//                logger.warning("关闭数据流出错！错误信息：" + e.message)
//                return null
//            }
//        }
    }

    /**
     * 解析Excel数据
     * @param workbook Excel工作簿对象
     * @return 解析结果
     */
//    private fun parseExcel(workbook: Workbook?): List<ExcelDataVO> {
//        val resultDataList: MutableList<ExcelDataVO> = ArrayList<ExcelDataVO>()
//        // 解析sheet
//        for (sheetNum in 0 until workbook!!.numberOfSheets) {
//            val sheet = workbook.getSheetAt(sheetNum) ?: continue
//
//            // 校验sheet是否合法
//
//            // 获取第一行数据
//            val firstRowNum = sheet.firstRowNum
//            val firstRow = sheet.getRow(firstRowNum)
//            if (null == firstRow) {
//                logger.warning("解析Excel失败，在第一行没有读取到任何数据！")
//            }
//
//            // 解析每一行的数据，构造数据对象
//            val rowStart = firstRowNum + 1
//            val rowEnd = sheet.physicalNumberOfRows
//            for (rowNum in rowStart until rowEnd) {
//                val row = sheet.getRow(rowNum) ?: continue
//                val resultData: ExcelDataVO = convertRowToData(row)
//                if (null == resultData) {
//                    logger.warning(("第 " + row.rowNum).toString() + "行数据不合法，已忽略！")
//                    continue
//                }
//                resultDataList.add(resultData)
//            }
//        }
//        println(resultDataList)
//        return resultDataList
//    }
    private fun <T> parseExcel(workbook: Workbook?, convertRowFunction: (Row) -> T): MutableList<T> {
        val resultDataList = ArrayList<T>()

        // 解析sheet
        for (sheetNum in 0 until workbook!!.numberOfSheets) {
            val sheet = workbook.getSheetAt(sheetNum) ?: continue

            // 校验sheet是否合法

            // 获取第一行数据
            val firstRowNum = sheet.firstRowNum
            val firstRow = sheet.getRow(firstRowNum)
            if (null == firstRow) {
                logger.warning("解析Excel失败，在第一行没有读取到任何数据！")
            }

            // 解析每一行的数据，构造数据对象
            val rowStart = firstRowNum + 2
            val rowEnd = sheet.physicalNumberOfRows
            for (rowNum in rowStart until rowEnd) {
                val row = sheet.getRow(rowNum) ?: continue
                val resultData: T = convertRowFunction(row)
                if (resultData == null) break
                else resultDataList.add(resultData)
            }
        }
        return resultDataList
    }

    /**
     * 解析Excel数据
     * @param workbook Excel工作簿对象
     * @return 解析结果
     */
//    private fun parseEventExcel(workbook: Workbook?): MutableList<ExcelDataVO> {
//        val resultDataList = ArrayList<ExcelDataVO>()
//        // 解析sheet
//        for (sheetNum in 0 until workbook!!.numberOfSheets) {
//            val sheet = workbook.getSheetAt(sheetNum) ?: continue
//
//            // 校验sheet是否合法
//
//            // 获取第一行数据
//            val firstRowNum = sheet.firstRowNum
//            val firstRow = sheet.getRow(firstRowNum)
//            if (null == firstRow) {
//                logger.warning("解析Excel失败，在第一行没有读取到任何数据！")
//            }
//
//            // 解析每一行的数据，构造数据对象
//            val rowStart = firstRowNum + 2
//            val rowEnd = sheet.physicalNumberOfRows
//            for (rowNum in rowStart until rowEnd) {
//                val row = sheet.getRow(rowNum) ?: continue
//                val resultData: ExcelDataVO = convertRowToData(row)
//                if (resultData.id == null) break
//                else resultDataList.add(resultData)
//
//            }
//        }
//        return resultDataList
//    }
//
//    /**
//     * 解析Excel数据
//     * @param workbook Excel工作簿对象
//     * @return 解析结果
//     */
//    private fun parseAgeExcel(workbook: Workbook?): MutableList<AgeDataVO> {
//        val resultDataList = ArrayList<AgeDataVO>()
//        // 解析sheet
//        for (sheetNum in 0 until workbook!!.numberOfSheets) {
//            val sheet = workbook.getSheetAt(sheetNum) ?: continue
//
//            // 校验sheet是否合法
//
//            // 获取第一行数据
//            val firstRowNum = sheet.firstRowNum
//            val firstRow = sheet.getRow(firstRowNum)
//            if (null == firstRow) {
//                logger.warning("解析Excel失败，在第一行没有读取到任何数据！")
//            }
//
//            // 解析每一行的数据，构造数据对象
//            val rowStart = firstRowNum + 2
//            val rowEnd = sheet.physicalNumberOfRows
//            for (rowNum in rowStart until rowEnd) {
//                val row = sheet.getRow(rowNum) ?: continue
//                val resultData: AgeDataVO = convertRowToAgeData(row)
//                if (resultData.age == null) break
//                else resultDataList.add(resultData)
//            }
//        }
//        return resultDataList
//    }

    /**
     * 将单元格内容转换为字符串
     * @param cell
     * @return
     */
    private fun convertCellValueToString(cell: Cell?): String? {
        if (cell == null) {
            return null
        }
        var returnValue: String? = null
        when (cell.cellType) {
            CellType.NUMERIC -> {
                val doubleValue: Double = cell.numericCellValue

                // 格式化科学计数法，取一位整数
                val df = DecimalFormat("0")
                returnValue = df.format(doubleValue)
            }

            CellType.STRING -> returnValue = cell.stringCellValue
            CellType.BOOLEAN -> {
                val booleanValue: Boolean = cell.booleanCellValue
                returnValue = booleanValue.toString()
            }

            CellType.BLANK -> {}
            CellType.FORMULA -> returnValue = cell.cellFormula
            CellType.ERROR -> {}
            else -> {}
        }
        return returnValue
    }

    /**
     * 提取每一行中需要的数据，构造成为一个结果数据对象
     *
     * 当该行中有单元格的数据为空或不合法时，忽略该行的数据
     *
     * @param row 行数据
     * @return 解析后的行数据对象，行数据错误时返回null
     */
    private fun convertRowToData(row: Row): ExcelDataVO {
        val resultData = ExcelDataVO()
        var cellNum = 0
        // id
        resultData.id = convertCellValueToString(row.getCell(cellNum++))
        println(resultData.id)
        resultData.event = convertCellValueToString(row.getCell(cellNum++))
        resultData.grade = convertCellValueToString(row.getCell(cellNum++))?.toInt()
        resultData.postEvent = convertCellValueToString(row.getCell(cellNum++))
        resultData.effectChr = convertCellValueToString(row.getCell(cellNum++))?.toInt()
        resultData.effectInt = convertCellValueToString(row.getCell(cellNum++))?.toInt()
        resultData.effectStr = convertCellValueToString(row.getCell(cellNum++))?.toInt()
        resultData.effectMny = convertCellValueToString(row.getCell(cellNum++))?.toInt()
        resultData.effectSpr = convertCellValueToString(row.getCell(cellNum++))?.toInt()
        resultData.effectLif = convertCellValueToString(row.getCell(cellNum++))?.toInt()
        resultData.effectAge = convertCellValueToString(row.getCell(cellNum++))?.toInt()
        resultData.noRandom = convertCellValueToString(row.getCell(cellNum++))?.toInt()
        resultData.include = convertCellValueToString(row.getCell(cellNum++))
        resultData.exclude = convertCellValueToString(row.getCell(cellNum++))
        resultData.branch = mutableListOf(
            convertCellValueToString(row.getCell(cellNum++)),
            convertCellValueToString(row.getCell(cellNum++)),
            convertCellValueToString(row.getCell(cellNum++))
        )
        return resultData
    }


    /**
     * 提取每一行中需要的数据，构造成为一个结果数据对象
     *
     * 当该行中有单元格的数据为空或不合法时，忽略该行的数据
     *
     * @param row 行数据
     * @return 解析后的行数据对象，行数据错误时返回null
     */
    private fun convertRowToAgeData(row: Row): AgeDataVO {
        val resultData = AgeDataVO()
        // id
        if (row.firstCellNum >= 0) {

        resultData.age = convertCellValueToString(row.getCell(row.firstCellNum.toInt()))?.toInt()
        println(resultData.age)
            var eventList = mutableListOf<String?>()
//        if (resultData.age == null) return resultData
            for (cellNum in row.firstCellNum until row.lastCellNum) {
                if (row.getCell(cellNum) != null && cellNum >= 0) {
                    println("cellNum:$cellNum,rowNum:${row.rowNum}")
                    println("cellNum0:${convertCellValueToString(row.getCell(0))}")
                    println("cellNum6:${convertCellValueToString(row.getCell(6))}")
                    eventList.add(convertCellValueToString(row.getCell(cellNum)))
                }
            }
            resultData.eventList=eventList

        }
        return resultData
    }


}