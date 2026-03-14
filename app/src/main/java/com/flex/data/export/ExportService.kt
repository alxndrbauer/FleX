package com.flex.data.export

import android.content.ContentResolver
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.flex.domain.model.DayType
import com.flex.domain.model.ExportData
import com.flex.domain.model.WorkLocation
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportService @Inject constructor() {

    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dayNameFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN)
    private val dayNameShortFormatter = DateTimeFormatter.ofPattern("EEE", Locale.GERMAN)
    private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)

    // region CSV

    fun exportToCsv(data: ExportData, uri: Uri, contentResolver: ContentResolver) {
        val sb = StringBuilder()
        sb.append('\uFEFF') // UTF-8 BOM for Excel compatibility

        sb.append("Datum;Tag;Typ;Ort;Start;Ende;Brutto;Pause;Netto;Soll;Differenz;Notiz\n")

        for (row in data.rows) {
            val dayName = row.date.format(dayNameFormatter)
            val typeLabel = row.dayType?.let { dayTypeLabel(it) } ?: "-"
            val locationLabel = row.location?.let { locationLabel(it) } ?: "-"
            val startStr = row.startTime?.format(timeFormatter) ?: "-"
            val endStr = row.endTime?.format(timeFormatter) ?: "-"
            val grossStr = if (row.grossMinutes > 0) formatDuration(row.grossMinutes) else "-"
            val breakStr = if (row.grossMinutes > 0) formatDuration(row.breakMinutes) else "-"
            val netStr = if (row.netMinutes > 0) formatDuration(row.netMinutes) else "-"
            val targetStr = if (row.targetMinutes > 0) formatDuration(row.targetMinutes.toLong()) else "-"
            val diff = row.netMinutes - row.targetMinutes
            val diffStr = if (row.targetMinutes > 0 || row.netMinutes > 0) {
                val sign = if (diff >= 0) "+" else ""
                "$sign${formatDuration(kotlin.math.abs(diff))}"
            } else "-"
            val noteStr = row.note?.replace(";", ",") ?: ""

            sb.append("${row.date.format(dateFormatter)};$dayName;$typeLabel;$locationLabel;$startStr;$endStr;$grossStr;$breakStr;$netStr;$targetStr;$diffStr;$noteStr\n")
        }

        // Summary row
        val totalDiff = data.totalNetMinutes - data.totalTargetMinutes
        val totalDiffSign = if (totalDiff >= 0) "+" else ""
        val totalDiffStr = "$totalDiffSign${formatDuration(kotlin.math.abs(totalDiff))}"
        sb.append(";;;;;;;Gesamt;;${formatDuration(data.totalNetMinutes)};${formatDuration(data.totalTargetMinutes)};$totalDiffStr;\n")

        contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))
        }
    }

    // endregion

    // region PDF

    fun exportToPdf(data: ExportData, uri: Uri, contentResolver: ContentResolver) {
        val pageWidth = 842   // A4 landscape width (pt at 72 dpi)
        val pageHeight = 595  // A4 landscape height
        val margin = 30f
        val contentWidth = pageWidth - 2 * margin

        // Column definitions: label to width
        val columns = buildColumns(contentWidth)

        val rowHeight = 13f
        val headerRowHeight = 15f

        val titlePaint = paint(12f, Color.BLACK, bold = true)
        val colHeaderPaint = paint(7.5f, Color.BLACK, bold = true)
        val cellPaint = paint(7f, Color.BLACK)
        val summaryPaint = paint(7.5f, Color.BLACK, bold = true)
        val footerPaint = paint(7f, Color.DKGRAY)
        val grayFill = fillPaint(Color.rgb(235, 235, 235))
        val headerFill = fillPaint(Color.rgb(210, 210, 210))
        val linePaint = strokePaint(Color.rgb(180, 180, 180), 0.5f)

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        var y = margin

        // Title
        canvas.drawText(
            "Monatsbericht ${data.yearMonth.format(monthFormatter)}",
            margin, y + 12f, titlePaint
        )
        y += 20f

        // Column headers
        drawRow(canvas, columns, margin, y, headerRowHeight, headerFill, linePaint) {
            columns.forEachIndexed { i, (label, _) ->
                val x = columnX(columns, i, margin)
                canvas.drawText(label, x + 2f, y + 10f, colHeaderPaint)
            }
        }
        y += headerRowHeight

        // Data rows
        data.rows.forEachIndexed { index, row ->
            val fill = if (index % 2 == 0) null else grayFill
            drawRow(canvas, columns, margin, y, rowHeight, fill, linePaint) {
                val values = rowValues(row)
                values.forEachIndexed { i, value ->
                    val x = columnX(columns, i, margin)
                    canvas.drawText(value, x + 2f, y + 9f, cellPaint)
                }
            }
            y += rowHeight
        }

        // Summary row
        y += 3f
        val totalDiff = data.totalNetMinutes - data.totalTargetMinutes
        val totalDiffSign = if (totalDiff >= 0) "+" else ""
        val totalDiffStr = "$totalDiffSign${formatDuration(kotlin.math.abs(totalDiff))}"

        drawRow(canvas, columns, margin, y, rowHeight, headerFill, linePaint) {
            canvas.drawText("Gesamt", margin + 2f, y + 9f, summaryPaint)
            val netIdx = 8
            val targetIdx = 9
            val diffIdx = 10
            canvas.drawText(formatDuration(data.totalNetMinutes), columnX(columns, netIdx, margin) + 2f, y + 9f, summaryPaint)
            canvas.drawText(formatDuration(data.totalTargetMinutes), columnX(columns, targetIdx, margin) + 2f, y + 9f, summaryPaint)
            canvas.drawText(totalDiffStr, columnX(columns, diffIdx, margin) + 2f, y + 9f, summaryPaint)
        }
        y += rowHeight

        // Footer
        val today = LocalDate.now().format(dateFormatter)
        canvas.drawText(
            "Erstellt am $today mit FleX",
            margin, (pageHeight - 12).toFloat(), footerPaint
        )

        pdfDocument.finishPage(page)
        contentResolver.openOutputStream(uri)?.use { pdfDocument.writeTo(it) }
        pdfDocument.close()
    }

    private fun buildColumns(contentWidth: Float): List<Pair<String, Float>> {
        val fixed = listOf(
            "Datum" to 64f,
            "Tag" to 44f,
            "Typ" to 64f,
            "Ort" to 40f,
            "Start" to 36f,
            "Ende" to 36f,
            "Brutto" to 38f,
            "Pause" to 38f,
            "Netto" to 38f,
            "Soll" to 38f,
            "Diff" to 50f
        )
        val usedWidth = fixed.sumOf { it.second.toDouble() }.toFloat()
        val noteWidth = contentWidth - usedWidth
        return fixed + listOf("Notiz" to noteWidth)
    }

    private fun columnX(columns: List<Pair<String, Float>>, index: Int, startX: Float): Float {
        return startX + columns.take(index).sumOf { it.second.toDouble() }.toFloat()
    }

    private fun drawRow(
        canvas: Canvas,
        columns: List<Pair<String, Float>>,
        startX: Float,
        y: Float,
        height: Float,
        fill: Paint?,
        border: Paint,
        drawContent: () -> Unit
    ) {
        val totalWidth = columns.sumOf { it.second.toDouble() }.toFloat()
        if (fill != null) {
            canvas.drawRect(startX, y, startX + totalWidth, y + height, fill)
        }
        drawContent()
        canvas.drawRect(startX, y, startX + totalWidth, y + height, border)
    }

    private fun rowValues(row: com.flex.domain.model.ExportDayRow): List<String> {
        val dayName = row.date.format(dayNameShortFormatter)
        val typeLabel = row.dayType?.let { dayTypeLabel(it) } ?: "-"
        val locationLabel = row.location?.let { locationLabel(it) } ?: "-"
        val startStr = row.startTime?.format(timeFormatter) ?: "-"
        val endStr = row.endTime?.format(timeFormatter) ?: "-"
        val grossStr = if (row.grossMinutes > 0) formatDuration(row.grossMinutes) else "-"
        val breakStr = if (row.grossMinutes > 0) formatDuration(row.breakMinutes) else "-"
        val netStr = if (row.netMinutes > 0) formatDuration(row.netMinutes) else "-"
        val targetStr = if (row.targetMinutes > 0) formatDuration(row.targetMinutes.toLong()) else "-"
        val diff = row.netMinutes - row.targetMinutes
        val diffStr = if (row.targetMinutes > 0 || row.netMinutes > 0) {
            val sign = if (diff >= 0) "+" else ""
            "$sign${formatDuration(kotlin.math.abs(diff))}"
        } else "-"
        val noteStr = row.note ?: ""
        return listOf(
            row.date.format(dateFormatter), dayName, typeLabel, locationLabel,
            startStr, endStr, grossStr, breakStr, netStr, targetStr, diffStr, noteStr
        )
    }

    // endregion

    // region Helpers

    private fun dayTypeLabel(dayType: DayType): String = when (dayType) {
        DayType.WORK -> "Arbeit"
        DayType.VACATION -> "Urlaub"
        DayType.SPECIAL_VACATION -> "Sonderurlaub"
        DayType.FLEX_DAY -> "Gleittag"
        DayType.SATURDAY_BONUS -> "Samstag+"
    }

    private fun locationLabel(location: WorkLocation): String = when (location) {
        WorkLocation.OFFICE -> "Büro"
        WorkLocation.HOME_OFFICE -> "HO"
    }

    private fun formatDuration(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return "$h:${m.toString().padStart(2, '0')}"
    }

    private fun paint(textSize: Float, color: Int, bold: Boolean = false): Paint = Paint().apply {
        this.textSize = textSize
        this.color = color
        this.isAntiAlias = true
        if (bold) this.typeface = Typeface.DEFAULT_BOLD
    }

    private fun fillPaint(color: Int): Paint = Paint().apply {
        this.color = color
        this.style = Paint.Style.FILL
    }

    private fun strokePaint(color: Int, strokeWidth: Float): Paint = Paint().apply {
        this.color = color
        this.style = Paint.Style.STROKE
        this.strokeWidth = strokeWidth
    }

    // endregion
}
