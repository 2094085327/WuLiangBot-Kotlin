package bot.wuliang.excel

import bot.wuliang.botLog.logUtil.LoggerUtils.logError
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
import java.text.DecimalFormat


/**
 * @description: Excel读取解析类
 * @author Nature Zero
 * @date 2024/2/14 20:12
 */
@Suppress("UNUSED")
class ExcelReader {

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
        if (fileType.equals("xls", ignoreCase = true)) {
            workbook = HSSFWorkbook(inputStream)
        } else if (fileType.equals("xlsx", ignoreCase = true)) {
            workbook = XSSFWorkbook(inputStream)
        }
        return workbook
    }

    /**
     * 读取Excel文件内容
     * @param fileName 要读取的Excel文件所在路径
     * @return 读取结果列表，读取失败时返回null
     */
    fun readExcel(fileName: String): Workbook? {
        val workbook: Workbook?
        val inputStream: FileInputStream?

        // 获取Excel后缀名
        val fileType = fileName.substring(fileName.lastIndexOf(".") + 1, fileName.length)
        // 获取Excel文件
        val excelFile = File(fileName)
        if (!excelFile.exists()) {
            logError("指定的Excel文件:${fileName}不存在！")
            return null
        }

        // 获取Excel工作簿
        inputStream = FileInputStream(excelFile)
        workbook = getWorkbook(inputStream, fileType)

        return workbook
    }

    /**
     *解析Excel数据
     *
     * @param T
     * @param workbook Excel工作簿对象
     * @param convertRowFunction 转换函数
     * @return 解析结果
     */
    private fun <T> parseExcel(workbook: Workbook?, convertRowFunction: (Row) -> T?): MutableList<T> {
        val resultDataList = ArrayList<T>()

        // 解析sheet
        for (sheetNum in 0 until workbook!!.numberOfSheets) {
            val sheet = workbook.getSheetAt(sheetNum) ?: continue

            // 校验sheet是否合法

            // 获取第一行数据
            val firstRowNum = sheet.firstRowNum
            val firstRow = sheet.getRow(firstRowNum)
            if (null == firstRow) {
                logError("解析Excel失败，在第一行没有读取到任何数据！")
            }

            // 解析每一行的数据，构造数据对象
            val rowStart = firstRowNum + 2
            val rowEnd = sheet.physicalNumberOfRows
            for (rowNum in rowStart until rowEnd) {
                val row = sheet.getRow(rowNum) ?: continue
                val resultData: T? = convertRowFunction(row)
                if (resultData == null) break
                else resultDataList.add(resultData)
            }
        }
        return resultDataList
    }

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


}