package com.example.share

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.DefaultPpioRoster
import com.example.domain.ShiftSchedulerEngine
import com.example.model.DutyArea
import com.example.model.ShiftSlotAssignment
import com.example.model.ShiftType
import com.example.model.SlotRoleType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DailyDutiesExporterTest {

  @Test
  fun buildSheetAndExportFiles_combinesTodayNightWithTomorrowDayAndGeneralShifts(): Unit = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val today = LocalDate.of(2026, 11, 16) // Monday
    val tomorrow = LocalDate.of(2026, 11, 17) // Tuesday
    val staff = DefaultPpioRoster.initialStaff("test_uid")

    val generated = ShiftSchedulerEngine.generateAssignmentsForDates(
      userId = "test_uid",
      dates = listOf(today, tomorrow),
      staffList = staff,
      existingAssignments = emptyList(),
      leaves = emptyList()
    ).toMutableList()

    // Ensure a General Shift entry is present if spare staff wasn't already allocated
    if (generated.none { it.date == tomorrow.toString() && it.shiftType == ShiftType.SHIFT_08_17.name }) {
      val bRajesh = staff.first { it.name == "B. RAJESH" }
      generated += ShiftSlotAssignment(
        id = "${tomorrow}_SHIFT_08_17_SHED_0",
        userId = "test_uid",
        date = tomorrow.toString(),
        shiftType = ShiftType.SHIFT_08_17.name,
        dutyArea = DutyArea.SHED.name,
        roleType = SlotRoleType.GENERAL_DUTY.name,
        slotIndex = 0,
        staffId = bRajesh.id,
        staffName = bRajesh.name
      )
    }

    val sheet = DailyDutiesExporter.buildSheetData(
      todayDate = today,
      staffList = staff,
      assignments = generated
    )

    assertTrue(sheet.headerRangeText.contains("16-NOV-2026"))
    assertTrue(sheet.headerRangeText.contains("17-NOV-2026"))
    assertTrue(sheet.shift3Night.showMedRow)
    assertTrue(sheet.shift3Night.medStaff != null)
    assertEquals(2, sheet.shift3Night.stationStaff.size)
    assertTrue(sheet.shift3Night.shedStaff.size in 3..5)
    assertTrue(!sheet.shift3Night.pointsmanName.isNullOrBlank())

    assertEquals(2, sheet.shift1Morning.stationStaff.size)
    assertTrue(sheet.shift1Morning.shedStaff.size in 3..5)
    assertTrue(!sheet.shift1Morning.pointsmanName.isNullOrBlank())

    assertEquals(2, sheet.shift2Afternoon.stationStaff.size)
    assertTrue(sheet.shift2Afternoon.shedStaff.size in 3..5)
    assertTrue(!sheet.shift2Afternoon.pointsmanName.isNullOrBlank())

    assertTrue(sheet.generalStaff.size in 1..3)

    val bitmap = DailyDutiesExporter.renderSheetBitmap(sheet)
    assertEquals(1080, bitmap.width)
    assertTrue(bitmap.height > 700)
    bitmap.recycle()

    val png = DailyDutiesExporter.createPngFile(context, sheet)
    assertTrue(png.exists() && png.length() > 1000L)
    val header = png.inputStream().use { stream -> ByteArray(4).also { stream.read(it) } }
    assertEquals(0x89.toByte(), header[0])
    assertEquals(0x50.toByte(), header[1])
    assertEquals(0x4E.toByte(), header[2])
    assertEquals(0x47.toByte(), header[3])

    val summary = DailyDutiesExporter.buildPlainTextSummary(sheet)
    assertTrue(summary.contains("SHIFT 3"))
    assertTrue(summary.contains("SHIFT 1"))
    assertTrue(summary.contains("SHIFT 2"))
    assertTrue(summary.contains("GENERAL SHIFT"))
    assertTrue(summary.contains("POINTSMAN", ignoreCase = true))
  }
}
