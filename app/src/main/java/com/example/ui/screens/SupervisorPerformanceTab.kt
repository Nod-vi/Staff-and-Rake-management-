package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.StaffPerformanceRow
import com.example.domain.SupervisorAnalyticsEngine
import com.example.model.DutyPermissionRecord
import com.example.model.EmergencyOverrideLog
import com.example.model.LeaveRecord
import com.example.model.ShiftSlotAssignment
import com.example.model.StaffMember
import com.example.ui.theme.DepotTeal
import com.example.ui.theme.DepotTealContainer
import com.example.ui.theme.DepotTealOnContainer
import com.example.ui.theme.GlassPillBadge
import com.example.ui.theme.GlassSurfaceBright
import com.example.ui.theme.GlassSurfaceMid
import com.example.ui.theme.GlassSurfaceSoft
import com.example.ui.theme.GlassTile
import com.example.ui.theme.RailwayCobalt
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SafetyAmberContainer
import com.example.ui.theme.SafetyAmberOnContainer
import com.example.ui.theme.SignalCrimson
import com.example.ui.theme.SignalCrimsonContainer
import com.example.ui.theme.SignalCrimsonOnContainer
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.SignalEmeraldContainer
import com.example.ui.theme.SignalEmeraldOnContainer
import com.example.ui.theme.liquidGlassAccent
import com.example.ui.theme.liquidGlassSurface
import java.time.LocalDate
import java.time.YearMonth

private enum class PerformancePeriod(val label: String) {
  THIS_MONTH("This Month"),
  LAST_30_DAYS("Last 30 Days"),
  THIS_YEAR("This Year")
}

private enum class PerformanceSort(val label: String) {
  DUTIES("Duties"),
  LEAVES("Leaves"),
  RELIEF_HOURS("Relief Hrs"),
  SPECIAL_CR("Special CR"),
  NAME("Name")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SupervisorPerformanceTab(
  selectedDate: LocalDate,
  staffList: List<StaffMember>,
  assignments: List<ShiftSlotAssignment>,
  leaves: List<LeaveRecord>,
  overrides: List<EmergencyOverrideLog>,
  permissions: List<DutyPermissionRecord>,
  modifier: Modifier = Modifier
) {
  var period by rememberSaveable { mutableStateOf(PerformancePeriod.THIS_MONTH) }
  var sortBy by rememberSaveable { mutableStateOf(PerformanceSort.DUTIES) }
  var query by rememberSaveable { mutableStateOf("") }

  val (rangeStart, rangeEnd) = remember(period, selectedDate) {
    when (period) {
      PerformancePeriod.THIS_MONTH -> {
        val ym = YearMonth.from(selectedDate)
        ym.atDay(1) to ym.atEndOfMonth()
      }
      PerformancePeriod.LAST_30_DAYS -> {
        selectedDate.minusDays(29) to selectedDate
      }
      PerformancePeriod.THIS_YEAR -> {
        LocalDate.of(selectedDate.year, 1, 1) to LocalDate.of(selectedDate.year, 12, 31)
      }
    }
  }

  val allRows = remember(staffList, assignments, leaves, overrides, permissions, rangeStart, rangeEnd) {
    SupervisorAnalyticsEngine.computeStaffPerformanceMetrics(
      staffList = staffList,
      assignments = assignments,
      leaves = leaves,
      overrides = overrides,
      permissions = permissions,
      startDate = rangeStart,
      endDate = rangeEnd
    )
  }

  val filteredAndSorted = remember(allRows, sortBy, query) {
    val q = query.trim().lowercase()
    val filtered = if (q.isEmpty()) allRows else allRows.filter {
      it.staff.name.lowercase().contains(q) ||
        it.staff.designationEnum.label.lowercase().contains(q) ||
        it.staff.serialNo.toString().contains(q)
    }
    when (sortBy) {
      PerformanceSort.DUTIES -> filtered.sortedWith(compareByDescending<StaffPerformanceRow> { it.totalDuties }.thenBy { it.staff.serialNo })
      PerformanceSort.LEAVES -> filtered.sortedWith(compareByDescending<StaffPerformanceRow> { it.approvedLeaveDays }.thenBy { it.staff.serialNo })
      PerformanceSort.RELIEF_HOURS -> filtered.sortedWith(compareByDescending<StaffPerformanceRow> { it.totalPermissionHours }.thenBy { it.staff.serialNo })
      PerformanceSort.SPECIAL_CR -> filtered.sortedWith(compareByDescending<StaffPerformanceRow> { it.specialLeaveCreditsEarned }.thenBy { it.staff.serialNo })
      PerformanceSort.NAME -> filtered.sortedBy { it.staff.name }
    }
  }

  val depotTotalDuties = remember(allRows) { allRows.sumOf { it.totalDuties } }
  val depotTotalSpecialCredits = remember(allRows) { allRows.sumOf { it.specialLeaveCreditsEarned } }
  val depotTotalLeaveDays = remember(allRows) { allRows.sumOf { it.approvedLeaveDays } }
  val depotTotalReliefHours = remember(allRows) { allRows.sumOf { it.totalPermissionHours } }

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 780.dp),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      item(key = "perf_controls", contentType = "controls") {
        GlassTile(
          shape = RoundedCornerShape(24.dp),
          topColor = GlassSurfaceBright,
          bottomColor = GlassSurfaceMid,
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Text(
              text = "Staff Performance Monitor",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "Period: $rangeStart to $rangeEnd · ${allRows.size} active duty staff",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Period Switcher
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              PerformancePeriod.entries.forEach { p ->
                val active = period == p
                  Box(
                    modifier = Modifier
                      .weight(1f)
                      .heightIn(min = 48.dp)
                      .let { base ->
                        if (active) {
                          base.liquidGlassAccent(shape = RoundedCornerShape(12.dp))
                        } else {
                          base.liquidGlassSurface(
                            shape = RoundedCornerShape(12.dp),
                            topColor = GlassSurfaceBright,
                            bottomColor = GlassSurfaceSoft,
                            borderWidth = 1.dp
                          )
                        }
                      }
                      .clickable { period = p }
                      .padding(horizontal = 6.dp, vertical = 8.dp)
                      .testTag("perf_period_${p.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                  ) {
                    Text(
                      text = p.label,
                      style = MaterialTheme.typography.labelMedium,
                      fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                      color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                      textAlign = TextAlign.Center,
                      maxLines = 2
                    )
                  }
              }
            }

            // Depot Totals Row
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              PerfDepotStat("Duties", "$depotTotalDuties", RailwayCobalt, Modifier.weight(1f))
              PerfDepotStat("Special CR", "+$depotTotalSpecialCredits", SignalEmerald, Modifier.weight(1f))
              PerfDepotStat("Leave Days", "${depotTotalLeaveDays}d", SignalCrimson, Modifier.weight(1f))
              PerfDepotStat(
                "Relief Hrs",
                SupervisorAnalyticsEngine.formatHours(depotTotalReliefHours),
                DepotTeal,
                Modifier.weight(1f)
              )
            }

            OutlinedTextField(
              value = query,
              onValueChange = { query = it },
              leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
              placeholder = { Text("Search staff by name, designation, or serial #...") },
              singleLine = true,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("perf_search_input")
            )

            // Sort Chips
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              PerformanceSort.entries.forEach { s ->
                val active = sortBy == s
                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = if (active) RailwayCobalt else GlassSurfaceSoft,
                  border = BorderStroke(
                    1.dp,
                    if (active) RailwayCobalt else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                  ),
                  modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable { sortBy = s }
                    .testTag("perf_sort_${s.name.lowercase()}")
                ) {
                  Box(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                  ) {
                    Text(
                      text = "Sort: ${s.label}",
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                      color = if (active) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                  }
                }
              }
            }
          }
        }
      }

      items(
        items = filteredAndSorted,
        key = { it.staff.id },
        contentType = { "perf_staff_card" }
      ) { row ->
        StaffPerformanceCard(row = row)
      }

      item(key = "perf_bottom_spacer") {
        Spacer(modifier = Modifier.height(16.dp))
      }
    }
  }
}

@Composable
private fun PerfDepotStat(
  label: String,
  value: String,
  accent: Color,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = accent.copy(alpha = 0.10f),
    border = BorderStroke(1.dp, accent.copy(alpha = 0.3f)),
    modifier = modifier
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(
        text = value,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = accent,
        maxLines = 1
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 2
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StaffPerformanceCard(row: StaffPerformanceRow) {
  val (tagBg, tagFg, tagBorder) = when (row.performanceTag) {
    "Top Performer" -> Triple(SignalEmeraldContainer, SignalEmeraldOnContainer, SignalEmerald)
    "High Absence / Relief" -> Triple(SignalCrimsonContainer, SignalCrimsonOnContainer, SignalCrimson)
    "No Duties Logged" -> Triple(SafetyAmberContainer, SafetyAmberOnContainer, SafetyAmber)
    else -> Triple(DepotTealContainer, DepotTealOnContainer, DepotTeal)
  }

  GlassTile(
    shape = RoundedCornerShape(22.dp),
    topColor = GlassSurfaceBright,
    bottomColor = GlassSurfaceMid,
    modifier = Modifier
      .fillMaxWidth()
      .testTag("perf_card_${row.staff.id}")
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Text(
          text = "#${row.staff.serialNo} ${row.staff.name}",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          maxLines = 2
        )
        Text(
          text = "${row.staff.designationEnum.label}" +
            (if (row.staff.isPointsman) " · Pointsman" else "") +
            " · Rest: ${row.staff.restDayEnum.label}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          GlassPillBadge(
            text = row.performanceTag.uppercase(),
            tintColor = tagBg.copy(alpha = 0.78f),
            textColor = tagFg,
            borderColor = tagBorder
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        PerfDepotStat("Duties", "${row.totalDuties}", RailwayCobalt, Modifier.weight(1f))
        PerfDepotStat("Special CR", "+${row.specialLeaveCreditsEarned}", SignalEmerald, Modifier.weight(1f))
        PerfDepotStat("Leaves", "${row.approvedLeaveDays}d", SignalCrimson, Modifier.weight(1f))
        PerfDepotStat(
          "Relief",
          if (row.permissionCount > 0) SupervisorAnalyticsEngine.formatHours(row.totalPermissionHours) else "0h",
          DepotTeal,
          Modifier.weight(1f)
        )
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))

      Text(
        text = "Shifts: 22/06(${row.nightDuties}) · 06/14(${row.morningDuties}) · 14/22(${row.afternoonDuties}) · 08/17(${row.generalDuties})",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      val extraNotes = buildList {
        if (row.restDayDuties > 0) add("Rest-day duties: ${row.restDayDuties}")
        if (row.doubleDutyDays > 0) add("2x duty days: ${row.doubleDutyDays}")
        if (row.approvedLeaveDays > 0) {
          add("Leave mix: CL ${row.approvedClDays}d · LAP ${row.approvedLapDays}d · LHAP ${row.approvedLhapDays}d")
        }
        if (row.approvedTrainingDays > 0) {
          add("Training: ${row.approvedTrainingDays}d")
        }
        if (row.permissionCount > 0) {
          add("Permissions: ${row.permissionCount} (${SupervisorAnalyticsEngine.formatHours(row.totalPermissionHours)})")
        }
        if (row.emergencyOverridesCount > 0) add("Overrides: ${row.emergencyOverridesCount}")
      }
      if (extraNotes.isNotEmpty()) {
        Text(
          text = extraNotes.joinToString("  |  "),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurface
        )
      }
    }
  }
}
