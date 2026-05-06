package com.example.coreexcel

import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Row
import java.io.InputStream
import android.net.Uri
import android.content.ContentResolver

data class ExcelRow(val name: String, val anterior: Double, var actual: Double)

class WorkbookManager(val name: String, val uriString: String) {
    var workbook: org.apache.poi.ss.usermodel.Workbook? = null
    var sheetIndex: Int = 0
    val rows: MutableList<ExcelRow> = mutableListOf()

    private fun getNumericValue(cell: org.apache.poi.ss.usermodel.Cell?): Double {
        if (cell == null) return 0.0
        return when (cell.cellType) {
            CellType.NUMERIC -> cell.numericCellValue
            CellType.STRING -> {
                val text = cell.stringCellValue.trim()
                text.toDoubleOrNull() ?: 0.0
            }
            CellType.FORMULA -> {
                try { cell.numericCellValue } catch (e: Exception) { 0.0 }
            }
            CellType.BOOLEAN -> if (cell.booleanCellValue) 1.0 else 0.0
            CellType.BLANK -> 0.0
            else -> 0.0
        }
    }

    private fun getStringValue(cell: org.apache.poi.ss.usermodel.Cell?): String {
        if (cell == null) return ""
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue
            CellType.NUMERIC -> cell.numericCellValue.toString()
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> {
                try { cell.stringCellValue } catch (e: Exception) { "" }
            }
            CellType.BLANK -> ""
            else -> ""
        }
    }

    fun load(input: InputStream) {
        workbook = WorkbookFactory.create(input)
        val sheet = workbook?.getSheetAt(0) ?: return
        rows.clear()
        val rowIterator = sheet.rowIterator()
        if (rowIterator.hasNext()) rowIterator.next() // skip header
        while (rowIterator.hasNext()) {
            val r = rowIterator.next()
            val name = getStringValue(r.getCell(2))
            val anterior = getNumericValue(r.getCell(3))
            val actual = getNumericValue(r.getCell(4))
            rows.add(ExcelRow(name, anterior, actual))
        }
    }

    fun loadFromUri(resolver: ContentResolver, uri: Uri) {
        resolver.openInputStream(uri)?.use { load(it) }
    }

    fun updateActualForRow(rowIndex: Int, actualValue: Double) {
        if (rowIndex in rows.indices) {
            rows[rowIndex].actual = actualValue
        }
    }

    fun consumptionForRow(rowIndex: Int): Double {
        val r = rows.getOrNull(rowIndex) ?: return 0.0
        return r.actual - r.anterior
    }

    fun save(resolver: ContentResolver, uri: Uri): Boolean {
        val sheet = workbook?.getSheetAt(sheetIndex) ?: return false
        for (i in rows.indices) {
            val row = sheet.getRow(i + 1) ?: sheet.createRow(i + 1)
            val cellActual = row.getCell(4) ?: row.createCell(4)
            cellActual.setCellValue(rows[i].actual)
        }
        resolver.openOutputStream(uri)?.use { out -> workbook?.write(out) }
        return true
    }
}
