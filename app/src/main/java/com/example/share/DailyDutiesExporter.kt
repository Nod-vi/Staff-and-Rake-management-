package com.example.share

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.model.DutyArea
import com.example.model.ShiftSlotAssignment
import com.example.model.ShiftType
import com.example.model.SlotRoleType
import com.example.model.StaffMember
import com.example.model.isMedDutyActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DutyShiftSheetBlock(
  val title: String,
  val shedStaff: List<String>,
  val stationStaff: List<String>,
  val medStaff: String?,
  val showMedRow: Boolean,
  val pointsmanName: String?,
  val shunterName: String? = null
)

data class GeneralStaffRow(
  val name: String,
  val designation: String
)

data class DailyDutiesSheetData(
  val todayDate: LocalDate,
  val tomorrowDate: LocalDate,
  val headerRangeText: String,
  val shift3Night: DutyShiftSheetBlock,
  val shift1Morning: DutyShiftSheetBlock,
  val shift2Afternoon: DutyShiftSheetBlock,
  val generalHeaderTitle: String,
  val generalStaff: List<GeneralStaffRow>,
  val generalShunterName: String? = null,
  val showShunter: Boolean = false
)

object DailyDutiesExporter {

  private val dateFormatter = DateTimeFormatter.ofPattern("EEEE, dd-MMM-yyyy", Locale.ENGLISH)

  fun buildSheetData(
    todayDate: LocalDate,
    staffList: List<StaffMember>,
    assignments: List<ShiftSlotAssignment>,
    includeShunter: Boolean = false
  ): DailyDutiesSheetData {
    val tomorrowDate = todayDate.plusDays(1)
    val staffById = staffList.associateBy { it.id }

    fun formattedDay(date: LocalDate): String = date.format(dateFormatter).uppercase(Locale.ENGLISH)

    fun extractShiftBlock(date: LocalDate, shiftType: ShiftType, shiftNumberLabel: String): DutyShiftSheetBlock {
      val dateStr = date.toString()
      val items = assignments.filter { it.date == dateStr && it.shiftType == shiftType.name }

      val pointsman = items
        .firstOrNull { it.dutyArea == DutyArea.SHED.name && it.roleType == SlotRoleType.POINTSMAN.name }
        ?.staffName

      val shunter = if (includeShunter) {
        items
          .firstOrNull { it.dutyArea == DutyArea.SHED.name && it.roleType == SlotRoleType.SHUNTER.name }
          ?.staffName
      } else {
        null
      }

      val shedNames = items
        .filter {
          it.dutyArea == DutyArea.SHED.name &&
            it.roleType != SlotRoleType.POINTSMAN.name &&
            it.roleType != SlotRoleType.SHUNTER.name
        }
        .sortedBy { it.slotIndex }
        .map { it.staffName }

      val stationArea = shiftType.stationArea
      val stationNames = if (stationArea != null) {
        items.filter { it.dutyArea == stationArea.name }
          .sortedBy { it.slotIndex }
          .map { it.staffName }
      } else {
        emptyList()
      }

      val showMed = shiftType == ShiftType.SHIFT_22_06 && date.isMedDutyActive()
      val medName = if (showMed) {
        items.firstOrNull { it.dutyArea == DutyArea.STATION_MED.name }?.staffName
      } else {
        null
      }

      return DutyShiftSheetBlock(
        title = "$shiftNumberLabel (${shiftType.timeWindow}) • ${formattedDay(date)}",
        shedStaff = shedNames,
        stationStaff = stationNames,
        medStaff = medName,
        showMedRow = showMed,
        pointsmanName = pointsman,
        shunterName = shunter
      )
    }

    val tomorrowStr = tomorrowDate.toString()
    val generalItems = assignments.filter { it.date == tomorrowStr && it.shiftType == ShiftType.SHIFT_08_17.name }
    val generalShunter = if (includeShunter) {
      generalItems.firstOrNull { it.roleType == SlotRoleType.SHUNTER.name }?.staffName
    } else {
      null
    }
    val generalRows = generalItems
      .filter { it.roleType != SlotRoleType.SHUNTER.name }
      .sortedBy { it.slotIndex }
      .map { a ->
        val desig = staffById[a.staffId]?.designationEnum?.label ?: ""
        GeneralStaffRow(name = a.staffName, designation = desig)
      }

    return DailyDutiesSheetData(
      todayDate = todayDate,
      tomorrowDate = tomorrowDate,
      headerRangeText = "${formattedDay(todayDate)} / ${formattedDay(tomorrowDate)}",
      shift3Night = extractShiftBlock(todayDate, ShiftType.SHIFT_22_06, "SHIFT 3"),
      shift1Morning = extractShiftBlock(tomorrowDate, ShiftType.SHIFT_06_14, "SHIFT 1"),
      shift2Afternoon = extractShiftBlock(tomorrowDate, ShiftType.SHIFT_14_22, "SHIFT 2"),
      generalHeaderTitle = "GENERAL SHIFT (${ShiftType.SHIFT_08_17.timeWindow}) • ${formattedDay(tomorrowDate)}",
      generalStaff = generalRows,
      generalShunterName = generalShunter,
      showShunter = includeShunter
    )
  }

  fun buildPlainTextSummary(sheet: DailyDutiesSheetData): String = buildString {
    appendLine("*DAILY DUTIES*")
    appendLine(sheet.headerRangeText)
    appendLine()
    listOf(sheet.shift3Night, sheet.shift1Morning, sheet.shift2Afternoon).forEach { block ->
      appendLine("*${block.title}*")
      appendLine("Shed: ${if (block.shedStaff.isEmpty()) "Unassigned" else block.shedStaff.joinToString(", ")}")
      appendLine("Station: ${if (block.stationStaff.isEmpty()) "Unassigned" else block.stationStaff.joinToString(", ")}")
      if (block.showMedRow) {
        appendLine("Medchal (MED): ${block.medStaff ?: "Unassigned"}")
      }
      appendLine("Pointsman: ${block.pointsmanName ?: "Unassigned"}")
      if (sheet.showShunter) {
        appendLine("Shunter: ${block.shunterName ?: "Unassigned"}")
      }
      appendLine()
    }
    appendLine("*${sheet.generalHeaderTitle}*")
    if (sheet.generalStaff.isEmpty()) {
      appendLine("General Staff: None assigned (0/3)")
    } else {
      sheet.generalStaff.forEach { row ->
        appendLine("- ${row.name}${if (row.designation.isNotBlank()) " (${row.designation})" else ""}")
      }
    }
    if (sheet.showShunter) {
      appendLine("Shunter: ${sheet.generalShunterName ?: "Unassigned"}")
    }
  }

  suspend fun createPngFile(context: Context, sheet: DailyDutiesSheetData): File = withContext(Dispatchers.IO) {
    val bitmap = renderSheetBitmap(sheet)
    val dir = File(context.cacheDir, "shared_duties").apply { mkdirs() }
    val outFile = File(dir, "PPIO_Daily_Duties_${sheet.todayDate}_to_${sheet.tomorrowDate}.png")
    FileOutputStream(outFile).use { fos ->
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
    }
    bitmap.recycle()
    outFile
  }

  suspend fun createPdfFile(context: Context, sheet: DailyDutiesSheetData): File = withContext(Dispatchers.IO) {
    val bitmap = renderSheetBitmap(sheet)
    val dir = File(context.cacheDir, "shared_duties").apply { mkdirs() }
    val outFile = File(dir, "PPIO_Daily_Duties_${sheet.todayDate}_to_${sheet.tomorrowDate}.pdf")

    val pdf = PdfDocument()
    try {
      val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
      val page = pdf.startPage(pageInfo)
      page.canvas.drawBitmap(bitmap, 0f, 0f, null)
      pdf.finishPage(page)
      FileOutputStream(outFile).use { fos ->
        pdf.writeTo(fos)
      }
    } finally {
      pdf.close()
      bitmap.recycle()
    }
    outFile
  }

  fun getShareUri(context: Context, file: File): Uri {
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
  }

  fun launchWhatsAppShare(context: Context, imageUri: Uri) {
    val directIntent = Intent(Intent.ACTION_SEND).apply {
      type = "image/png"
      putExtra(Intent.EXTRA_STREAM, imageUri)
      clipData = ClipData.newRawUri("Daily Duties Sheet", imageUri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      setPackage("com.whatsapp")
    }
    try {
      context.startActivity(directIntent)
    } catch (_: ActivityNotFoundException) {
      val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, imageUri)
        clipData = ClipData.newRawUri("Daily Duties Sheet", imageUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      context.startActivity(Intent.createChooser(fallbackIntent, "Share Daily Duties Sheet"))
    }
  }

  fun launchFileShare(context: Context, fileUri: Uri, mimeType: String, captionText: String, chooserTitle: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
      type = mimeType
      putExtra(Intent.EXTRA_STREAM, fileUri)
      putExtra(Intent.EXTRA_TEXT, captionText)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
  }

  fun renderSheetBitmap(sheet: DailyDutiesSheetData): Bitmap {
    val width = 1080
    val margin = 36f
    val contentW = width - margin * 2f
    val rowH = 44f
    val bannerH = 54f
    val subBannerH = 40f
    val sectionGap = 24f

    fun shiftRowsCount(block: DutyShiftSheetBlock): Int {
      val rightRows = block.stationStaff.size + if (block.showMedRow) 1 else 0
      return maxOf(4, block.shedStaff.size, rightRows)
    }

    val generalRowsCount = maxOf(2, sheet.generalStaff.size)

    fun blockHeight(block: DutyShiftSheetBlock): Float {
      val footerRows = if (sheet.showShunter) 2 else 1
      return bannerH + rowH + (shiftRowsCount(block) * rowH) + (footerRows * rowH)
    }

    val generalFooterHeight = if (sheet.showShunter) rowH else 0f

    val totalHeight = (
      margin +
        bannerH + subBannerH + sectionGap +
        blockHeight(sheet.shift3Night) + sectionGap +
        blockHeight(sheet.shift1Morning) + sectionGap +
        blockHeight(sheet.shift2Afternoon) + sectionGap +
        (bannerH + rowH + generalRowsCount * rowH + generalFooterHeight) +
        margin
      ).toInt()

    val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)

    val navyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1F3B63") }
    val subHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E4ECF7") }
    val shedHdrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EAF0F8") }
    val stationHdrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#CFEAD9") }
    val stationRowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E5F4EB") }
    val medRowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FADBD1") }
    val pointsmanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FEF3C7") }
    val shunterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E0F2FE") }
    val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#94A3B8")
      style = Paint.Style.STROKE
      strokeWidth = 2f
    }
    val innerGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#CBD5E1")
      style = Paint.Style.STROKE
      strokeWidth = 1.2f
    }

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.WHITE
      textSize = 26f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      textAlign = Paint.Align.CENTER
    }
    val subBannerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#0F172A")
      textSize = 18f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      textAlign = Paint.Align.CENTER
    }
    val shiftBannerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.WHITE
      textSize = 20f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      textAlign = Paint.Align.CENTER
    }
    val colHdrLeftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#1F3B63")
      textSize = 17f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      textAlign = Paint.Align.LEFT
    }
    val colHdrRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#166534")
      textSize = 17f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      textAlign = Paint.Align.RIGHT
    }
    val cellLeftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#0F172A")
      textSize = 19f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
      textAlign = Paint.Align.LEFT
    }
    val cellRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#14532D")
      textSize = 19f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      textAlign = Paint.Align.RIGHT
    }
    val medLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#9A3412")
      textSize = 18f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      textAlign = Paint.Align.CENTER
    }
    val medNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#9A3412")
      textSize = 19f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      textAlign = Paint.Align.RIGHT
    }
    val pointsmanTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#5C4304")
      textSize = 20f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      textAlign = Paint.Align.CENTER
    }
    val shunterTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#075985")
      textSize = 20f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      textAlign = Paint.Align.CENTER
    }

    fun textBaseline(top: Float, h: Float, p: Paint): Float {
      val fm = p.fontMetrics
      return top + (h - (fm.descent - fm.ascent)) / 2f - fm.ascent
    }

    var y = margin
    val left = margin
    val right = width - margin
    val midX = left + contentW * 0.48f
    val medSplitX = midX + (right - midX) * 0.44f

    // Top DAILY DUTIES Header
    canvas.drawRect(left, y, right, y + bannerH, navyPaint)
    canvas.drawText("DAILY DUTIES", width / 2f, textBaseline(y, bannerH, titlePaint), titlePaint)
    canvas.drawRect(left, y, right, y + bannerH, borderPaint)
    y += bannerH

    canvas.drawRect(left, y, right, y + subBannerH, subHeaderPaint)
    canvas.drawText(sheet.headerRangeText, width / 2f, textBaseline(y, subBannerH, subBannerTextPaint), subBannerTextPaint)
    canvas.drawRect(left, y, right, y + subBannerH, borderPaint)
    y += subBannerH + sectionGap

    fun drawShiftBlock(block: DutyShiftSheetBlock) {
      val blockTop = y
      // Banner
      canvas.drawRect(left, y, right, y + bannerH, navyPaint)
      canvas.drawText(block.title, width / 2f, textBaseline(y, bannerH, shiftBannerTextPaint), shiftBannerTextPaint)
      y += bannerH

      // Column headers
      canvas.drawRect(left, y, midX, y + rowH, shedHdrPaint)
      canvas.drawRect(midX, y, right, y + rowH, stationHdrPaint)
      canvas.drawText("SHED STAFF", left + 16f, textBaseline(y, rowH, colHdrLeftPaint), colHdrLeftPaint)
      canvas.drawText("STATION STAFF", right - 16f, textBaseline(y, rowH, colHdrRightPaint), colHdrRightPaint)
      canvas.drawLine(left, y + rowH, right, y + rowH, innerGridPaint)
      y += rowH

      val rows = shiftRowsCount(block)
      for (i in 0 until rows) {
        canvas.drawRect(left, y, midX, y + rowH, whitePaint)
        val shedText = block.shedStaff.getOrNull(i).orEmpty()
        if (shedText.isNotBlank()) {
          canvas.drawText(shedText, left + 16f, textBaseline(y, rowH, cellLeftPaint), cellLeftPaint)
        }

        val isMedRow = block.showMedRow && i == block.stationStaff.size
        if (isMedRow) {
          canvas.drawRect(midX, y, right, y + rowH, medRowPaint)
          canvas.drawText("MED", (midX + medSplitX) / 2f, textBaseline(y, rowH, medLabelPaint), medLabelPaint)
          canvas.drawLine(medSplitX, y, medSplitX, y + rowH, innerGridPaint)
          val medName = block.medStaff ?: "UNASSIGNED"
          canvas.drawText(medName, right - 16f, textBaseline(y, rowH, medNamePaint), medNamePaint)
        } else {
          canvas.drawRect(midX, y, right, y + rowH, stationRowPaint)
          val stName = block.stationStaff.getOrNull(i).orEmpty()
          if (stName.isNotBlank()) {
            canvas.drawText(stName, right - 16f, textBaseline(y, rowH, cellRightPaint), cellRightPaint)
          }
        }
        canvas.drawLine(left, y + rowH, right, y + rowH, innerGridPaint)
        y += rowH
      }

      // Pointsman footer bar
      canvas.drawRect(left, y, right, y + rowH, pointsmanPaint)
      val pmText = "POINTSMAN: ${block.pointsmanName ?: "UNASSIGNED"}"
      canvas.drawText(pmText, width / 2f, textBaseline(y, rowH, pointsmanTextPaint), pointsmanTextPaint)
      y += rowH

      // Shunter footer bar (only when enabled)
      if (sheet.showShunter) {
        canvas.drawLine(left, y, right, y, innerGridPaint)
        canvas.drawRect(left, y, right, y + rowH, shunterPaint)
        val shText = "SHUNTER: ${block.shunterName ?: "UNASSIGNED"}"
        canvas.drawText(shText, width / 2f, textBaseline(y, rowH, shunterTextPaint), shunterTextPaint)
        y += rowH
      }

      canvas.drawRect(left, blockTop, right, y, borderPaint)
      y += sectionGap
    }

    drawShiftBlock(sheet.shift3Night)
    drawShiftBlock(sheet.shift1Morning)
    drawShiftBlock(sheet.shift2Afternoon)

    // General Shift block
    val genTop = y
    canvas.drawRect(left, y, right, y + bannerH, navyPaint)
    canvas.drawText(sheet.generalHeaderTitle, width / 2f, textBaseline(y, bannerH, shiftBannerTextPaint), shiftBannerTextPaint)
    y += bannerH

    canvas.drawRect(left, y, midX, y + rowH, shedHdrPaint)
    canvas.drawRect(midX, y, right, y + rowH, stationHdrPaint)
    canvas.drawText("GENERAL STAFF", left + 16f, textBaseline(y, rowH, colHdrLeftPaint), colHdrLeftPaint)
    canvas.drawText("DESIGNATION", right - 16f, textBaseline(y, rowH, colHdrRightPaint), colHdrRightPaint)
    canvas.drawLine(left, y + rowH, right, y + rowH, innerGridPaint)
    y += rowH

    for (i in 0 until generalRowsCount) {
      val row = sheet.generalStaff.getOrNull(i)
      canvas.drawRect(left, y, midX, y + rowH, whitePaint)
      canvas.drawRect(midX, y, right, y + rowH, whitePaint)
      if (row != null) {
        canvas.drawText(row.name, left + 16f, textBaseline(y, rowH, cellLeftPaint), cellLeftPaint)
        canvas.drawText(row.designation, right - 16f, textBaseline(y, rowH, cellRightPaint), cellRightPaint)
      }
      canvas.drawLine(left, y + rowH, right, y + rowH, innerGridPaint)
      y += rowH
    }
    if (sheet.showShunter) {
      canvas.drawRect(left, y, right, y + rowH, shunterPaint)
      val genShText = "SHUNTER: ${sheet.generalShunterName ?: "UNASSIGNED"}"
      canvas.drawText(genShText, width / 2f, textBaseline(y, rowH, shunterTextPaint), shunterTextPaint)
      y += rowH
    }
    canvas.drawRect(left, genTop, right, y, borderPaint)

    return bitmap
  }
}
