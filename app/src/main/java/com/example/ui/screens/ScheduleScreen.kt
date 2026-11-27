package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.CandidateEvaluation
import com.example.domain.ConflictReason
import com.example.domain.ShiftCoverageSummary
import com.example.domain.ShiftSchedulerEngine
import com.example.model.DraftSlotEntry
import com.example.model.DutyArea
import com.example.model.DutyApprovalRequest
import com.example.model.DutyDateLock
import com.example.model.DutyDraftStatus
import com.example.model.LeaveRecord
import com.example.model.ShiftSlotAssignment
import com.example.model.ShiftSlotSpec
import com.example.model.ShiftType
import com.example.model.SlotRoleType
import com.example.model.StaffMember
import com.example.model.UserRole
import com.example.model.isMedDutyActive
import com.example.ui.theme.DepotTeal
import com.example.ui.theme.DepotTealContainer
import com.example.ui.theme.DepotTealOnContainer
import com.example.ui.theme.GlassIconOrb
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
import com.example.ui.theme.SignalCrimson
import com.example.ui.theme.SignalCrimsonContainer
import com.example.ui.theme.SignalCrimsonOnContainer
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.SignalEmeraldContainer
import com.example.ui.theme.SignalEmeraldOnContainer
import com.example.ui.theme.liquidGlassAccent
import com.example.ui.theme.liquidGlassSurface
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private data class ConflictDialogPayload(
  val slot: ShiftSlotSpec,
  val evaluation: CandidateEvaluation,
  val replacements: List<CandidateEvaluation>
)

@Composable
fun ScheduleScreen(
  selectedDate: LocalDate,
  staffList: List<StaffMember>,
  assignments: List<ShiftSlotAssignment>,
  leaves: List<LeaveRecord>,
  userRole: UserRole = UserRole.SUPERVISOR,
  roleAccess: com.example.model.ResolvedRoleAccess = com.example.model.RoleAccessPolicy.resolve(userRole, emptyMap()),
  showShunterForTechnicians: Boolean = false,
  dutyDrafts: List<DutyApprovalRequest> = emptyList(),
  dutyLocks: List<DutyDateLock> = emptyList(),
  stagedDraftsByDate: Map<String, List<DraftSlotEntry>> = emptyMap(),
  isBusy: Boolean,
  onSelectDate: (LocalDate) -> Unit,
  onPrevDay: () -> Unit,
  onNextDay: () -> Unit,
  onAutoAssignDay: () -> Unit,
  onAutoAssignWeek: () -> Unit,
  onAssignStaff: (slot: ShiftSlotSpec, staff: StaffMember, isOverride: Boolean, reason: String, conflictSummary: String) -> Unit,
  onClearSlot: (slotId: String) -> Unit,
  onSubmitDraftForApproval: (LocalDate) -> Unit = {},
  onDiscardDraft: (LocalDate) -> Unit = {},
  onApproveDraft: (DutyApprovalRequest) -> Unit = {},
  onRejectDraft: (DutyApprovalRequest) -> Unit = {}
) {
  val selectedDateStr = selectedDate.toString()
  val showShunterSlots = userRole != UserRole.TECHNICIAN || showShunterForTechnicians
  val daySlots = remember(selectedDate) { ShiftSchedulerEngine.buildDutyDaySlots(selectedDate) }
  val cycleSlotIds = remember(daySlots) { daySlots.mapTo(HashSet()) { it.documentId } }
  val slotsByShift = remember(daySlots) { daySlots.groupBy { it.shiftType } }

  val dateLock = remember(dutyLocks, selectedDateStr) {
    dutyLocks.firstOrNull { it.dutyDate == selectedDateStr }
  }
  val isSelectedDateLocked = remember(dateLock, assignments, cycleSlotIds) {
    dateLock != null || assignments.any { it.id in cycleSlotIds && it.approvedLocked }
  }
  val pendingDrafts = remember(dutyDrafts) {
    dutyDrafts.filter { it.statusEnum == DutyDraftStatus.PENDING }
  }
  val selectedPendingDraft = remember(pendingDrafts, selectedDateStr) {
    pendingDrafts.firstOrNull { it.dutyDate == selectedDateStr }
  }
  val selectedRejectedDraft = remember(dutyDrafts, selectedDateStr) {
    dutyDrafts.firstOrNull { it.dutyDate == selectedDateStr && it.statusEnum == DutyDraftStatus.REJECTED }
  }
  val hasUnsentLocalStaged = roleAccess.canDraftDuties && stagedDraftsByDate.containsKey(selectedDateStr)

  // Draft-level roles see their draft overlay for unlocked dates; everyone else sees published DB duties.
  val displayedAssignments = remember(
    roleAccess,
    isSelectedDateLocked,
    stagedDraftsByDate,
    selectedPendingDraft,
    selectedDateStr,
    assignments,
    cycleSlotIds
  ) {
    if (roleAccess.canDraftDuties && !isSelectedDateLocked) {
      val draftEntries = stagedDraftsByDate[selectedDateStr] ?: selectedPendingDraft?.proposedSlots
      if (draftEntries != null) {
        val outsideCycle = assignments.filterNot { it.id in cycleSlotIds }
        outsideCycle + draftEntries.map { it.toAssignment(depotId = "", approverName = "").copy(approvedLocked = false) }
      } else {
        assignments
      }
    } else {
      assignments
    }
  }

  val canEditCurrentDay = when {
    roleAccess.canDirectEditDuties -> true
    roleAccess.canDraftDuties -> !isSelectedDateLocked
    else -> false
  }

  val coverageSummaries = remember(selectedDate, displayedAssignments) {
    ShiftSchedulerEngine.computeDutyDayCoverage(selectedDate, displayedAssignments)
  }
  val summaryByShift = remember(coverageSummaries) { coverageSummaries.associateBy { it.shiftType } }
  val assignmentById = remember(displayedAssignments) { displayedAssignments.associateBy { it.id } }
  val staffById = remember(staffList) { staffList.associateBy { it.id } }

  val conflictsBySlotId = remember(selectedDate, daySlots, assignmentById, staffById, displayedAssignments, leaves) {
    val minWindowStr = selectedDate.minusDays(2).toString()
    val maxWindowStr = selectedDate.plusDays(1).toString()

    val windowAssignmentsByStaff = displayedAssignments
      .filter { it.date in minWindowStr..maxWindowStr }
      .groupBy { it.staffId }
    // Every slot in buildDutyDaySlots(selectedDate) — including yesterday's 22/06 night shift —
    // belongs to selectedDate's operational duty cycle.
    val activeLeavesCurrentByStaff = leaves
      .filter { it.isBlockingDate(selectedDateStr) }
      .groupBy { it.staffId }

    buildMap<String, List<ConflictReason>> {
      for (slot in daySlots) {
        val assignment = assignmentById[slot.documentId] ?: continue
        val staff = staffById[assignment.staffId] ?: continue
        val slotDate = slot.shiftType.startDateForDutyDate(selectedDate)
        val staffAssignments = windowAssignmentsByStaff[staff.id] ?: emptyList()
        val staffLeaves = activeLeavesCurrentByStaff[staff.id] ?: emptyList()
        put(
          slot.documentId,
          ShiftSchedulerEngine.detectConflicts(
            staff = staff,
            slot = slot,
            targetDate = slotDate,
            assignments = staffAssignments,
            leaves = staffLeaves
          )
        )
      }
    }
  }

  var conflictPayload by remember { mutableStateOf<ConflictDialogPayload?>(null) }
  var showWeeklyCalendarGrid by remember { mutableStateOf(false) }

  Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    ScheduleViewModeToggleBar(
      showWeeklyCalendarGrid = showWeeklyCalendarGrid,
      onSelectMode = { showWeeklyCalendarGrid = it },
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 780.dp)
        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
    )

    if (showWeeklyCalendarGrid) {
      WeeklyRosterCalendarGrid(
        selectedDate = selectedDate,
        staffList = staffList,
        assignments = displayedAssignments,
        leaves = leaves,
        dutyLocks = dutyLocks,
        userRole = userRole,
        roleAccess = roleAccess,
        showShunterForTechnicians = showShunterForTechnicians,
        isBusy = isBusy,
        onSelectDate = onSelectDate,
        onOpenDayShiftBoard = { targetDate ->
          onSelectDate(targetDate)
          showWeeklyCalendarGrid = false
        },
        onAutoAssignWeek = onAutoAssignWeek,
        modifier = Modifier.weight(1f)
      )
    } else {
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
      ) {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 780.dp),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          if (roleAccess.canApproveDuties && pendingDrafts.isNotEmpty()) {
            item(key = "pending_approvals_panel", contentType = "approval_panel") {
          SupervisorPendingApprovalsPanel(
            pendingDrafts = pendingDrafts,
            isBusy = isBusy,
            onViewDate = { dateStr ->
              runCatching { LocalDate.parse(dateStr) }.getOrNull()?.let(onSelectDate)
            },
            onApproveDraft = onApproveDraft,
            onRejectDraft = onRejectDraft
          )
        }
      }

      item(key = "schedule_header", contentType = "header") {
        DateNavigationHeader(
          selectedDate = selectedDate,
          userRole = userRole,
          roleAccess = roleAccess,
          isSelectedDateLocked = isSelectedDateLocked,
          lockedByName = dateLock?.lockedByName,
          isBusy = isBusy,
          onSelectDate = onSelectDate,
          onPrevDay = onPrevDay,
          onNextDay = onNextDay,
          onAutoAssignDay = onAutoAssignDay,
          onAutoAssignWeek = onAutoAssignWeek
        )
      }

      if (roleAccess.canDraftDuties) {
        item(key = "storemate_draft_banner", contentType = "draft_banner") {
          val cycleDraftCount = cycleSlotIds.count { assignmentById.containsKey(it) }
          StoremateDraftStatusCard(
            selectedDate = selectedDate,
            isLocked = isSelectedDateLocked,
            lockedByName = dateLock?.lockedByName,
            hasUnsentLocalStaged = hasUnsentLocalStaged,
            pendingDraft = selectedPendingDraft,
            rejectedDraft = selectedRejectedDraft,
            draftedSlotCount = cycleDraftCount,
            isBusy = isBusy,
            onSubmitForApproval = { onSubmitDraftForApproval(selectedDate) },
            onDiscardLocal = { onDiscardDraft(selectedDate) }
          )
        }
      } else if (!roleAccess.canDirectEditDuties) {
        item(key = "technician_readonly_banner", contentType = "readonly_banner") {
          GlassTile(
            shape = RoundedCornerShape(20.dp),
            topColor = GlassSurfaceBright,
            bottomColor = GlassSurfaceSoft
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              GlassIconOrb(
                shape = RoundedCornerShape(12.dp),
                tintColor = DepotTealContainer.copy(alpha = 0.6f),
                modifier = Modifier.size(38.dp)
              ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = DepotTeal)
              }
              Column {
                Text(
                  text = "Read-Only Depot Schedule (${userRole.label})",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = "Published shifts are shared live across all staff. Apply for leave in the Leave tab.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        }
      }

      item(key = "schedule_busy_bar", contentType = "busy_indicator") {
        androidx.compose.animation.AnimatedVisibility(visible = isBusy) {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
      }

      items(
        items = ShiftType.entries,
        key = { it.name },
        contentType = { "shift_board" }
      ) { shift ->
        val summary = summaryByShift.getValue(shift)
        val shiftSlots = slotsByShift[shift].orEmpty()
        ShiftBoardCard(
          shiftType = shift,
          selectedDate = selectedDate,
          summary = summary,
          slots = shiftSlots,
          assignmentById = assignmentById,
          staffById = staffById,
          conflictsBySlotId = conflictsBySlotId,
          staffList = staffList,
          allAssignments = displayedAssignments,
          leaves = leaves,
          canEdit = canEditCurrentDay,
          showShunterSlots = showShunterSlots,
          onSelectCandidate = { slot, candidate ->
            if (!canEditCurrentDay) return@ShiftBoardCard
            if (candidate.isFullyEligible) {
              onAssignStaff(slot, candidate.staff, false, "", "")
            } else {
              val replacements = ShiftSchedulerEngine.suggestReplacements(
                slot = slot,
                staffList = staffList,
                assignments = displayedAssignments,
                leaves = leaves,
                excludeStaffId = candidate.staff.id,
                limit = 3
              )
              conflictPayload = ConflictDialogPayload(slot, candidate, replacements)
            }
          },
          onClearSlot = { slotId ->
            if (canEditCurrentDay) onClearSlot(slotId)
          },
          onInspectExistingConflict = { slot, assignedStaff ->
            if (!canEditCurrentDay) return@ShiftBoardCard
            val eval = ShiftSchedulerEngine.evaluateCandidatesForSlot(slot, staffList, displayedAssignments, leaves)
              .firstOrNull { it.staff.id == assignedStaff.id }
            if (eval != null && !eval.isFullyEligible) {
              val replacements = ShiftSchedulerEngine.suggestReplacements(
                slot = slot,
                staffList = staffList,
                assignments = displayedAssignments,
                leaves = leaves,
                excludeStaffId = assignedStaff.id,
                limit = 3
              )
              conflictPayload = ConflictDialogPayload(slot, eval, replacements)
            }
          }
        )
      }

      item(key = "schedule_bottom_spacer", contentType = "spacer") {
        Spacer(modifier = Modifier.height(16.dp))
      }
    }
      }
    }
  }

  conflictPayload?.let { payload ->
    ConflictResolutionDialog(
      payload = payload,
      onDismiss = { conflictPayload = null },
      onPickReplacement = { replacement ->
        onAssignStaff(payload.slot, replacement.staff, false, "", "")
        conflictPayload = null
      },
      onConfirmOverride = { reason ->
        val summary = payload.evaluation.conflicts.joinToString("; ") { it.label }
        onAssignStaff(payload.slot, payload.evaluation.staff, true, reason, summary)
        conflictPayload = null
      }
    )
  }
}

@Composable
private fun ScheduleViewModeToggleBar(
  showWeeklyCalendarGrid: Boolean,
  onSelectMode: (Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  GlassTile(
    modifier = modifier,
    shape = RoundedCornerShape(18.dp),
    topColor = GlassSurfaceBright,
    bottomColor = GlassSurfaceMid
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(6.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      val options = listOf(
        false to "Daily Shift Board",
        true to "Weekly Calendar Grid"
      )
      options.forEach { (isWeekly, label) ->
        val selected = showWeeklyCalendarGrid == isWeekly
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
            .clickable { onSelectMode(isWeekly) }
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(if (isWeekly) "view_mode_weekly_calendar" else "view_mode_daily_board"),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DateNavigationHeader(
  selectedDate: LocalDate,
  userRole: UserRole,
  roleAccess: com.example.model.ResolvedRoleAccess = com.example.model.RoleAccessPolicy.resolve(userRole, emptyMap()),
  isSelectedDateLocked: Boolean,
  lockedByName: String?,
  isBusy: Boolean,
  onSelectDate: (LocalDate) -> Unit,
  onPrevDay: () -> Unit,
  onNextDay: () -> Unit,
  onAutoAssignDay: () -> Unit,
  onAutoAssignWeek: () -> Unit
) {
  val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE, dd MMM yyyy") }
  val shortDateFormatter = remember { DateTimeFormatter.ofPattern("dd MMM") }
  val prevDate = remember(selectedDate) { selectedDate.minusDays(1) }
  val nightDateShort = remember(prevDate) { prevDate.format(shortDateFormatter) }
  val dutyDateShort = remember(selectedDate) { selectedDate.format(shortDateFormatter) }
  val isMedActiveForCycle = prevDate.isMedDutyActive()
  val weekDays = remember(selectedDate) {
    (-2L..4L).map { selectedDate.plusDays(it) }
  }

  GlassTile(
    shape = RoundedCornerShape(24.dp),
    topColor = GlassSurfaceBright,
    bottomColor = GlassSurfaceMid
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        IconButton(
          onClick = onPrevDay,
          modifier = Modifier
            .size(48.dp)
            .testTag("prev_day_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = "Previous day"
          )
        }

        Column(
          modifier = Modifier.weight(1f),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
              imageVector = Icons.Default.CalendarToday,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = selectedDate.format(dateFormatter),
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface
            )
          }
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = "$nightDateShort (22/06) → $dutyDateShort (06/14, 14/22, 08/17)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2
          )
          Spacer(modifier = Modifier.height(6.dp))
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            val nightDayShort = prevDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase()
            GlassPillBadge(
              text = if (isMedActiveForCycle) {
                "MEDCHAL (MED) · $nightDayShort NIGHT"
              } else {
                "NO MEDCHAL ($nightDayShort NIGHT)"
              },
              tintColor = if (isMedActiveForCycle) DepotTealContainer.copy(alpha = 0.65f) else GlassSurfaceSoft,
              textColor = if (isMedActiveForCycle) DepotTealOnContainer else MaterialTheme.colorScheme.onSurfaceVariant,
              borderColor = if (isMedActiveForCycle) DepotTeal else GlassRimHighlight
            )
            if (isSelectedDateLocked) {
              GlassPillBadge(
                text = if (lockedByName.isNullOrBlank()) "APPROVED · LOCKED" else "LOCKED BY ${lockedByName.uppercase()}",
                tintColor = SignalEmeraldContainer.copy(alpha = 0.72f),
                textColor = SignalEmeraldOnContainer,
                borderColor = SignalEmerald
              )
            }
          }
        }

        IconButton(
          onClick = onNextDay,
          modifier = Modifier
            .size(48.dp)
            .testTag("next_day_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Next day"
          )
        }
      }

      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(weekDays, key = { it.toString() }) { day ->
          val isSelected = day == selectedDate
          val chipModifier = if (isSelected) {
            Modifier.liquidGlassAccent(shape = RoundedCornerShape(16.dp))
          } else {
            Modifier.liquidGlassSurface(
              shape = RoundedCornerShape(16.dp),
              topColor = GlassSurfaceBright,
              bottomColor = GlassSurfaceSoft,
              borderWidth = 1.dp
            )
          }
          Box(
            modifier = chipModifier
              .heightIn(min = 48.dp)
              .clickable { onSelectDate(day) }
              .testTag("date_chip_${day}"),
            contentAlignment = Alignment.Center
          ) {
            Column(
              modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Text(
                text = day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = day.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleSmall,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
              )
            }
          }
        }
      }

      if (roleAccess.canDirectEditDuties) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Button(
            onClick = onAutoAssignDay,
            enabled = !isBusy,
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = if (!isBusy) Color.Transparent else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
              contentColor = Color.White
            ),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 48.dp)
              .let { mod -> if (!isBusy) mod.liquidGlassAccent(shape = RoundedCornerShape(16.dp)) else mod }
              .testTag("auto_assign_day_button"),
            shape = RoundedCornerShape(16.dp)
          ) {
            Icon(
              imageVector = Icons.Default.AutoAwesome,
              contentDescription = null,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Auto-Assign Day", maxLines = 2, textAlign = TextAlign.Center)
          }

          OutlinedButton(
            onClick = onAutoAssignWeek,
            enabled = !isBusy,
            colors = ButtonDefaults.outlinedButtonColors(containerColor = GlassSurfaceSoft),
            border = BorderStroke(1.dp, GlassRimHighlight),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 48.dp)
              .testTag("auto_assign_week_button"),
            shape = RoundedCornerShape(16.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Schedule,
              contentDescription = null,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Auto-Assign Week", maxLines = 2, textAlign = TextAlign.Center)
          }
        }
      } else if (roleAccess.canDraftDuties && !isSelectedDateLocked) {
        Button(
          onClick = onAutoAssignDay,
          enabled = !isBusy,
          elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = if (!isBusy) Color.Transparent else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            contentColor = Color.White
          ),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .let { mod -> if (!isBusy) mod.liquidGlassAccent(shape = RoundedCornerShape(16.dp)) else mod }
            .testTag("auto_assign_day_button"),
          shape = RoundedCornerShape(16.dp)
        ) {
          Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Auto-Fill Draft Shifts (For Approval)", maxLines = 2, textAlign = TextAlign.Center)
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShiftBoardCard(
  shiftType: ShiftType,
  selectedDate: LocalDate,
  summary: ShiftCoverageSummary,
  slots: List<ShiftSlotSpec>,
  assignmentById: Map<String, ShiftSlotAssignment>,
  staffById: Map<String, StaffMember>,
  conflictsBySlotId: Map<String, List<ConflictReason>>,
  staffList: List<StaffMember>,
  allAssignments: List<ShiftSlotAssignment>,
  leaves: List<LeaveRecord>,
  canEdit: Boolean,
  showShunterSlots: Boolean = true,
  onSelectCandidate: (ShiftSlotSpec, CandidateEvaluation) -> Unit,
  onClearSlot: (String) -> Unit,
  onInspectExistingConflict: (ShiftSlotSpec, StaffMember) -> Unit
) {
  val shiftIcon = when (shiftType) {
    ShiftType.SHIFT_06_14 -> Icons.Default.WbSunny
    ShiftType.SHIFT_14_22 -> Icons.Default.WbTwilight
    ShiftType.SHIFT_22_06 -> Icons.Default.NightlightRound
    ShiftType.SHIFT_08_17 -> Icons.Default.Schedule
  }
  val shiftDate = remember(selectedDate, shiftType) { shiftType.startDateForDutyDate(selectedDate) }
  val shiftDateFormatter = remember { DateTimeFormatter.ofPattern("EEE, dd MMM") }
  val shortDutyFormatter = remember { DateTimeFormatter.ofPattern("dd MMM") }
  val shiftDateLabel = remember(shiftDate) { shiftDate.format(shiftDateFormatter) }
  val dutyDateShortLabel = remember(selectedDate) { selectedDate.format(shortDutyFormatter) }
  val shiftSubtitle = remember(shiftType, shiftDateLabel, dutyDateShortLabel) {
    if (shiftType == ShiftType.SHIFT_22_06) {
      "${shiftType.timeWindow} · Starts $shiftDateLabel ($dutyDateShortLabel Duty)"
    } else {
      "${shiftType.timeWindow} · $shiftDateLabel"
    }
  }

  val pointsmanSlots = remember(slots) {
    slots.filter { it.dutyArea == DutyArea.SHED && it.roleType == SlotRoleType.POINTSMAN }
  }
  val shunterSlots = remember(slots, showShunterSlots) {
    if (showShunterSlots) {
      slots.filter { it.dutyArea == DutyArea.SHED && it.roleType == SlotRoleType.SHUNTER }
    } else {
      emptyList()
    }
  }
  val shedGeneralSlots = remember(slots) {
    slots.filter {
      it.dutyArea == DutyArea.SHED &&
        it.roleType != SlotRoleType.POINTSMAN &&
        it.roleType != SlotRoleType.SHUNTER
    }
  }
  val stationArea = shiftType.stationArea
  val stationSlots = remember(slots, stationArea) {
    if (stationArea != null) slots.filter { it.dutyArea == stationArea } else emptyList()
  }
  val medSlots = remember(slots) { slots.filter { it.dutyArea == DutyArea.STATION_MED } }

  GlassTile(
    shape = RoundedCornerShape(24.dp),
    topColor = GlassSurfaceBright,
    bottomColor = GlassSurfaceMid
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier
            .weight(1f)
            .padding(end = 8.dp)
        ) {
          GlassIconOrb(
            shape = RoundedCornerShape(14.dp),
            tintColor = RailwayCobalt.copy(alpha = 0.14f),
            modifier = Modifier.size(44.dp)
          ) {
            Icon(
              imageVector = shiftIcon,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary
            )
          }
          Column {
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
              Text(
                text = shiftType.code,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
              )
              Text(
                text = shiftType.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
              )
            }
            Text(
              text = shiftSubtitle,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        CompliancePill(
          label = if (shiftType.isGeneralShift) {
            "${summary.shedFilled}/0–3 STAFF"
          } else if (summary.isFullyCompliant) {
            "OPTIMAL"
          } else {
            "NEEDS STAFF"
          },
          isCompliant = summary.isFullyCompliant
        )
      }

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        if (shiftType.isGeneralShift) {
          CoverageMiniBadge(
            label = "General ${summary.shedFilled}/0–3",
            isOk = summary.isShedCompliant
          )
          if (showShunterSlots) {
            CoverageMiniBadge(
              label = "Shunter ${summary.shunterFilled}/1",
              isOk = true
            )
          }
        } else {
          CoverageMiniBadge(
            label = "PM ${summary.pointsmanFilled}/1",
            isOk = summary.isPointsmanCompliant
          )
          if (showShunterSlots) {
            CoverageMiniBadge(
              label = "SH ${summary.shunterFilled}/1",
              isOk = true
            )
          }
          CoverageMiniBadge(
            label = "Shed ${summary.shedFilled}/3–5",
            isOk = summary.isShedCompliant
          )
          if (stationArea != null) {
            CoverageMiniBadge(
              label = "${stationArea.shortCode} ${summary.stationFilled}/2",
              isOk = summary.isStationCompliant
            )
          }
          if (summary.medActive) {
            CoverageMiniBadge(
              label = "MED ${summary.medFilled}/1",
              isOk = summary.isMedCompliant
            )
          }
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))

      if (shiftType.isGeneralShift) {
        SectionHeaderTitle(
          title = if (showShunterSlots) {
            "General Shift Staff (0–3 Personnel + Shunter)"
          } else {
            "General Shift Staff (0–3 Personnel)"
          },
          subtitle = "Optional daytime shed support depending on availability"
        )
        shunterSlots.forEach { slot ->
          val slotAssignment = assignmentById[slot.documentId]
          SlotDropdownSelector(
            slot = slot,
            assignment = slotAssignment,
            assignedStaff = slotAssignment?.let { staffById[it.staffId] },
            liveConflicts = conflictsBySlotId[slot.documentId].orEmpty(),
            staffList = staffList,
            allAssignments = allAssignments,
            leaves = leaves,
            canEdit = canEdit,
            onSelectCandidate = { eval -> onSelectCandidate(slot, eval) },
            onClearSlot = { onClearSlot(slot.documentId) },
            onInspectConflict = { assigned -> onInspectExistingConflict(slot, assigned) }
          )
        }
        shedGeneralSlots.forEach { slot ->
          val slotAssignment = assignmentById[slot.documentId]
          SlotDropdownSelector(
            slot = slot,
            assignment = slotAssignment,
            assignedStaff = slotAssignment?.let { staffById[it.staffId] },
            liveConflicts = conflictsBySlotId[slot.documentId].orEmpty(),
            staffList = staffList,
            allAssignments = allAssignments,
            leaves = leaves,
            canEdit = canEdit,
            onSelectCandidate = { eval -> onSelectCandidate(slot, eval) },
            onClearSlot = { onClearSlot(slot.documentId) },
            onInspectConflict = { assigned -> onInspectExistingConflict(slot, assigned) }
          )
        }
      } else {
        pointsmanSlots.forEach { slot ->
          val slotAssignment = assignmentById[slot.documentId]
          SlotDropdownSelector(
            slot = slot,
            assignment = slotAssignment,
            assignedStaff = slotAssignment?.let { staffById[it.staffId] },
            liveConflicts = conflictsBySlotId[slot.documentId].orEmpty(),
            staffList = staffList,
            allAssignments = allAssignments,
            leaves = leaves,
            canEdit = canEdit,
            onSelectCandidate = { eval -> onSelectCandidate(slot, eval) },
            onClearSlot = { onClearSlot(slot.documentId) },
            onInspectConflict = { assigned -> onInspectExistingConflict(slot, assigned) }
          )
        }
        shunterSlots.forEach { slot ->
          val slotAssignment = assignmentById[slot.documentId]
          SlotDropdownSelector(
            slot = slot,
            assignment = slotAssignment,
            assignedStaff = slotAssignment?.let { staffById[it.staffId] },
            liveConflicts = conflictsBySlotId[slot.documentId].orEmpty(),
            staffList = staffList,
            allAssignments = allAssignments,
            leaves = leaves,
            canEdit = canEdit,
            onSelectCandidate = { eval -> onSelectCandidate(slot, eval) },
            onClearSlot = { onClearSlot(slot.documentId) },
            onInspectConflict = { assigned -> onInspectExistingConflict(slot, assigned) }
          )
        }
        shedGeneralSlots.forEach { slot ->
          val slotAssignment = assignmentById[slot.documentId]
          SlotDropdownSelector(
            slot = slot,
            assignment = slotAssignment,
            assignedStaff = slotAssignment?.let { staffById[it.staffId] },
            liveConflicts = conflictsBySlotId[slot.documentId].orEmpty(),
            staffList = staffList,
            allAssignments = allAssignments,
            leaves = leaves,
            canEdit = canEdit,
            onSelectCandidate = { eval -> onSelectCandidate(slot, eval) },
            onClearSlot = { onClearSlot(slot.documentId) },
            onInspectConflict = { assigned -> onInspectExistingConflict(slot, assigned) }
          )
        }

        if (stationArea != null) {
          HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
          SectionHeaderTitle(
            title = "${stationArea.label} (2 Personnel)"
          )
          stationSlots.forEach { slot ->
            val slotAssignment = assignmentById[slot.documentId]
            SlotDropdownSelector(
              slot = slot,
              assignment = slotAssignment,
              assignedStaff = slotAssignment?.let { staffById[it.staffId] },
              liveConflicts = conflictsBySlotId[slot.documentId].orEmpty(),
              staffList = staffList,
              allAssignments = allAssignments,
              leaves = leaves,
              canEdit = canEdit,
              onSelectCandidate = { eval -> onSelectCandidate(slot, eval) },
              onClearSlot = { onClearSlot(slot.documentId) },
              onInspectConflict = { assigned -> onInspectExistingConflict(slot, assigned) }
            )
          }
        }
      }

      if (shiftType == ShiftType.SHIFT_22_06) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
        if (shiftDate.isMedDutyActive()) {
          SectionHeaderTitle(
            title = "Medchal (MED) Night Duty (1 Personnel · Mon–Thu Night)"
          )
          medSlots.forEach { slot ->
            val slotAssignment = assignmentById[slot.documentId]
            SlotDropdownSelector(
              slot = slot,
              assignment = slotAssignment,
              assignedStaff = slotAssignment?.let { staffById[it.staffId] },
              liveConflicts = conflictsBySlotId[slot.documentId].orEmpty(),
              staffList = staffList,
              allAssignments = allAssignments,
              leaves = leaves,
              canEdit = canEdit,
              onSelectCandidate = { eval -> onSelectCandidate(slot, eval) },
              onClearSlot = { onClearSlot(slot.documentId) },
              onInspectConflict = { assigned -> onInspectExistingConflict(slot, assigned) }
            )
          }
        } else {
          GlassTile(
            shape = RoundedCornerShape(16.dp),
            topColor = GlassSurfaceBright,
            bottomColor = GlassSurfaceSoft,
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = "Medchal (MED) operates Monday night to Thursday night only (No MED on Fri, Sat, or Sun nights).",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(12.dp)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SectionHeaderTitle(title: String, subtitle: String? = null) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.onSurface
    )
    if (!subtitle.isNullOrBlank()) {
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlotDropdownSelector(
  slot: ShiftSlotSpec,
  assignment: ShiftSlotAssignment?,
  assignedStaff: StaffMember?,
  liveConflicts: List<ConflictReason>,
  staffList: List<StaffMember>,
  allAssignments: List<ShiftSlotAssignment>,
  leaves: List<LeaveRecord>,
  canEdit: Boolean,
  onSelectCandidate: (CandidateEvaluation) -> Unit,
  onClearSlot: () -> Unit,
  onInspectConflict: (StaffMember) -> Unit
) {
  var expanded by remember { mutableStateOf(false) }
  var showUnavailableSection by remember { mutableStateOf(false) }

  val hasLeaveConflict = liveConflicts.any { it.isHardExclusion }
  val isOverride = assignment?.isEmergencyOverride == true

  val slotTint = when {
    hasLeaveConflict -> SignalCrimsonContainer.copy(alpha = 0.55f)
    isOverride || liveConflicts.isNotEmpty() -> SafetyAmberContainer.copy(alpha = 0.55f)
    assignment != null -> SignalEmeraldContainer.copy(alpha = 0.25f)
    else -> Color.Transparent
  }

  Box(modifier = Modifier.fillMaxWidth()) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp)
        .liquidGlassSurface(
          shape = RoundedCornerShape(16.dp),
          topColor = if (assignment != null) GlassSurfaceBright else GlassSurfaceSoft,
          bottomColor = GlassSurfaceSoft,
          tintColor = slotTint,
          borderWidth = 1.dp
        )
        .then(
          if (canEdit) {
            Modifier.clickable {
              showUnavailableSection = false
              expanded = true
            }
          } else {
            Modifier
          }
        )
        .testTag("slot_dropdown_${slot.documentId}")
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Text(
              text = slot.label,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (slot.roleType == SlotRoleType.POINTSMAN) {
              Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(4.dp)
              ) {
                Text(
                  text = "POINTSMAN ONLY",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
              }
            } else if (slot.roleType == SlotRoleType.SHUNTER) {
              Surface(
                color = DepotTealContainer,
                shape = RoundedCornerShape(4.dp)
              ) {
                Text(
                  text = "SHUNTER",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = DepotTealOnContainer,
                  modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
              }
            }
          }

          if (assignment != null) {
            Text(
              text = assignment.staffName + (assignedStaff?.let { " (${it.designationEnum.label})" } ?: ""),
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurface
            )
            if (isOverride) {
              Text(
                text = "OVERRIDE: ${assignment.overrideReason.ifBlank { "Manual emergency" }}",
                style = MaterialTheme.typography.bodySmall,
                color = SafetyAmberOnContainer
              )
            } else if (liveConflicts.isNotEmpty()) {
              Text(
                text = "CONFLICT: ${liveConflicts.first().label}" + if (canEdit) " — Tap to resolve" else "",
                style = MaterialTheme.typography.bodySmall,
                color = SignalCrimsonOnContainer
              )
            } else if (assignment.approvedLocked && assignment.approvedByName.isNotBlank()) {
              Text(
                text = "Approved by ${assignment.approvedByName}",
                style = MaterialTheme.typography.labelSmall,
                color = SignalEmeraldOnContainer
              )
            }
          } else {
            Text(
              text = when {
                !canEdit -> "Unassigned"
                slot.roleType == SlotRoleType.SHUNTER -> "Select Shunter..."
                slot.isRequired -> "Select eligible staff..."
                slot.shiftType.isGeneralShift -> "Optional General Shift staff..."
                else -> "Optional Shed staff..."
              },
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        if (canEdit) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            if (liveConflicts.isNotEmpty() && assignedStaff != null) {
              IconButton(
                onClick = { onInspectConflict(assignedStaff) },
                modifier = Modifier.size(40.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Warning,
                  contentDescription = "Resolve conflict",
                  tint = SignalCrimson
                )
              }
            }
            if (assignment != null) {
              IconButton(
                onClick = onClearSlot,
                modifier = Modifier
                  .size(40.dp)
                  .testTag("clear_slot_${slot.documentId}")
              ) {
                Icon(
                  imageVector = Icons.Default.Clear,
                  contentDescription = "Clear slot",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
            Icon(
              imageVector = Icons.Default.KeyboardArrowDown,
              contentDescription = "Open staff dropdown",
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        } else if (assignment?.approvedLocked == true) {
          Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Approved & locked",
            tint = SignalEmerald,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }

    if (expanded && canEdit) {
      val evaluations = remember(slot, staffList, allAssignments, leaves) {
        ShiftSchedulerEngine.evaluateCandidatesForSlot(slot, staffList, allAssignments, leaves)
      }
      val eligible = evaluations.filter { it.isFullyEligible }
      val conflictedNonLeave = evaluations.filter { !it.isFullyEligible && !it.isOnLeave }
      val onLeave = evaluations.filter { it.isOnLeave }
      val unavailableCount = conflictedNonLeave.size + onLeave.size
      val dismissPicker = {
        expanded = false
        showUnavailableSection = false
      }

      Dialog(
        onDismissRequest = dismissPicker,
        properties = DialogProperties(
          usePlatformDefaultWidth = false,
          decorFitsSystemWindows = true
        )
      ) {
        BoxWithConstraints(
          modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
              onClick = dismissPicker
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
          val bottomNavClearance = if (maxHeight >= 520.dp) 84.dp else 8.dp
          val dialogMaxHeight = (maxHeight - bottomNavClearance).coerceAtLeast(220.dp)

          Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(bottom = bottomNavClearance),
            contentAlignment = Alignment.Center
          ) {
            Surface(
              shape = RoundedCornerShape(24.dp),
              color = GlassPopupSurface,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
              tonalElevation = 8.dp,
              modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .heightIn(max = dialogMaxHeight)
                .clickable(
                  interactionSource = remember { MutableInteractionSource() },
                  indication = null,
                  onClick = {}
                )
                .testTag("slot_staff_picker_dialog")
            ) {
              Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = slot.label,
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                      text = "Select staff for this slot",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  IconButton(
                    onClick = dismissPicker,
                    modifier = Modifier.size(48.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Default.Close,
                      contentDescription = "Close",
                      tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
                HorizontalDivider()

                Column(
                  modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  if (assignment != null) {
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                          dismissPicker()
                          onClearSlot()
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                      Icon(Icons.Default.Clear, contentDescription = null, tint = SignalCrimson)
                      Text(
                        text = "Clear Assignment",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = SignalCrimson
                      )
                    }
                    HorizontalDivider()
                  }

                  Text(
                    text = stringResource(R.string.dropdown_available_staff_header, eligible.size),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = SignalEmerald,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                  )

                  if (eligible.isEmpty()) {
                    Text(
                      text = stringResource(
                        if (slot.roleType == SlotRoleType.SHUNTER) {
                          R.string.dropdown_no_available_shunter
                        } else {
                          R.string.dropdown_no_available_staff
                        }
                      ),
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                    )
                  } else {
                    eligible.forEach { eval ->
                      Row(
                        modifier = Modifier
                          .fillMaxWidth()
                          .heightIn(min = 48.dp)
                          .clip(RoundedCornerShape(10.dp))
                          .background(color = SignalEmeraldContainer.copy(alpha = 0.48f))
                          .clickable {
                            dismissPicker()
                            onSelectCandidate(eval)
                          }
                          .padding(horizontal = 10.dp, vertical = 8.dp)
                          .testTag("dropdown_available_staff_${eval.staff.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                      ) {
                        Icon(
                          imageVector = Icons.Default.CheckCircle,
                          contentDescription = stringResource(R.string.dropdown_available_staff_cd),
                          tint = SignalEmerald
                        )
                        Column(modifier = Modifier.weight(1f)) {
                          Text(
                            text = "${eval.staff.name} · ${eval.staff.designationEnum.label}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = SignalEmeraldOnContainer
                          )
                          Text(
                            text = "Week shifts: ${eval.weeklyShiftCount}" +
                              if (eval.staff.isPointsman) " · Pointsman" else "",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = SignalEmerald
                          )
                        }
                      }
                    }
                  }

                  if (unavailableCount > 0) {
                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                    val collapsedSummary = buildList {
                      if (conflictedNonLeave.isNotEmpty()) add("${conflictedNonLeave.size} conflicted")
                      if (onLeave.isNotEmpty()) add("${onLeave.size} on leave")
                    }.joinToString(" · ")

                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showUnavailableSection = !showUnavailableSection }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("toggle_unavailable_staff_${slot.documentId}"),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                      Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = SafetyAmber
                      )
                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = stringResource(R.string.dropdown_unavailable_toggle_title, unavailableCount),
                          style = MaterialTheme.typography.labelLarge,
                          fontWeight = FontWeight.SemiBold,
                          color = SafetyAmberOnContainer
                        )
                        Text(
                          text = if (showUnavailableSection) {
                            stringResource(R.string.dropdown_tap_to_collapse)
                          } else {
                            "Tap to expand · $collapsedSummary"
                          },
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                      }
                      Icon(
                        imageVector = if (showUnavailableSection) {
                          Icons.Default.KeyboardArrowUp
                        } else {
                          Icons.Default.KeyboardArrowDown
                        },
                        contentDescription = stringResource(
                          if (showUnavailableSection) {
                            R.string.dropdown_collapse_unavailable_cd
                          } else {
                            R.string.dropdown_expand_unavailable_cd
                          }
                        ),
                        tint = SafetyAmberOnContainer
                      )
                    }

                    AnimatedVisibility(visible = showUnavailableSection) {
                      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (conflictedNonLeave.isNotEmpty()) {
                          HorizontalDivider()
                          Text(
                            text = stringResource(
                              R.string.dropdown_conflicted_subheader,
                              conflictedNonLeave.size
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = SafetyAmber,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                          )
                          conflictedNonLeave.forEach { eval ->
                            Row(
                              modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                  dismissPicker()
                                  onSelectCandidate(eval)
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .testTag("dropdown_conflicted_staff_${eval.staff.id}"),
                              verticalAlignment = Alignment.CenterVertically,
                              horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                              Icon(Icons.Default.Warning, contentDescription = null, tint = SafetyAmber)
                              Column(modifier = Modifier.weight(1f)) {
                                Text(
                                  text = "${eval.staff.name} · ${eval.staff.designationEnum.label}",
                                  style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                  text = eval.primaryConflictLabel.orEmpty(),
                                  style = MaterialTheme.typography.bodySmall,
                                  color = SafetyAmberOnContainer
                                )
                              }
                            }
                          }
                        }

                        if (onLeave.isNotEmpty()) {
                          HorizontalDivider()
                          Text(
                            text = stringResource(R.string.dropdown_on_leave_subheader, onLeave.size),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = SignalCrimson,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                          )
                          val slotDutyDateStr = remember(slot.date, slot.shiftType) {
                            slot.shiftType.dutyDateForStartDate(LocalDate.parse(slot.date)).toString()
                          }
                          onLeave.forEach { eval ->
                            val activeLeave = leaves.firstOrNull {
                              it.staffId == eval.staff.id && it.isBlockingDate(slotDutyDateStr)
                            }
                            val leaveBadge = if (activeLeave != null) {
                              "${activeLeave.leaveTypeEnum.code} · ${activeLeave.startDate} to ${activeLeave.endDate}"
                            } else {
                              "On Leave"
                            }
                            Column(
                              modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("dropdown_leave_staff_${eval.staff.id}")
                            ) {
                              Text(
                                text = eval.staff.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                              )
                              Text(
                                text = leaveBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = SignalCrimson
                              )
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ConflictResolutionDialog(
  payload: ConflictDialogPayload,
  onDismiss: () -> Unit,
  onPickReplacement: (CandidateEvaluation) -> Unit,
  onConfirmOverride: (String) -> Unit
) {
  var overrideReason by remember { mutableStateOf("") }
  val isLeave = payload.evaluation.isOnLeave

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
          imageVector = Icons.Default.Warning,
          contentDescription = null,
          tint = if (isLeave) SignalCrimson else SafetyAmber
        )
        Text(
          text = "Scheduling Conflict Detected",
          style = MaterialTheme.typography.titleMedium
        )
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "${payload.evaluation.staff.name} cannot be routinely assigned to ${payload.slot.label}:",
          style = MaterialTheme.typography.bodyMedium
        )
        payload.evaluation.conflicts.forEach { conflict ->
          Surface(
            color = if (conflict.isHardExclusion) SignalCrimsonContainer else SafetyAmberContainer,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = "• ${conflict.label}",
              style = MaterialTheme.typography.bodySmall,
              color = if (conflict.isHardExclusion) SignalCrimsonOnContainer else SafetyAmberOnContainer,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
          }
        }

        if (payload.replacements.isNotEmpty()) {
          HorizontalDivider()
          Text(
            text = "Suggested Safe Replacements:",
            style = MaterialTheme.typography.titleSmall
          )
          payload.replacements.forEach { rep ->
            OutlinedButton(
              onClick = { onPickReplacement(rep) },
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
              shape = RoundedCornerShape(10.dp)
            ) {
              Icon(
                imageVector = Icons.Default.PersonAdd,
                contentDescription = null,
                tint = SignalEmerald,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "${rep.staff.name} (${rep.staff.designationEnum.label} · ${rep.weeklyShiftCount} shifts)",
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
        }

        if (!isLeave) {
          HorizontalDivider()
          Text(
            text = "Emergency Manual Override:",
            style = MaterialTheme.typography.titleSmall,
            color = SafetyAmberOnContainer
          )
          OutlinedTextField(
            value = overrideReason,
            onValueChange = { overrideReason = it },
            label = { Text("Override reason (required)") },
            placeholder = { Text("e.g., Sudden sickness / Train set breakdown") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("override_reason_input")
          )
        } else {
          Text(
            text = "Staff on approved leave cannot be selected for shift duty. Remove the leave entry in the Leave & Rest tab if they returned early.",
            style = MaterialTheme.typography.bodySmall,
            color = SignalCrimsonOnContainer
          )
        }
      }
    },
    confirmButton = {
      if (!isLeave) {
        Button(
          onClick = { onConfirmOverride(overrideReason) },
          enabled = overrideReason.trim().isNotEmpty(),
          colors = ButtonDefaults.buttonColors(containerColor = SafetyAmber),
          modifier = Modifier.testTag("confirm_override_button")
        ) {
          Text("Override & Assign")
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}

@Composable
private fun StoremateDraftStatusCard(
  selectedDate: LocalDate,
  isLocked: Boolean,
  lockedByName: String?,
  hasUnsentLocalStaged: Boolean,
  pendingDraft: DutyApprovalRequest?,
  rejectedDraft: DutyApprovalRequest?,
  draftedSlotCount: Int,
  isBusy: Boolean,
  onSubmitForApproval: () -> Unit,
  onDiscardLocal: () -> Unit
) {
  val dateStr = selectedDate.toString()
  val (title, subtitle, tint) = when {
    isLocked -> Triple(
      "Approved & Locked for $dateStr",
      "Approved by ${lockedByName?.ifBlank { "Supervisor" } ?: "Supervisor"}. Shift duties are saved in DB and cannot be altered.",
      SignalEmeraldContainer.copy(alpha = 0.40f)
    )
    hasUnsentLocalStaged -> Triple(
      "Storemate Draft ($draftedSlotCount slots staged)",
      "Changes are local only until approved by a Supervisor.",
      SafetyAmberContainer.copy(alpha = 0.45f)
    )
    pendingDraft != null -> Triple(
      "Sent for Supervisor Approval (${pendingDraft.proposedSlots.size} slots)",
      "Awaiting Supervisor review before updating the shared roster.",
      DepotTealContainer.copy(alpha = 0.45f)
    )
    rejectedDraft != null -> Triple(
      "Draft Returned by ${rejectedDraft.reviewedByName.ifBlank { "Supervisor" }}",
      rejectedDraft.reviewNotes.ifBlank { "Please adjust the shifts and resend for approval." },
      SignalCrimsonContainer.copy(alpha = 0.35f)
    )
    else -> Triple(
      "Storemate Duty Draft ($dateStr)",
      "Assign shift duties below and send for Supervisor approval.",
      Color.Transparent
    )
  }

  GlassTile(
    shape = RoundedCornerShape(22.dp),
    topColor = GlassSurfaceBright,
    bottomColor = GlassSurfaceMid,
    tintColor = tint
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        GlassIconOrb(
          shape = RoundedCornerShape(12.dp),
          tintColor = if (isLocked) SignalEmeraldContainer.copy(alpha = 0.7f) else SafetyAmberContainer.copy(alpha = 0.7f),
          modifier = Modifier.size(40.dp)
        ) {
          Icon(
            imageVector = if (isLocked) Icons.Default.Lock else Icons.AutoMirrored.Filled.Send,
            contentDescription = null,
            tint = if (isLocked) SignalEmerald else SafetyAmber
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
          Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }

      if (!isLocked) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Button(
            onClick = onSubmitForApproval,
            enabled = !isBusy && draftedSlotCount > 0,
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = if (!isBusy && draftedSlotCount > 0) Color.Transparent else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
              contentColor = Color.White
            ),
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 48.dp)
              .let { mod ->
                if (!isBusy && draftedSlotCount > 0) {
                  mod.liquidGlassAccent(shape = RoundedCornerShape(14.dp), baseColor = SignalEmerald)
                } else {
                  mod
                }
              }
              .testTag("submit_duty_draft_button"),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            shape = RoundedCornerShape(14.dp)
          ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (pendingDraft != null && !hasUnsentLocalStaged) "Resend Approval ($draftedSlotCount)" else "Send for Supervisor Approval",
              maxLines = 2,
              textAlign = TextAlign.Center
            )
          }

          if (hasUnsentLocalStaged) {
            OutlinedButton(
              onClick = onDiscardLocal,
              enabled = !isBusy,
              modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("discard_duty_draft_button"),
              shape = RoundedCornerShape(14.dp)
            ) {
              Text("Discard")
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SupervisorPendingApprovalsPanel(
  pendingDrafts: List<DutyApprovalRequest>,
  isBusy: Boolean,
  onViewDate: (String) -> Unit,
  onApproveDraft: (DutyApprovalRequest) -> Unit,
  onRejectDraft: (DutyApprovalRequest) -> Unit
) {
  GlassTile(
    shape = RoundedCornerShape(24.dp),
    topColor = GlassSurfaceBright,
    bottomColor = GlassSurfaceMid,
    tintColor = SafetyAmberContainer.copy(alpha = 0.35f)
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = "Pending Shift Draft Approvals (${pendingDrafts.size})",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          modifier = Modifier
            .weight(1f)
            .padding(end = 8.dp),
          maxLines = 2
        )
        GlassPillBadge(
          text = "ACTION REQUIRED",
          tintColor = SafetyAmberContainer,
          textColor = SafetyAmberOnContainer,
          borderColor = SafetyAmber
        )
      }

      pendingDrafts.forEach { draft ->
        val shiftSummary = remember(draft.proposedSlots) {
          draft.proposedSlots.groupBy { ShiftType.fromRaw(it.shiftType).code }
            .entries.joinToString(" · ") { (code, list) -> "$code (${list.size})" }
        }
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .liquidGlassSurface(
              shape = RoundedCornerShape(16.dp),
              topColor = GlassSurfaceBright,
              bottomColor = GlassSurfaceSoft,
              borderWidth = 1.dp
            )
            .padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Duty Date: ${draft.dutyDate}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "From ${draft.submittedByName} · ${draft.proposedSlots.size} slots ($shiftSummary)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            TextButton(
              onClick = { onViewDate(draft.dutyDate) },
              modifier = Modifier.heightIn(min = 48.dp)
            ) {
              Text("Open Date")
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Button(
              onClick = { onApproveDraft(draft) },
              enabled = !isBusy,
              elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White
              ),
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .liquidGlassAccent(shape = RoundedCornerShape(12.dp), baseColor = SignalEmerald)
                .testTag("approve_draft_${draft.dutyDate}")
            ) {
              Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Approve & Lock", maxLines = 2, textAlign = TextAlign.Center)
            }

            OutlinedButton(
              onClick = { onRejectDraft(draft) },
              enabled = !isBusy,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("reject_draft_${draft.dutyDate}")
            ) {
              Icon(Icons.Default.Close, contentDescription = null, tint = SignalCrimson, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Return", color = SignalCrimson)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun CompliancePill(label: String, isCompliant: Boolean) {
  GlassPillBadge(
    text = label,
    tintColor = if (isCompliant) SignalEmeraldContainer.copy(alpha = 0.65f) else SafetyAmberContainer.copy(alpha = 0.65f),
    textColor = if (isCompliant) SignalEmeraldOnContainer else SafetyAmberOnContainer,
    borderColor = if (isCompliant) SignalEmerald else SafetyAmber
  )
}

@Composable
private fun CoverageMiniBadge(label: String, isOk: Boolean) {
  GlassPillBadge(
    text = label,
    tintColor = if (isOk) SignalEmeraldContainer.copy(alpha = 0.55f) else GlassSurfaceSoft,
    textColor = if (isOk) SignalEmeraldOnContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    borderColor = if (isOk) SignalEmerald else GlassRimHighlight
  )
}
