package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.ShiftCoverageSummary
import com.example.domain.ShiftSchedulerEngine
import com.example.model.DutyArea
import com.example.model.DutyDateLock
import com.example.model.LeaveRecord
import com.example.model.ShiftSlotAssignment
import com.example.model.ShiftType
import com.example.model.SlotRoleType
import com.example.model.StaffMember
import com.example.model.UserRole
import com.example.ui.theme.DepotTeal
import com.example.ui.theme.GlassPillBadge
import com.example.ui.theme.GlassPopupSurface
import com.example.ui.theme.GlassRimHighlight
import com.example.ui.theme.GlassSurfaceBright
import com.example.ui.theme.GlassSurfaceMid
import com.example.ui.theme.GlassSurfaceSoft
import com.example.ui.theme.GlassTile
import com.example.ui.theme.RailwayCobalt
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SafetyAmberContainer
import com.example.ui.theme.SafetyAmberOnContainer
import com.example.ui.theme.SignalCrimsonOnContainer
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.SignalEmeraldContainer
import com.example.ui.theme.SignalEmeraldOnContainer
import com.example.ui.theme.liquidGlassAccent
import com.example.ui.theme.liquidGlassSurface
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val ALL_ROSTER_SHIFTS = listOf(
  ShiftType.SHIFT_22_06,
  ShiftType.SHIFT_06_14,
  ShiftType.SHIFT_14_22,
  ShiftType.SHIFT_08_17
)

private enum class CalendarGridMode(val label: String) {
  SHIFT_MATRIX("Shift Matrix"),
  DAY_CARDS("7-Day Cards")
}

private data class DayShiftCellModel(
  val dutyDate: LocalDate,
  val shiftType: ShiftType,
  val summary: ShiftCoverageSummary,
  val assignments: List<ShiftSlotAssignment>
)

private data class DayRosterSummaryModel(
  val dutyDate: LocalDate,
  val isLocked: Boolean,
  val shiftCells: Map<ShiftType, DayShiftCellModel>,
  val onLeaveStaff: List<StaffMember>,
  val onRestStaff: List<StaffMember>
)

private data class WeeklyShiftDistributionSummary(
  val shiftType: ShiftType,
  val totalAssignedSlots: Int,
  val totalTargetSlots: Int,
  val pointsmanAssigned: Int,
  val shedAssigned: Int,
  val stationAssigned: Int,
  val uniqueStaffCount: Int,
  val compliantDays: Int,
  val activeDays: Int
)

@Composable
fun WeeklyRosterCalendarGrid(
  selectedDate: LocalDate,
  staffList: List<StaffMember>,
  assignments: List<ShiftSlotAssignment>,
  leaves: List<LeaveRecord>,
  dutyLocks: List<DutyDateLock> = emptyList(),
  userRole: UserRole = UserRole.SUPERVISOR,
  roleAccess: com.example.model.ResolvedRoleAccess = com.example.model.RoleAccessPolicy.resolve(userRole, emptyMap()),
  showShunterForTechnicians: Boolean = false,
  isBusy: Boolean = false,
  onSelectDate: (LocalDate) -> Unit,
  onOpenDayShiftBoard: ((LocalDate) -> Unit)? = null,
  onAutoAssignWeek: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val showShunter = userRole != UserRole.TECHNICIAN || showShunterForTechnicians
  val visibleAssignments = remember(assignments, showShunter) {
    if (showShunter) assignments else assignments.filter { it.roleType != SlotRoleType.SHUNTER.name }
  }
  var gridMode by rememberSaveable { mutableStateOf(CalendarGridMode.SHIFT_MATRIX) }
  var spotlightShift by rememberSaveable { mutableStateOf<ShiftType?>(null) }
  var inspectedCell by remember { mutableStateOf<Pair<DayRosterSummaryModel, ShiftType>?>(null) }

  val weekStart = remember(selectedDate) {
    selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
  }
  val weekDays = remember(weekStart) {
    (0L..6L).map { weekStart.plusDays(it) }
  }
  val activeDutyStaff = remember(staffList) {
    staffList.filter { !it.isSupervisory }
  }
  val lockDateSet = remember(dutyLocks) {
    dutyLocks.mapTo(HashSet()) { it.dutyDate }
  }

  val daySummaries = remember(weekDays, visibleAssignments, leaves, activeDutyStaff, lockDateSet) {
    weekDays.map { dutyDate ->
      val dutyDateStr = dutyDate.toString()
      val coverageByShift = ShiftSchedulerEngine.computeDutyDayCoverage(dutyDate, visibleAssignments)
        .associateBy { it.shiftType }
      val cycleSlots = ShiftSchedulerEngine.buildDutyDaySlots(dutyDate)
      val cycleSlotIdsByShift = cycleSlots.groupBy({ it.shiftType }, { it.documentId })
      val assignmentById = visibleAssignments.associateBy { it.id }

      val shiftCells = ALL_ROSTER_SHIFTS.associateWith { shiftType ->
        val slotIds = cycleSlotIdsByShift[shiftType].orEmpty()
        val shiftAssignments = slotIds.mapNotNull { assignmentById[it] }
        DayShiftCellModel(
          dutyDate = dutyDate,
          shiftType = shiftType,
          summary = coverageByShift.getValue(shiftType),
          assignments = shiftAssignments
        )
      }
      val isLocked = dutyDateStr in lockDateSet ||
        shiftCells.values.any { cell -> cell.assignments.any { it.approvedLocked } }

      val onLeave = activeDutyStaff.filter { member ->
        leaves.any { it.staffId == member.id && it.isBlockingDate(dutyDateStr) }
      }
      val onRest = activeDutyStaff.filter { member ->
        member.restDayEnum.dayOfWeek == dutyDate.dayOfWeek
      }

      DayRosterSummaryModel(
        dutyDate = dutyDate,
        isLocked = isLocked,
        shiftCells = shiftCells,
        onLeaveStaff = onLeave,
        onRestStaff = onRest
      )
    }
  }

  val shiftDistributions = remember(daySummaries) {
    ALL_ROSTER_SHIFTS.map { shiftType ->
      var totalAssigned = 0
      var totalTarget = 0
      var pmCount = 0
      var shedCount = 0
      var stationCount = 0
      var compliantDays = 0
      var activeDays = 0
      val uniqueStaff = HashSet<String>()

      daySummaries.forEach { day ->
        val cell = day.shiftCells.getValue(shiftType)
        val sum = cell.summary
        val req = if (shiftType.isGeneralShift) {
          sum.shedMaxTarget
        } else {
          sum.pointsmanTarget + sum.shedMinTarget + sum.stationTarget + sum.medTarget
        }
        totalTarget += req
        totalAssigned += cell.assignments.size
        pmCount += sum.pointsmanFilled
        shedCount += sum.shedFilled
        stationCount += sum.stationFilled + sum.medFilled
        if (sum.isFullyCompliant) compliantDays++
        if (cell.assignments.isNotEmpty()) activeDays++
        cell.assignments.forEach { uniqueStaff.add(it.staffId) }
      }

      WeeklyShiftDistributionSummary(
        shiftType = shiftType,
        totalAssignedSlots = totalAssigned,
        totalTargetSlots = totalTarget,
        pointsmanAssigned = pmCount,
        shedAssigned = shedCount,
        stationAssigned = stationCount,
        uniqueStaffCount = uniqueStaff.size,
        compliantDays = compliantDays,
        activeDays = activeDays
      )
    }
  }

  val gridColumns = if (gridMode == CalendarGridMode.SHIFT_MATRIX) {
    GridCells.Fixed(5)
  } else {
    GridCells.Adaptive(minSize = 240.dp)
  }

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter
  ) {
    LazyVerticalGrid(
      columns = gridColumns,
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 860.dp)
        .testTag("weekly_roster_lazy_vertical_grid"),
      contentPadding = PaddingValues(12.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      item(
        key = "weekly_calendar_header",
        span = { GridItemSpan(maxLineSpan) },
        contentType = "header"
      ) {
        WeeklyCalendarHeaderCard(
          weekStart = weekStart,
          selectedDate = selectedDate,
          gridMode = gridMode,
          spotlightShift = spotlightShift,
          userRole = userRole,
          roleAccess = roleAccess,
          isBusy = isBusy,
          onPrevWeek = { onSelectDate(selectedDate.minusWeeks(1)) },
          onNextWeek = { onSelectDate(selectedDate.plusWeeks(1)) },
          onJumpToday = { onSelectDate(LocalDate.now()) },
          onToggleGridMode = { gridMode = it },
          onSelectSpotlightShift = { spotlightShift = it },
          onAutoAssignWeek = onAutoAssignWeek
        )
      }

      item(
        key = "weekly_distribution_overview",
        span = { GridItemSpan(maxLineSpan) },
        contentType = "distribution_overview"
      ) {
        WeeklyShiftDistributionStrip(
          distributions = shiftDistributions,
          spotlightShift = spotlightShift,
          onToggleSpotlight = { clicked ->
            spotlightShift = if (spotlightShift == clicked) null else clicked
          }
        )
      }

      if (gridMode == CalendarGridMode.SHIFT_MATRIX) {
        item(
          key = "matrix_header_day",
          contentType = "matrix_header"
        ) {
          MatrixColumnHeaderCell(
            title = "DAY",
            subtitle = "L · R",
            accent = RailwayCobalt
          )
        }

        items(
          items = ALL_ROSTER_SHIFTS,
          key = { "matrix_header_${it.name}" },
          contentType = { "matrix_header" }
        ) { shiftType ->
          val isDimmed = spotlightShift != null && spotlightShift != shiftType
          val shortSub = when (shiftType) {
            ShiftType.SHIFT_22_06 -> "Night"
            ShiftType.SHIFT_06_14 -> "Morning"
            ShiftType.SHIFT_14_22 -> "Afternoon"
            ShiftType.SHIFT_08_17 -> "General"
          }
          MatrixColumnHeaderCell(
            title = shiftType.code,
            subtitle = shortSub,
            accent = shiftAccentColor(shiftType),
            isDimmed = isDimmed,
            onClick = {
              spotlightShift = if (spotlightShift == shiftType) null else shiftType
            }
          )
        }

        daySummaries.forEach { dayModel ->
          val dateStr = dayModel.dutyDate.toString()
          item(
            key = "matrix_day_$dateStr",
            contentType = "matrix_day_cell"
          ) {
            MatrixDayRowHeaderCell(
              dayModel = dayModel,
              isSelected = dayModel.dutyDate == selectedDate,
              onClick = {
                onSelectDate(dayModel.dutyDate)
                onOpenDayShiftBoard?.invoke(dayModel.dutyDate)
              }
            )
          }

          ALL_ROSTER_SHIFTS.forEach { shiftType ->
            val cell = dayModel.shiftCells.getValue(shiftType)
            val isDimmed = spotlightShift != null && spotlightShift != shiftType
            item(
              key = "matrix_cell_${dateStr}_${shiftType.name}",
              contentType = "matrix_shift_cell"
            ) {
              MatrixShiftGridCell(
                cell = cell,
                isSelectedDay = dayModel.dutyDate == selectedDate,
                isLocked = dayModel.isLocked,
                isDimmed = isDimmed,
                onClick = {
                  onSelectDate(dayModel.dutyDate)
                  inspectedCell = dayModel to shiftType
                }
              )
            }
          }
        }
      } else {
        items(
          items = daySummaries,
          key = { "day_card_${it.dutyDate}" },
          contentType = { "day_roster_card" }
        ) { dayModel ->
          WeeklyDayRosterGridCard(
            dayModel = dayModel,
            isSelected = dayModel.dutyDate == selectedDate,
            spotlightShift = spotlightShift,
            onSelectDay = {
              onSelectDate(dayModel.dutyDate)
              onOpenDayShiftBoard?.invoke(dayModel.dutyDate)
            },
            onInspectShift = { shiftType ->
              onSelectDate(dayModel.dutyDate)
              inspectedCell = dayModel to shiftType
            }
          )
        }
      }

      item(
        key = "weekly_grid_legend",
        span = { GridItemSpan(maxLineSpan) },
        contentType = "legend"
      ) {
        WeeklyGridLegendTile()
      }

      item(
        key = "weekly_grid_bottom_spacer",
        span = { GridItemSpan(maxLineSpan) },
        contentType = "spacer"
      ) {
        Spacer(modifier = Modifier.height(16.dp))
      }
    }
  }

  inspectedCell?.let { (dayModel, shiftType) ->
    WeeklyShiftCellDetailDialog(
      dayModel = dayModel,
      shiftType = shiftType,
      staffById = remember(staffList) { staffList.associateBy { it.id } },
      onDismiss = { inspectedCell = null },
      onOpenDayShiftBoard = onOpenDayShiftBoard?.let { callback ->
        {
          inspectedCell = null
          onSelectDate(dayModel.dutyDate)
          callback(dayModel.dutyDate)
        }
      }
    )
  }
}

@Composable
private fun WeeklyCalendarHeaderCard(
  weekStart: LocalDate,
  selectedDate: LocalDate,
  gridMode: CalendarGridMode,
  spotlightShift: ShiftType?,
  userRole: UserRole,
  roleAccess: com.example.model.ResolvedRoleAccess = com.example.model.RoleAccessPolicy.resolve(userRole, emptyMap()),
  isBusy: Boolean,
  onPrevWeek: () -> Unit,
  onNextWeek: () -> Unit,
  onJumpToday: () -> Unit,
  onToggleGridMode: (CalendarGridMode) -> Unit,
  onSelectSpotlightShift: (ShiftType?) -> Unit,
  onAutoAssignWeek: (() -> Unit)?
) {
  val rangeFormatter = remember { DateTimeFormatter.ofPattern("dd MMM") }
  val yearFormatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy") }
  val weekEnd = remember(weekStart) { weekStart.plusDays(6) }
  val rangeText = remember(weekStart, weekEnd) {
    "${weekStart.format(rangeFormatter)} – ${weekEnd.format(yearFormatter)}"
  }

  GlassTile(
    shape = RoundedCornerShape(24.dp),
    topColor = GlassSurfaceBright,
    bottomColor = GlassSurfaceMid
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        IconButton(
          onClick = onPrevWeek,
          modifier = Modifier
            .size(48.dp)
            .testTag("weekly_prev_week_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = "Previous week"
          )
        }

        Column(
          modifier = Modifier.weight(1f),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = Icons.Default.CalendarMonth,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp)
            )
            Text(
              text = "Weekly Roster Calendar",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
          }
          Text(
            text = "Mon $rangeText",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        IconButton(
          onClick = onNextWeek,
          modifier = Modifier
            .size(48.dp)
            .testTag("weekly_next_week_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Next week"
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        CalendarGridMode.entries.forEach { mode ->
          val selected = gridMode == mode
          val icon = if (mode == CalendarGridMode.SHIFT_MATRIX) {
            Icons.Default.GridView
          } else {
            Icons.Default.ViewModule
          }
          Box(
            modifier = (if (selected) {
              Modifier.liquidGlassAccent(shape = RoundedCornerShape(14.dp))
            } else {
              Modifier.liquidGlassSurface(
                shape = RoundedCornerShape(14.dp),
                topColor = GlassSurfaceBright,
                bottomColor = GlassSurfaceSoft,
                borderWidth = 1.dp
              )
            })
              .weight(1f)
              .heightIn(min = 48.dp)
              .clickable { onToggleGridMode(mode) }
              .padding(horizontal = 10.dp, vertical = 8.dp)
              .testTag("weekly_grid_mode_${mode.name.lowercase()}"),
            contentAlignment = Alignment.Center
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(16.dp)
              )
              Text(
                text = mode.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2
              )
            }
          }
        }
      }

      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        item(key = "filter_all_shifts") {
          ShiftFilterChip(
            label = "All Shifts",
            selected = spotlightShift == null,
            accent = RailwayCobalt,
            onClick = { onSelectSpotlightShift(null) },
            testTag = "weekly_filter_all"
          )
        }
        items(ALL_ROSTER_SHIFTS, key = { it.name }) { shift ->
          ShiftFilterChip(
            label = "${shift.code} ${shift.displayName.removeSuffix(" Shift")}",
            selected = spotlightShift == shift,
            accent = shiftAccentColor(shift),
            onClick = {
              onSelectSpotlightShift(if (spotlightShift == shift) null else shift)
            },
            testTag = "weekly_filter_${shift.name.lowercase()}"
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (selectedDate != LocalDate.now()) {
          OutlinedButton(
            onClick = onJumpToday,
            colors = ButtonDefaults.outlinedButtonColors(containerColor = GlassSurfaceSoft),
            border = BorderStroke(1.dp, GlassRimHighlight),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("weekly_jump_today_button")
          ) {
            Text("This Week")
          }
        }

        if (roleAccess.canDirectEditDuties && onAutoAssignWeek != null) {
          Button(
            onClick = onAutoAssignWeek,
            enabled = !isBusy,
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = if (!isBusy) Color.Transparent else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
              contentColor = Color.White
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 48.dp)
              .let { mod -> if (!isBusy) mod.liquidGlassAccent(shape = RoundedCornerShape(14.dp)) else mod }
              .testTag("weekly_calendar_auto_assign_button")
          ) {
            Icon(
              imageVector = Icons.Default.AutoAwesome,
              contentDescription = null,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Auto-Assign Current Week",
              maxLines = 2,
              textAlign = TextAlign.Center
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ShiftFilterChip(
  label: String,
  selected: Boolean,
  accent: Color,
  onClick: () -> Unit,
  testTag: String
) {
  Box(
    modifier = (if (selected) {
      Modifier.liquidGlassAccent(
        shape = RoundedCornerShape(50),
        baseColor = accent,
        highlightColor = accent.copy(alpha = 0.85f)
      )
    } else {
      Modifier.liquidGlassSurface(
        shape = RoundedCornerShape(50),
        topColor = GlassSurfaceBright,
        bottomColor = GlassSurfaceSoft,
        borderWidth = 1.dp
      )
    })
      .heightIn(min = 48.dp)
      .clickable(onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 8.dp)
      .testTag(testTag),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
      color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
    )
  }
}

@Composable
private fun WeeklyShiftDistributionStrip(
  distributions: List<WeeklyShiftDistributionSummary>,
  spotlightShift: ShiftType?,
  onToggleSpotlight: (ShiftType) -> Unit
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      text = "Weekly Staff Distribution (22/06, 06/14, 14/22 & 08/17 General)",
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(horizontal = 2.dp)
    )
    distributions.chunked(2).forEach { rowItems ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        rowItems.forEach { dist ->
          val accent = shiftAccentColor(dist.shiftType)
          val isHighlighted = spotlightShift == dist.shiftType
          val isDimmed = spotlightShift != null && spotlightShift != dist.shiftType
          val ratio = (dist.totalAssignedSlots.toFloat() / dist.totalTargetSlots.coerceAtLeast(1))
            .coerceIn(0f, 1f)

          GlassTile(
            modifier = Modifier
              .weight(1f)
              .clickable { onToggleSpotlight(dist.shiftType) }
              .testTag("weekly_dist_card_${dist.shiftType.name.lowercase()}"),
            shape = RoundedCornerShape(18.dp),
            topColor = if (isDimmed) GlassSurfaceSoft else GlassSurfaceBright,
            bottomColor = if (isDimmed) GlassSurfaceSoft else GlassSurfaceMid,
            tintColor = accent.copy(alpha = if (isHighlighted) 0.22f else 0.08f)
          ) {
            Column(
              modifier = Modifier.padding(10.dp),
              verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                  modifier = Modifier.weight(1f)
                ) {
                  Icon(
                    imageVector = shiftIcon(dist.shiftType),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(15.dp)
                  )
                  Text(
                    text = "${dist.shiftType.code} ${dist.shiftType.displayName.removeSuffix(" Shift")}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    maxLines = 2
                  )
                }
                Spacer(modifier = Modifier.width(4.dp))
                val rightTag = if (dist.shiftType.isGeneralShift) {
                  "${dist.activeDays}/7d"
                } else {
                  "${dist.compliantDays}/7d"
                }
                Text(
                  text = rightTag,
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = if (dist.shiftType.isGeneralShift || dist.compliantDays == 7) SignalEmerald else SafetyAmber
                )
              }

              Text(
                text = if (dist.shiftType.isGeneralShift) {
                  "${dist.totalAssignedSlots} / 0–${dist.totalTargetSlots} slots"
                } else {
                  "${dist.totalAssignedSlots} / ${dist.totalTargetSlots} slots"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2
              )

              LinearProgressIndicator(
                progress = { ratio },
                color = if (dist.compliantDays == 7 && !dist.shiftType.isGeneralShift) SignalEmerald else accent,
                trackColor = GlassSurfaceSoft,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(6.dp)
              )

              val breakdownLine = if (dist.shiftType.isGeneralShift) {
                "General Shed: ${dist.shedAssigned} (0–3/day)"
              } else {
                "PM:${dist.pointsmanAssigned} · Shed:${dist.shedAssigned} · Stn:${dist.stationAssigned}"
              }
              Text(
                text = breakdownLine,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
              )
              Text(
                text = "${dist.uniqueStaffCount} unique staff",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                fontWeight = FontWeight.SemiBold,
                color = accent,
                maxLines = 2
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MatrixColumnHeaderCell(
  title: String,
  subtitle: String,
  accent: Color,
  isDimmed: Boolean = false,
  onClick: (() -> Unit)? = null
) {
  GlassTile(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 52.dp)
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    shape = RoundedCornerShape(14.dp),
    topColor = if (isDimmed) GlassSurfaceSoft else GlassSurfaceBright,
    bottomColor = GlassSurfaceSoft,
    tintColor = accent.copy(alpha = if (isDimmed) 0.04f else 0.14f)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 4.dp, vertical = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = if (isDimmed) accent.copy(alpha = 0.5f) else accent,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
private fun MatrixDayRowHeaderCell(
  dayModel: DayRosterSummaryModel,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  val date = dayModel.dutyDate
  val dayShort = remember(date) {
    date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase()
  }
  val dateLabel = remember(date) {
    date.format(DateTimeFormatter.ofPattern("dd MMM"))
  }

  val baseMod = if (isSelected) {
    Modifier.liquidGlassAccent(shape = RoundedCornerShape(16.dp))
  } else {
    Modifier.liquidGlassSurface(
      shape = RoundedCornerShape(16.dp),
      topColor = GlassSurfaceBright,
      bottomColor = GlassSurfaceMid,
      borderWidth = 1.dp
    )
  }

  Box(
    modifier = baseMod
      .fillMaxWidth()
      .heightIn(min = 108.dp)
      .clickable(onClick = onClick)
      .padding(6.dp)
      .testTag("weekly_day_cell_${dayModel.dutyDate}"),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        Text(
          text = dayShort,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = if (isSelected) Color.White else MaterialTheme.colorScheme.primary
        )
        if (dayModel.isLocked) {
          Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Locked",
            tint = if (isSelected) Color.White else SignalEmerald,
            modifier = Modifier.size(11.dp)
          )
        }
      }
      Text(
        text = dateLabel,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        fontWeight = FontWeight.SemiBold,
        color = if (isSelected) Color.White.copy(alpha = 0.92f) else MaterialTheme.colorScheme.onSurface,
        maxLines = 1
      )

      Text(
        text = "L:${dayModel.onLeaveStaff.size} R:${dayModel.onRestStaff.size}",
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
        color = if (isSelected) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1
      )
    }
  }
}

@Composable
private fun MatrixShiftGridCell(
  cell: DayShiftCellModel,
  isSelectedDay: Boolean,
  isLocked: Boolean,
  isDimmed: Boolean,
  onClick: () -> Unit
) {
  val sum = cell.summary
  val isGeneral = cell.shiftType.isGeneralShift
  val requiredCount = if (isGeneral) {
    sum.shedMaxTarget
  } else {
    sum.pointsmanTarget + sum.shedMinTarget + sum.stationTarget + sum.medTarget
  }
  val filledCount = cell.assignments.size
  val hasOverride = cell.assignments.any { it.isEmergencyOverride }

  val statusTint = when {
    isDimmed -> Color.Transparent
    isGeneral && filledCount > 0 -> SignalEmeraldContainer.copy(alpha = 0.30f)
    isGeneral -> Color.Transparent
    sum.isFullyCompliant && !hasOverride -> SignalEmeraldContainer.copy(alpha = 0.36f)
    filledCount > 0 -> SafetyAmberContainer.copy(alpha = 0.38f)
    else -> Color.Transparent
  }
  val statusInk = when {
    isGeneral && filledCount > 0 -> SignalEmerald
    isGeneral -> MaterialTheme.colorScheme.onSurfaceVariant
    sum.isFullyCompliant && !hasOverride -> SignalEmerald
    filledCount > 0 -> SafetyAmber
    else -> MaterialTheme.colorScheme.onSurfaceVariant
  }

  val staffPreview = remember(cell.assignments) {
    cell.assignments.map { shortStaffName(it.staffName) }
  }

  GlassTile(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 108.dp)
      .clickable(onClick = onClick)
      .testTag("weekly_cell_${cell.dutyDate}_${cell.shiftType.name}"),
    shape = RoundedCornerShape(16.dp),
    topColor = if (isDimmed) GlassSurfaceSoft else GlassSurfaceBright,
    bottomColor = GlassSurfaceSoft,
    tintColor = statusTint
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(6.dp),
      verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          Icon(
            imageVector = if (sum.isFullyCompliant) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = statusInk,
            modifier = Modifier.size(11.dp)
          )
          Text(
            text = if (isGeneral) "$filledCount/0–3" else "$filledCount/$requiredCount",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            fontWeight = FontWeight.Bold,
            color = statusInk,
            maxLines = 1
          )
        }
        if (isLocked && filledCount > 0) {
          Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = SignalEmerald,
            modifier = Modifier.size(10.dp)
          )
        }
      }

      val areaLine = if (isGeneral) {
        "Gen:${sum.shedFilled}"
      } else {
        val stCode = cell.shiftType.stationArea?.shortCode ?: "ST"
        buildString {
          append("P:${sum.pointsmanFilled} S:${sum.shedFilled} $stCode:${sum.stationFilled}")
          if (sum.medActive) append(" M:${sum.medFilled}")
        }
      }
      Text(
        text = areaLine,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )

      if (staffPreview.isEmpty()) {
        Text(
          text = if (isGeneral) "Optional" else if (isSelectedDay) "Assign" else "Empty",
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
          maxLines = 1
        )
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
          staffPreview.take(2).forEach { name ->
            Text(
              text = "• $name",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
          if (staffPreview.size > 2) {
            Text(
              text = "+${staffPreview.size - 2} more",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
              fontWeight = FontWeight.Bold,
              color = shiftAccentColor(cell.shiftType),
              maxLines = 1
            )
          }
        }
      }
    }
  }
}

@Composable
private fun WeeklyDayRosterGridCard(
  dayModel: DayRosterSummaryModel,
  isSelected: Boolean,
  spotlightShift: ShiftType?,
  onSelectDay: () -> Unit,
  onInspectShift: (ShiftType) -> Unit
) {
  val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE, dd MMM") }
  val prevDateStr = remember(dayModel.dutyDate) {
    dayModel.dutyDate.minusDays(1).format(DateTimeFormatter.ofPattern("dd MMM"))
  }

  GlassTile(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("weekly_day_card_${dayModel.dutyDate}"),
    shape = RoundedCornerShape(22.dp),
    topColor = GlassSurfaceBright,
    bottomColor = GlassSurfaceMid,
    tintColor = if (isSelected) RailwayCobalt.copy(alpha = 0.14f) else Color.Transparent
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Text(
              text = dayModel.dutyDate.format(dateFormatter),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold
            )
            if (dayModel.isLocked) {
              GlassPillBadge(
                text = "LOCKED",
                tintColor = SignalEmeraldContainer.copy(alpha = 0.72f),
                textColor = SignalEmeraldOnContainer,
                borderColor = SignalEmerald
              )
            }
          }
          Text(
            text = "22/06 starts $prevDateStr · Leave: ${dayModel.onLeaveStaff.size} · Rest: ${dayModel.onRestStaff.size}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        TextButton(
          onClick = onSelectDay,
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("weekly_open_day_${dayModel.dutyDate}")
        ) {
          Icon(
            imageVector = Icons.Default.EditCalendar,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text("Day Slots")
        }
      }

      val shiftsToShow = if (spotlightShift != null) {
        listOf(spotlightShift)
      } else {
        ALL_ROSTER_SHIFTS
      }

      shiftsToShow.forEach { shiftType ->
        val cell = dayModel.shiftCells.getValue(shiftType)
        val sum = cell.summary
        val isGeneral = shiftType.isGeneralShift
        val req = if (isGeneral) {
          sum.shedMaxTarget
        } else {
          sum.pointsmanTarget + sum.shedMinTarget + sum.stationTarget + sum.medTarget
        }
        val accent = shiftAccentColor(shiftType)

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .liquidGlassSurface(
              shape = RoundedCornerShape(14.dp),
              topColor = GlassSurfaceBright,
              bottomColor = GlassSurfaceSoft,
              tintColor = accent.copy(alpha = 0.08f),
              borderWidth = 1.dp
            )
            .clickable { onInspectShift(shiftType) }
            .padding(10.dp)
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(
                imageVector = shiftIcon(shiftType),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(15.dp)
              )
              Text(
                text = "${shiftType.code} ${shiftType.displayName}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 2
              )
            }
            val summaryLabel = if (isGeneral) {
              "${cell.assignments.size}/0–$req (General Shed)"
            } else {
              val stCode = shiftType.stationArea?.shortCode ?: "ST"
              "${cell.assignments.size}/$req (PM:${sum.pointsmanFilled} · Shed:${sum.shedFilled} · $stCode:${sum.stationFilled}" +
                (if (sum.medActive) " · MED:${sum.medFilled}" else "") + ")"
            }
            Text(
              text = summaryLabel,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
              color = if (sum.isFullyCompliant) SignalEmerald else SafetyAmber
            )

            if (cell.assignments.isEmpty()) {
              Text(
                text = if (isGeneral) "No staff assigned to 08/17 General Shift (0–3 optional)" else "No staff assigned to ${shiftType.code}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            } else {
              Text(
                text = cell.assignments.joinToString(" · ") { a ->
                  val tag = when {
                    a.roleType == SlotRoleType.SHUNTER.name -> " [SH]"
                    isGeneral -> " [GEN]"
                    a.roleType == SlotRoleType.POINTSMAN.name -> " [PM]"
                    a.dutyArea == DutyArea.STATION_SC.name -> " [SC]"
                    a.dutyArea == DutyArea.STATION_UR.name -> " [UR]"
                    a.dutyArea == DutyArea.STATION_MED.name -> " [MED]"
                    else -> ""
                  }
                  shortStaffName(a.staffName) + tag
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun WeeklyGridLegendTile() {
  GlassTile(
    shape = RoundedCornerShape(18.dp),
    topColor = GlassSurfaceBright,
    bottomColor = GlassSurfaceSoft
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Tap any shift cell (22/06, 06/14, 14/22, 08/17) for full breakdown · P=Pointsman · S=Shed · SC/UR/M=Station · Gen=08/17",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
      )
    }
  }
}

@Composable
private fun WeeklyShiftCellDetailDialog(
  dayModel: DayRosterSummaryModel,
  shiftType: ShiftType,
  staffById: Map<String, StaffMember>,
  onDismiss: () -> Unit,
  onOpenDayShiftBoard: (() -> Unit)?
) {
  val cell = dayModel.shiftCells.getValue(shiftType)
  val sum = cell.summary
  val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE, dd MMM yyyy") }
  val startDate = remember(dayModel.dutyDate, shiftType) {
    shiftType.startDateForDutyDate(dayModel.dutyDate)
  }

  val pmList = remember(cell.assignments) {
    cell.assignments.filter { it.dutyArea == DutyArea.SHED.name && it.roleType == SlotRoleType.POINTSMAN.name }
  }
  val shunterList = remember(cell.assignments) {
    cell.assignments.filter { it.dutyArea == DutyArea.SHED.name && it.roleType == SlotRoleType.SHUNTER.name }
  }
  val shedList = remember(cell.assignments) {
    cell.assignments.filter {
      it.dutyArea == DutyArea.SHED.name &&
        it.roleType != SlotRoleType.POINTSMAN.name &&
        it.roleType != SlotRoleType.SHUNTER.name
    }
  }
  val stationList = remember(cell.assignments) {
    cell.assignments.filter {
      it.dutyArea == DutyArea.STATION_SC.name || it.dutyArea == DutyArea.STATION_UR.name
    }
  }
  val medList = remember(cell.assignments) {
    cell.assignments.filter { it.dutyArea == DutyArea.STATION_MED.name }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = GlassPopupSurface,
    title = {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = "${shiftType.code} ${shiftType.displayName} · ${dayModel.dutyDate.format(dateFormatter)}",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = if (shiftType == ShiftType.SHIFT_22_06) {
            "Starts ${startDate.format(dateFormatter)} at 22:00 → ${dayModel.dutyDate.format(dateFormatter)} 06:00"
          } else {
            "${shiftType.timeWindow} (${startDate.format(dateFormatter)})"
          },
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        GlassPillBadge(
          text = if (shiftType.isGeneralShift) {
            "GENERAL SHIFT (${sum.shedFilled}/0–3 STAFF)"
          } else if (sum.isFullyCompliant) {
            "OPTIMAL COVERAGE"
          } else {
            "UNDERSTAFFED / NEEDS ATTENTION"
          },
          tintColor = if (sum.isFullyCompliant) SignalEmeraldContainer else SafetyAmberContainer,
          textColor = if (sum.isFullyCompliant) SignalEmeraldOnContainer else SafetyAmberOnContainer,
          borderColor = if (sum.isFullyCompliant) SignalEmerald else SafetyAmber
        )

        if (shiftType.isGeneralShift) {
          if (shunterList.isNotEmpty()) {
            ShiftAreaSection(
              title = "General Shift Shunter (${shunterList.size}/1)",
              assignments = shunterList,
              staffById = staffById
            )
          }
          ShiftAreaSection(
            title = "General Shift Shed Personnel (${sum.shedFilled}/0–3)",
            assignments = shedList,
            staffById = staffById
          )
        } else {
          ShiftAreaSection(
            title = "Pointsman (${sum.pointsmanFilled}/1)",
            assignments = pmList,
            staffById = staffById
          )
          if (shunterList.isNotEmpty()) {
            ShiftAreaSection(
              title = "Shunter (${shunterList.size}/1)",
              assignments = shunterList,
              staffById = staffById
            )
          }
          ShiftAreaSection(
            title = "Shed Duty (${sum.shedFilled}/3–5)",
            assignments = shedList,
            staffById = staffById
          )
          shiftType.stationArea?.let { stArea ->
            ShiftAreaSection(
              title = "${stArea.label} (${sum.stationFilled}/2)",
              assignments = stationList,
              staffById = staffById
            )
          }
          if (sum.medActive) {
            ShiftAreaSection(
              title = "Medchal (MED) Night Duty (${sum.medFilled}/1 · Mon–Thu Night)",
              assignments = medList,
              staffById = staffById
            )
          } else if (shiftType == ShiftType.SHIFT_22_06) {
            Text(
              text = "Medchal (MED): Inactive (operates Monday night to Thursday night only)",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        HorizontalDivider()
        Text(
          text = "Unavailable on ${dayModel.dutyDate}:",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = "On Leave (${dayModel.onLeaveStaff.size}): " +
            if (dayModel.onLeaveStaff.isEmpty()) "None" else dayModel.onLeaveStaff.joinToString(", ") { shortStaffName(it.name) },
          style = MaterialTheme.typography.bodySmall,
          color = SignalCrimsonOnContainer
        )
        Text(
          text = "Weekly Rest (${dayModel.onRestStaff.size}): " +
            if (dayModel.onRestStaff.isEmpty()) "None" else dayModel.onRestStaff.joinToString(", ") { shortStaffName(it.name) },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    },
    confirmButton = {
      if (onOpenDayShiftBoard != null) {
        Button(
          onClick = onOpenDayShiftBoard,
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("weekly_dialog_open_day_slots_button")
        ) {
          Text("Open Day Shift Board")
        }
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.heightIn(min = 48.dp)
      ) {
        Text("Close")
      }
    }
  )
}

@Composable
private fun ShiftAreaSection(
  title: String,
  assignments: List<ShiftSlotAssignment>,
  staffById: Map<String, StaffMember>
) {
  Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
    Text(
      text = title,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.primary
    )
    if (assignments.isEmpty()) {
      Text(
        text = "• Unassigned",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    } else {
      assignments.forEach { a ->
        val member = staffById[a.staffId]
        val desig = member?.designationEnum?.label?.let { " ($it)" }.orEmpty()
        val ovr = if (a.isEmergencyOverride) " [OVERRIDE]" else ""
        Text(
          text = "• ${a.staffName}$desig$ovr",
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.Medium
        )
      }
    }
  }
}

private fun shortStaffName(fullName: String): String {
  val trimmed = fullName.trim()
  if (trimmed.length <= 13) return trimmed
  val parts = trimmed.split("\\s+".toRegex()).filter { it.isNotBlank() }
  if (parts.size <= 1) return trimmed.take(12)
  val longest = parts.maxByOrNull { it.length } ?: parts.last()
  val firstPart = parts.first()
  return if (firstPart.length <= 3 && parts.size >= 2) {
    "$firstPart ${parts[1].take(9)}"
  } else {
    "${firstPart.take(9)} ${longest.take(1)}."
  }
}

private fun shiftAccentColor(shiftType: ShiftType): Color = when (shiftType) {
  ShiftType.SHIFT_22_06 -> RailwayCobalt
  ShiftType.SHIFT_06_14 -> DepotTeal
  ShiftType.SHIFT_14_22 -> SafetyAmber
  ShiftType.SHIFT_08_17 -> SignalEmerald
}

private fun shiftIcon(shiftType: ShiftType): ImageVector = when (shiftType) {
  ShiftType.SHIFT_22_06 -> Icons.Default.NightlightRound
  ShiftType.SHIFT_06_14 -> Icons.Default.WbSunny
  ShiftType.SHIFT_14_22 -> Icons.Default.WbTwilight
  ShiftType.SHIFT_08_17 -> Icons.Default.Schedule
}
