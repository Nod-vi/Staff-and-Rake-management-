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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
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
import com.example.domain.LeaveBalanceEngine
import com.example.domain.SupervisorAnalyticsEngine
import com.example.model.Designation
import com.example.model.LapUpdateStatus
import com.example.model.LeaveRecord
import com.example.model.LeaveStatus
import com.example.model.LeaveType
import com.example.model.ShiftSlotAssignment
import com.example.model.StaffLeaveAccount
import com.example.model.StaffMember
import com.example.model.UserRole
import com.example.model.WeeklyRestDay
import com.example.ui.theme.DepotTeal
import com.example.ui.theme.DepotTealContainer
import com.example.ui.theme.DepotTealOnContainer
import com.example.ui.theme.GlassIconOrb
import com.example.ui.theme.GlassPillBadge
import com.example.ui.theme.GlassRimHighlight
import com.example.ui.theme.GlassSurfaceBright
import com.example.ui.theme.GlassSurfaceMid
import com.example.ui.theme.GlassSurfaceSoft
import com.example.ui.theme.GlassTile
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
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

private enum class LeaveTabSection(val label: String) {
  LEAVE("Leaves"),
  CALENDAR("Calendar"),
  REST("Weekly Rest")
}

private enum class ActiveDatePickerTarget {
  START_DATE,
  END_DATE,
  DATE_RANGE
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LeaveAndRestScreen(
  selectedDate: LocalDate,
  staffList: List<StaffMember>,
  assignments: List<ShiftSlotAssignment>,
  leaves: List<LeaveRecord>,
  leaveAccounts: List<StaffLeaveAccount> = emptyList(),
  userRole: UserRole = UserRole.SUPERVISOR,
  roleAccess: com.example.model.ResolvedRoleAccess = com.example.model.RoleAccessPolicy.resolve(userRole, emptyMap()),
  currentActorUid: String = "",
  linkedStaffId: String = "",
  isBusy: Boolean,
  onAddLeave: (StaffMember, LeaveType, LocalDate, LocalDate, String, Boolean, LeaveStatus, StaffMember?) -> Unit,
  onUpdateLeave: (LeaveRecord, LeaveType, LocalDate, LocalDate, LeaveStatus, String, Boolean) -> Unit,
  onApprovePendingLeave: (LeaveRecord) -> Unit = {},
  onRejectPendingLeave: (LeaveRecord) -> Unit = {},
  onDeleteLeave: (String) -> Unit,
  onUpdateRestDay: (String, WeeklyRestDay) -> Unit,
  onDistributeBalancedRestDays: () -> Unit,
  onUpdateLapBalance: (StaffMember, Int, Int, String, Int) -> Unit = { _, _, _, _, _ -> },
  onApproveLapCorrection: (StaffLeaveAccount) -> Unit = {},
  onRejectLapCorrection: (StaffLeaveAccount) -> Unit = {},
  openCalendarSignal: Int = 0,
  prefillLeaveStaffId: String? = null,
  prefillLeaveType: LeaveType? = null,
  prefillLeaveSignal: Int = 0
) {
  val isReadOnlyLeaveView = roleAccess.isReadOnlyLeave
  val canApproveLeaves = !isReadOnlyLeaveView && roleAccess.canApproveLeaves
  val canManageRestDays = !isReadOnlyLeaveView && roleAccess.canManageRestDays
  val activeDutyStaff = remember(staffList) { staffList.filter { !it.isSupervisory } }
  val sseSupervisors = remember(staffList) { staffList.filter { it.designationEnum == Designation.SSE } }
  val linkedStaff = remember(staffList, linkedStaffId) {
    linkedStaffId.trim().takeIf { it.isNotBlank() }?.let { id ->
      staffList.firstOrNull { it.id == id }
    }
  }

  var activeSection by rememberSaveable { mutableStateOf(LeaveTabSection.LEAVE) }
  androidx.compose.runtime.LaunchedEffect(openCalendarSignal, isReadOnlyLeaveView) {
    if (isReadOnlyLeaveView) {
      activeSection = LeaveTabSection.LEAVE
    } else if (openCalendarSignal > 0) {
      activeSection = LeaveTabSection.CALENDAR
    }
  }
  val today = remember { LocalDate.now() }
  var calendarMonthStr by rememberSaveable { mutableStateOf(YearMonth.from(selectedDate).toString()) }
  var calendarFocusedDateStr by rememberSaveable { mutableStateOf(selectedDate.toString()) }
  val calendarYearMonth = remember(calendarMonthStr) {
    runCatching { YearMonth.parse(calendarMonthStr) }.getOrDefault(YearMonth.from(today))
  }
  val calendarFocusedDate = remember(calendarFocusedDateStr) {
    runCatching { LocalDate.parse(calendarFocusedDateStr) }.getOrDefault(today)
  }
  val monthlyCalendarData = remember(calendarYearMonth, staffList, leaves) {
    SupervisorAnalyticsEngine.buildMonthlyLeaveCalendar(
      yearMonth = calendarYearMonth,
      staffList = staffList,
      leaves = leaves
    )
  }
  var selectedStaffId by rememberSaveable(activeDutyStaff, linkedStaffId) {
    mutableStateOf(linkedStaff?.id ?: activeDutyStaff.firstOrNull()?.id)
  }
  val selectedStaff = remember(canApproveLeaves, linkedStaff, activeDutyStaff, selectedStaffId) {
    if (!canApproveLeaves) {
      linkedStaff
    } else {
      activeDutyStaff.firstOrNull { it.id == selectedStaffId }
        ?: linkedStaff
        ?: activeDutyStaff.firstOrNull()
    }
  }

  var selectedSseId by rememberSaveable(sseSupervisors) {
    mutableStateOf(sseSupervisors.firstOrNull()?.id)
  }
  val selectedSseSupervisor = remember(sseSupervisors, selectedSseId) {
    sseSupervisors.firstOrNull { it.id == selectedSseId } ?: sseSupervisors.firstOrNull()
  }

  var showStaffPicker by remember { mutableStateOf(false) }
  var showSelfStaffDropdown by remember { mutableStateOf(false) }
  var showSseDropdown by remember { mutableStateOf(false) }
  var selectedLeaveType by remember { mutableStateOf(LeaveType.CL) }
  androidx.compose.runtime.LaunchedEffect(prefillLeaveSignal) {
    if (prefillLeaveSignal > 0) {
      activeSection = LeaveTabSection.LEAVE
      if (canApproveLeaves && !prefillLeaveStaffId.isNullOrBlank()) {
        selectedStaffId = prefillLeaveStaffId
      }
      if (prefillLeaveType != null) {
        selectedLeaveType = prefillLeaveType
      }
    }
  }
  var selectedLeaveStatus by remember {
    mutableStateOf(if (canApproveLeaves) LeaveStatus.APPROVED else LeaveStatus.PENDING)
  }
  val effectiveSubmitStatus = if (canApproveLeaves) selectedLeaveStatus else LeaveStatus.PENDING

  var startDate by remember { mutableStateOf(selectedDate) }
  var endDate by remember { mutableStateOf(selectedDate.plusDays(1)) }
  var notes by remember { mutableStateOf("") }
  var autoClearConflictingShifts by remember { mutableStateOf(true) }
  var activePicker by remember { mutableStateOf<ActiveDatePickerTarget?>(null) }
  var editingLeave by remember { mutableStateOf<LeaveRecord?>(null) }
  var showLapUpdateDialog by remember { mutableStateOf(false) }
  var leaveRecordsSearchQuery by rememberSaveable { mutableStateOf("") }

  val filteredLeaves = remember(leaves, leaveRecordsSearchQuery) {
    val q = leaveRecordsSearchQuery.trim()
    if (q.isEmpty()) {
      leaves
    } else {
      leaves.filter { leave ->
        leave.staffName.contains(q, ignoreCase = true) ||
          leave.leaveTypeEnum.code.contains(q, ignoreCase = true) ||
          leave.leaveTypeEnum.label.contains(q, ignoreCase = true) ||
          leave.statusEnum.label.contains(q, ignoreCase = true) ||
          leave.startDate.contains(q, ignoreCase = true) ||
          leave.endDate.contains(q, ignoreCase = true) ||
          leave.notes.contains(q, ignoreCase = true) ||
          leave.forwardedToName.contains(q, ignoreCase = true) ||
          leave.appliedByName.contains(q, ignoreCase = true) ||
          leave.reviewedByName.contains(q, ignoreCase = true) ||
          (leave.leaveTypeEnum == LeaveType.SPECIAL && q.equals("CR", ignoreCase = true))
      }
    }
  }

  val balanceYear = selectedDate.year
  val pendingLapRequests = remember(leaveAccounts) {
    leaveAccounts
      .filter { it.pendingLapStatusEnum == LapUpdateStatus.PENDING }
      .sortedBy { it.staffName }
  }
  val selectedStaffLeaveAccount = remember(selectedStaff, leaveAccounts) {
    selectedStaff?.let { staff ->
      leaveAccounts.firstOrNull { it.staffId == staff.id || it.id == staff.id }
    }
  }
  val selectedStaffBalanceSummary = remember(
    selectedStaff,
    balanceYear,
    selectedStaffLeaveAccount,
    leaves,
    assignments
  ) {
    selectedStaff?.let { staff ->
      LeaveBalanceEngine.computeBalanceSummary(
        staff = staff,
        year = balanceYear,
        account = selectedStaffLeaveAccount,
        allLeaves = leaves,
        allAssignments = assignments
      )
    }
  }

  val startStr = startDate.toString()
  val endStr = endDate.toString()
  val dayCount = remember(startDate, endDate) {
    ChronoUnit.DAYS.between(startDate, endDate).coerceAtLeast(0L) + 1L
  }

  val overlappingLeave = remember(selectedStaff, leaves, startStr, endStr, effectiveSubmitStatus) {
    if (effectiveSubmitStatus == LeaveStatus.REJECTED || effectiveSubmitStatus == LeaveStatus.CANCELLED) {
      null
    } else {
      selectedStaff?.let { s ->
        leaves.firstOrNull {
          it.staffId == s.id &&
            it.statusEnum != LeaveStatus.REJECTED &&
            it.statusEnum != LeaveStatus.CANCELLED &&
            it.startDate <= endStr &&
            it.endDate >= startStr
        }
      }
    }
  }

  val impactedAssignmentCount = remember(selectedStaff, assignments, startStr, endStr, effectiveSubmitStatus) {
    if (!effectiveSubmitStatus.blocksShiftDuty) 0
    else selectedStaff?.let { s ->
      assignments.count { it.staffId == s.id && it.dutyDateStr in startStr..endStr }
    } ?: 0
  }

  val selectedDateStr = selectedDate.toString()
  val onLeaveOnSelectedDate = remember(leaves, selectedDateStr) {
    leaves.filter { it.isBlockingDate(selectedDateStr) }.distinctBy { it.staffId }.size
  }

  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 780.dp),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      if (!isReadOnlyLeaveView) {
        item(key = "leave_rest_switcher", contentType = "switcher") {
          GlassTile(
            shape = RoundedCornerShape(50),
            topColor = GlassSurfaceBright,
            bottomColor = GlassSurfaceSoft,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("leave_rest_section_switcher")
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              LeaveTabSection.entries.forEach { section ->
                val selected = activeSection == section
                Box(
                  modifier = (if (selected) {
                    Modifier.liquidGlassAccent(shape = RoundedCornerShape(50))
                  } else {
                    Modifier
                  })
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clickable { activeSection = section }
                    .padding(horizontal = 6.dp, vertical = 8.dp)
                    .testTag("section_${section.name.lowercase()}"),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = section.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                  )
                }
              }
            }
          }
        }
      }

      if (isReadOnlyLeaveView || activeSection == LeaveTabSection.LEAVE) {
        if (!isReadOnlyLeaveView && canApproveLeaves && pendingLapRequests.isNotEmpty()) {
          item(key = "pending_lap_approvals", contentType = "pending_lap_approvals") {
            PendingLapApprovalsCard(
              pendingAccounts = pendingLapRequests,
              isBusy = isBusy,
              onApprove = onApproveLapCorrection,
              onReject = onRejectLapCorrection
            )
          }
        }

        if (!isReadOnlyLeaveView && selectedStaffBalanceSummary != null) {
          item(key = "leave_balance_overview", contentType = "leave_balance") {
            val canUpdateLap = canApproveLeaves || (linkedStaff != null && selectedStaff?.id == linkedStaff.id)
            LeaveBalanceOverviewCard(
              summary = selectedStaffBalanceSummary,
              canUpdateLap = canUpdateLap,
              isApprover = canApproveLeaves,
              isBusy = isBusy,
              onClickUpdateLap = { showLapUpdateDialog = true }
            )
          }

          if (selectedStaffBalanceSummary.specialCreditEventsInYear.isNotEmpty() ||
            selectedStaffBalanceSummary.specialEarnedTotal > 0
          ) {
            item(key = "leave_tab_special_credits", contentType = "special_credits") {
              SpecialLeaveCreditsCard(summary = selectedStaffBalanceSummary)
            }
          }
        }

        if (!isReadOnlyLeaveView) {
        item(key = "leave_form_card", contentType = "leave_form") {
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
                  modifier = Modifier.weight(1f)
                ) {
                  GlassIconOrb(
                    shape = RoundedCornerShape(14.dp),
                    tintColor = SignalCrimsonContainer.copy(alpha = 0.55f),
                    modifier = Modifier.size(42.dp)
                  ) {
                    Icon(Icons.Default.EventBusy, contentDescription = null, tint = SignalCrimson)
                  }
                  Column {
                    Text(
                      text = if (canApproveLeaves) "Mark or Approve Staff Leave" else "Apply Leave (Forward to SSE)",
                      style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                      text = if (canApproveLeaves) {
                        "Approve leaves, adjust leave dates, or record direct absences."
                      } else {
                        "Submit a leave request — forwarded to an SSE Supervisor for approval."
                      },
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
                if (onLeaveOnSelectedDate > 0) {
                  GlassPillBadge(
                    text = "$onLeaveOnSelectedDate OFF TODAY",
                    tintColor = SignalCrimsonContainer.copy(alpha = 0.65f),
                    textColor = SignalCrimsonOnContainer,
                    borderColor = SignalCrimson
                  )
                }
              }

              Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .liquidGlassSurface(
                      shape = RoundedCornerShape(16.dp),
                      topColor = GlassSurfaceBright,
                      bottomColor = GlassSurfaceSoft,
                      tintColor = if (!canApproveLeaves && linkedStaff == null) {
                        SignalCrimsonContainer.copy(alpha = 0.28f)
                      } else {
                        Color.Transparent
                      },
                      borderWidth = 1.dp
                    )
                    .clickable(enabled = canApproveLeaves || linkedStaff != null) {
                      if (canApproveLeaves) {
                        showStaffPicker = true
                      } else if (linkedStaff != null) {
                        showSelfStaffDropdown = true
                      }
                    }
                    .testTag("leave_staff_dropdown")
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = if (canApproveLeaves) "Staff Member" else "Logged-in Staff (Linked Account)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                      Text(
                        text = selectedStaff?.let {
                          "#${it.serialNo} ${it.name} (${it.designationEnum.label})"
                        } ?: if (canApproveLeaves) {
                          "Select staff member"
                        } else {
                          "Not linked to any staff profile"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (!canApproveLeaves && linkedStaff == null) {
                          SignalCrimson
                        } else {
                          MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 2
                      )
                      selectedStaff?.let { member ->
                        Text(
                          text = buildStaffCapabilitySummary(member),
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                      }
                    }
                    if (canApproveLeaves) {
                      Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Select staff for leave")
                    } else if (linkedStaff != null) {
                      Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Logged-in linked staff"
                      )
                    } else {
                      Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Account not linked",
                        tint = SignalCrimson
                      )
                    }
                  }
                }

                if (!canApproveLeaves && linkedStaff != null) {
                  DropdownMenu(
                    expanded = showSelfStaffDropdown,
                    onDismissRequest = { showSelfStaffDropdown = false },
                    modifier = Modifier.testTag("leave_self_staff_menu")
                  ) {
                    DropdownMenuItem(
                      text = {
                        Text(
                          text = "#${linkedStaff.serialNo} ${linkedStaff.name} (${linkedStaff.designationEnum.label})",
                          fontWeight = FontWeight.SemiBold
                        )
                      },
                      trailingIcon = {
                        Icon(Icons.Default.Check, contentDescription = "Selected", tint = SignalEmerald)
                      },
                      onClick = { showSelfStaffDropdown = false }
                    )
                  }
                }
              }

              if (!canApproveLeaves && linkedStaff == null) {
                Surface(
                  color = SignalCrimsonContainer,
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("unlinked_staff_leave_warning")
                ) {
                  Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = SignalCrimson)
                    Text(
                      text = "Only linked staff can apply for leave. Ask Admin to link your account to your staff profile in the Users tab.",
                      style = MaterialTheme.typography.bodySmall,
                      color = SignalCrimsonOnContainer
                    )
                  }
                }
              }

              FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                LeaveType.entries.forEach { type ->
                  val selected = selectedLeaveType == type
                  val isTraining = type == LeaveType.TRAINING
                  Box(
                    modifier = (if (selected) {
                      if (isTraining) {
                        Modifier.liquidGlassAccent(
                          shape = RoundedCornerShape(50),
                          baseColor = DepotTeal,
                          highlightColor = DepotTeal.copy(alpha = 0.85f)
                        )
                      } else {
                        Modifier.liquidGlassAccent(shape = RoundedCornerShape(50))
                      }
                    } else {
                      Modifier.liquidGlassSurface(
                        shape = RoundedCornerShape(50),
                        topColor = GlassSurfaceBright,
                        bottomColor = GlassSurfaceSoft,
                        borderWidth = 1.dp
                      )
                    })
                      .heightIn(min = 48.dp)
                      .clickable { selectedLeaveType = type }
                      .padding(horizontal = 14.dp, vertical = 8.dp)
                      .testTag("leave_type_${type.code}"),
                    contentAlignment = Alignment.Center
                  ) {
                    Text(
                      text = if (isTraining) "Training" else type.code,
                      style = MaterialTheme.typography.labelLarge,
                      color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                  }
                }
              }

              if (selectedLeaveType == LeaveType.TRAINING) {
                Surface(
                  color = DepotTealContainer.copy(alpha = 0.72f),
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("training_leave_info_banner")
                ) {
                  Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    Icon(Icons.Default.School, contentDescription = null, tint = DepotTeal)
                    Text(
                      text = "Technical Training relieves staff from shift duty without deducting CL, LAP, LHAP, or Special Leave balances.",
                      style = MaterialTheme.typography.bodySmall,
                      color = DepotTealOnContainer
                    )
                  }
                }
              }

              if (canApproveLeaves) {
                Column(
                  modifier = Modifier.fillMaxWidth(),
                  verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  LeaveStatus.entries.chunked(2).forEach { pair ->
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                      pair.forEach { st ->
                        val selected = selectedLeaveStatus == st
                        val accentColor = when (st) {
                          LeaveStatus.APPROVED -> SignalEmerald
                          LeaveStatus.PENDING -> SafetyAmber
                          LeaveStatus.REJECTED, LeaveStatus.CANCELLED -> SignalCrimson
                        }
                        Box(
                          modifier = (if (selected) {
                            Modifier.liquidGlassAccent(
                              shape = RoundedCornerShape(50),
                              baseColor = accentColor,
                              highlightColor = accentColor.copy(alpha = 0.85f)
                            )
                          } else {
                            Modifier.liquidGlassSurface(
                              shape = RoundedCornerShape(50),
                              topColor = GlassSurfaceBright,
                              bottomColor = GlassSurfaceSoft,
                              borderWidth = 1.dp
                            )
                          })
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clickable { selectedLeaveStatus = st }
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .testTag("leave_status_${st.name.lowercase()}"),
                          contentAlignment = Alignment.Center
                        ) {
                          Text(
                            text = st.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                          )
                        }
                      }
                    }
                  }
                }
              } else {
                // Non-supervisors always submit PENDING leave forwarded to SSE Only
                Box(modifier = Modifier.fillMaxWidth()) {
                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .heightIn(min = 56.dp)
                      .liquidGlassSurface(
                        shape = RoundedCornerShape(16.dp),
                        topColor = GlassSurfaceBright,
                        bottomColor = GlassSurfaceSoft,
                        tintColor = SafetyAmberContainer.copy(alpha = 0.28f),
                        borderWidth = 1.dp
                      )
                      .clickable(enabled = sseSupervisors.isNotEmpty()) { showSseDropdown = true }
                      .testTag("forward_sse_dropdown")
                  ) {
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                      ) {
                        Icon(
                          imageVector = Icons.Default.SupervisorAccount,
                          contentDescription = null,
                          tint = SafetyAmber
                        )
                        Column {
                          Text(
                            text = "Forward To Supervisor (SSE Only)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                          )
                          Text(
                            text = selectedSseSupervisor?.let {
                              "#${it.serialNo} ${it.name} (SSE)"
                            } ?: "No SSE Supervisor available in staff roster",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2
                          )
                        }
                      }
                      Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Select SSE Supervisor")
                    }
                  }

                  DropdownMenu(
                    expanded = showSseDropdown,
                    onDismissRequest = { showSseDropdown = false }
                  ) {
                    sseSupervisors.forEach { sse ->
                      DropdownMenuItem(
                        text = {
                          Text("#${sse.serialNo} ${sse.name} · SSE")
                        },
                        onClick = {
                          selectedSseId = sse.id
                          showSseDropdown = false
                        }
                      )
                    }
                  }
                }
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = "Leave Period ($dayCount ${if (dayCount == 1L) "day" else "days"})",
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.onSurface
                )
                OutlinedButton(
                  onClick = { activePicker = ActiveDatePickerTarget.DATE_RANGE },
                  colors = ButtonDefaults.outlinedButtonColors(containerColor = GlassSurfaceSoft),
                  border = BorderStroke(1.dp, GlassRimHighlight),
                  modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("pick_leave_range_button"),
                  shape = RoundedCornerShape(14.dp)
                ) {
                  Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Pick Range")
                }
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                DateStepperField(
                  label = "Start Date",
                  date = startDate,
                  onPickDate = { activePicker = ActiveDatePickerTarget.START_DATE },
                  onDec = {
                    startDate = startDate.minusDays(1)
                    if (endDate.isBefore(startDate)) endDate = startDate
                  },
                  onInc = {
                    startDate = startDate.plusDays(1)
                    if (endDate.isBefore(startDate)) endDate = startDate
                  },
                  modifier = Modifier.weight(1f),
                  tagPrefix = "leave_start"
                )

                DateStepperField(
                  label = "End Date",
                  date = endDate,
                  onPickDate = { activePicker = ActiveDatePickerTarget.END_DATE },
                  onDec = {
                    val next = endDate.minusDays(1)
                    if (!next.isBefore(startDate)) endDate = next
                  },
                  onInc = { endDate = endDate.plusDays(1) },
                  modifier = Modifier.weight(1f),
                  tagPrefix = "leave_end"
                )
              }

              if (overlappingLeave != null) {
                Surface(
                  color = SignalCrimsonContainer,
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = SignalCrimson)
                    Text(
                      text = "Overlaps existing ${overlappingLeave.leaveTypeEnum.code} leave (${overlappingLeave.startDate} to ${overlappingLeave.endDate}).",
                      style = MaterialTheme.typography.bodySmall,
                      color = SignalCrimsonOnContainer
                    )
                  }
                }
              } else if (impactedAssignmentCount > 0) {
                Surface(
                  color = SafetyAmberContainer,
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = "$impactedAssignmentCount assigned shift(s) in this period",
                        style = MaterialTheme.typography.labelLarge,
                        color = SafetyAmberOnContainer
                      )
                      Text(
                        text = "Auto-vacate conflicting roster slots when saved",
                        style = MaterialTheme.typography.bodySmall,
                        color = SafetyAmberOnContainer
                      )
                    }
                    Switch(
                      checked = autoClearConflictingShifts,
                      onCheckedChange = { autoClearConflictingShifts = it },
                      modifier = Modifier.testTag("auto_vacate_shifts_switch")
                    )
                  }
                }
              }

              OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Remarks (optional)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                  unfocusedContainerColor = GlassSurfaceSoft,
                  focusedContainerColor = GlassSurfaceBright
                ),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("leave_notes_input")
              )

              val canSubmit = selectedStaff != null &&
                overlappingLeave == null &&
                !isBusy &&
                (canApproveLeaves || selectedSseSupervisor != null)

              Button(
                onClick = {
                  val staff = selectedStaff ?: return@Button
                  onAddLeave(
                    staff,
                    selectedLeaveType,
                    startDate,
                    endDate,
                    notes,
                    if (canApproveLeaves) autoClearConflictingShifts else false,
                    effectiveSubmitStatus,
                    if (canApproveLeaves) null else selectedSseSupervisor
                  )
                  notes = ""
                },
                enabled = canSubmit,
                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
                colors = ButtonDefaults.buttonColors(
                  containerColor = if (canSubmit) Color.Transparent else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                  contentColor = Color.White
                ),
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(min = 48.dp)
                  .let { mod ->
                    if (canSubmit) {
                      mod.liquidGlassAccent(shape = RoundedCornerShape(16.dp))
                    } else {
                      mod
                    }
                  }
                  .testTag("submit_leave_button"),
                shape = RoundedCornerShape(16.dp)
              ) {
                if (!canApproveLeaves) {
                  Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "Forward Leave to SSE ${selectedSseSupervisor?.name ?: ""}".trim(),
                    maxLines = 2,
                    textAlign = TextAlign.Center
                  )
                } else {
                  Text("Save Leave (${effectiveSubmitStatus.label})")
                }
              }
            }
          }
        }
        }

        item(key = "leave_list_title", contentType = "section_title") {
          val recordCountLabel = if (leaveRecordsSearchQuery.isBlank()) {
            "${leaves.size}"
          } else {
            "${filteredLeaves.size}/${leaves.size}"
          }
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
              ) {
                Text(
                  text = "Leave Records ($recordCountLabel)",
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onBackground
                )
                if (isReadOnlyLeaveView) {
                  GlassPillBadge(
                    text = "READ ONLY",
                    tintColor = DepotTealContainer.copy(alpha = 0.72f),
                    textColor = DepotTealOnContainer,
                    borderColor = DepotTeal,
                    modifier = Modifier.testTag("shift_incharge_leave_readonly_badge")
                  )
                }
              }
              if (isReadOnlyLeaveView && onLeaveOnSelectedDate > 0) {
                GlassPillBadge(
                  text = "$onLeaveOnSelectedDate OFF TODAY",
                  tintColor = SignalCrimsonContainer.copy(alpha = 0.65f),
                  textColor = SignalCrimsonOnContainer,
                  borderColor = SignalCrimson
                )
              }
            }
            Text(
              text = when {
                isReadOnlyLeaveView -> "${userRole.label} access: read-only view of depot leave records."
                canApproveLeaves -> "${userRole.label} access: approve/reject pending requests or edit status & dates."
                else -> "${userRole.label} access: view depot leaves or withdraw your pending requests."
              },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
              value = leaveRecordsSearchQuery,
              onValueChange = { leaveRecordsSearchQuery = it },
              placeholder = {
                Text(
                  text = "Search staff, leave type, status, or date...",
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              },
              leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
              trailingIcon = {
                if (leaveRecordsSearchQuery.isNotEmpty()) {
                  IconButton(
                    onClick = { leaveRecordsSearchQuery = "" },
                    modifier = Modifier
                      .size(48.dp)
                      .testTag("leave_records_search_clear")
                  ) {
                    Icon(Icons.Default.Close, contentDescription = "Clear leave search")
                  }
                }
              },
              singleLine = true,
              shape = RoundedCornerShape(16.dp),
              colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = GlassSurfaceSoft,
                focusedContainerColor = GlassSurfaceBright
              ),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("leave_records_search_input")
            )
          }
        }

        if (leaves.isEmpty()) {
          item(key = "leave_list_empty", contentType = "empty_leaves") {
            GlassTile(
              shape = RoundedCornerShape(22.dp),
              topColor = GlassSurfaceBright,
              bottomColor = GlassSurfaceMid,
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = "No leave records logged. All staff are available unless on scheduled weekly rest.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
              )
            }
          }
        } else if (filteredLeaves.isEmpty()) {
          item(key = "leave_list_search_empty", contentType = "empty_leaves") {
            GlassTile(
              shape = RoundedCornerShape(22.dp),
              topColor = GlassSurfaceBright,
              bottomColor = GlassSurfaceMid,
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = "No leave records match \"${leaveRecordsSearchQuery.trim()}\".",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
              )
            }
          }
        } else {
          items(
            items = filteredLeaves,
            key = { it.id },
            contentType = { "leave_card" }
          ) { leave ->
            val canWithdrawOwnPending = !isReadOnlyLeaveView &&
              !canApproveLeaves &&
              leave.statusEnum == LeaveStatus.PENDING &&
              leave.appliedByUid == currentActorUid
            LeaveRecordCard(
              leave = leave,
              referenceDate = selectedDate,
              canApproveOrEdit = !isReadOnlyLeaveView && canApproveLeaves,
              canDelete = !isReadOnlyLeaveView && (canApproveLeaves || canWithdrawOwnPending),
              isBusy = isBusy,
              onApprove = { onApprovePendingLeave(leave) },
              onReject = { onRejectPendingLeave(leave) },
              onEdit = { editingLeave = leave },
              onDelete = { onDeleteLeave(leave.id) }
            )
          }
        }
      } else if (activeSection == LeaveTabSection.CALENDAR) {
        leaveCalendarItems(
          calendarData = monthlyCalendarData,
          focusedDate = calendarFocusedDate,
          today = today,
          canApproveLeaves = canApproveLeaves,
          isBusy = isBusy,
          onPrevMonth = {
            val prev = calendarYearMonth.minusMonths(1)
            calendarMonthStr = prev.toString()
            calendarFocusedDateStr = prev.atDay(1).toString()
          },
          onNextMonth = {
            val next = calendarYearMonth.plusMonths(1)
            calendarMonthStr = next.toString()
            calendarFocusedDateStr = next.atDay(1).toString()
          },
          onJumpToday = {
            calendarMonthStr = YearMonth.from(today).toString()
            calendarFocusedDateStr = today.toString()
          },
          onSelectCalendarDate = { tappedDate ->
            calendarFocusedDateStr = tappedDate.toString()
          },
          onApprovePendingLeave = onApprovePendingLeave,
          onRejectPendingLeave = onRejectPendingLeave
        )
      } else {
        item(key = "rest_header", contentType = "rest_header") {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Weekend, contentDescription = null, tint = DepotTeal)
                Text("Weekly Rest Roster (REST)", style = MaterialTheme.typography.titleMedium)
              }
              Text(
                text = if (canManageRestDays) {
                  "Assign each staff member's mandatory weekly rest day."
                } else {
                  "Read-only weekly rest schedule across depot staff."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }

            if (canManageRestDays) {
              OutlinedButton(
                onClick = onDistributeBalancedRestDays,
                enabled = !isBusy,
                modifier = Modifier
                  .heightIn(min = 48.dp)
                  .testTag("distribute_rest_button")
              ) {
                Icon(Icons.Default.Balance, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Auto-Balance")
              }
            }
          }
        }

        items(
          items = activeDutyStaff,
          key = { it.id },
          contentType = { "rest_row" }
        ) { member ->
          RestDayRow(
            member = member,
            canEdit = canManageRestDays,
            onSelectRestDay = { restDay -> onUpdateRestDay(member.id, restDay) }
          )
        }
      }

      item(key = "leave_rest_bottom_spacer", contentType = "spacer") {
        Spacer(modifier = Modifier.height(16.dp))
      }
    }
  }

  if (!isReadOnlyLeaveView && showLapUpdateDialog && selectedStaff != null && selectedStaffBalanceSummary != null) {
    LapUpdateDialog(
      summary = selectedStaffBalanceSummary,
      isApprover = canApproveLeaves,
      isBusy = isBusy,
      onDismiss = { showLapUpdateDialog = false },
      onConfirm = { newLap, newLhap, reason ->
        onUpdateLapBalance(selectedStaff, newLap, newLhap, reason, balanceYear)
        showLapUpdateDialog = false
      }
    )
  }

  if (!isReadOnlyLeaveView && showStaffPicker && canApproveLeaves) {
    StaffLeavePickerDialog(
      staffList = activeDutyStaff,
      selectedStaffId = selectedStaff?.id,
      leaves = leaves,
      referenceDateStr = selectedDateStr,
      onDismiss = { showStaffPicker = false },
      onSelectStaff = { member ->
        selectedStaffId = member.id
        showStaffPicker = false
      }
    )
  }

  if (!isReadOnlyLeaveView) {
    when (activePicker) {
      ActiveDatePickerTarget.START_DATE -> {
        SingleDatePickerModal(
          initialDate = startDate,
          title = "Select Leave Start Date",
          onDismiss = { activePicker = null },
          onConfirm = { picked ->
            startDate = picked
            if (endDate.isBefore(picked)) endDate = picked
            activePicker = null
          }
        )
      }
      ActiveDatePickerTarget.END_DATE -> {
        SingleDatePickerModal(
          initialDate = endDate,
          title = "Select Leave End Date",
          onDismiss = { activePicker = null },
          onConfirm = { picked ->
            if (picked.isBefore(startDate)) {
              startDate = picked
            }
            endDate = picked
            activePicker = null
          }
        )
      }
      ActiveDatePickerTarget.DATE_RANGE -> {
        DateRangePickerModal(
          initialStart = startDate,
          initialEnd = endDate,
          onDismiss = { activePicker = null },
          onConfirm = { start, end ->
            startDate = start
            endDate = if (end.isBefore(start)) start else end
            activePicker = null
          }
        )
      }
      null -> Unit
    }
  }

  val targetEditLeave = editingLeave
  if (!isReadOnlyLeaveView && targetEditLeave != null) {
    LeaveEditDialog(
      leave = targetEditLeave,
      existingLeaves = leaves,
      assignments = assignments,
      isBusy = isBusy,
      onDismiss = { editingLeave = null },
      onSave = { leaveType, newStart, newEnd, newStatus, newNotes, clearConflicting ->
        onUpdateLeave(targetEditLeave, leaveType, newStart, newEnd, newStatus, newNotes, clearConflicting)
        editingLeave = null
      }
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LeaveRecordCard(
  leave: LeaveRecord,
  referenceDate: LocalDate,
  canApproveOrEdit: Boolean,
  canDelete: Boolean,
  isBusy: Boolean,
  onApprove: () -> Unit,
  onReject: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit
) {
  val refStr = referenceDate.toString()
  val isBlockingToday = leave.isBlockingDate(refStr)
  val isUpcomingApproved = leave.isBlockingDuty && leave.startDate > refStr

  val approvalStatus = leave.statusEnum
  val (approvalBg, approvalFg, approvalBorder) = when (approvalStatus) {
    LeaveStatus.APPROVED -> Triple(SignalEmeraldContainer, SignalEmeraldOnContainer, SignalEmerald)
    LeaveStatus.PENDING -> Triple(SafetyAmberContainer, SafetyAmberOnContainer, SafetyAmber)
    LeaveStatus.REJECTED, LeaveStatus.CANCELLED -> Triple(
      MaterialTheme.colorScheme.surfaceVariant,
      MaterialTheme.colorScheme.onSurfaceVariant,
      GlassRimHighlight
    )
  }

  val timingLabel = when {
    !leave.isBlockingDuty -> "AVAILABLE FOR DUTY"
    isBlockingToday -> "EXCLUDED ON $refStr"
    isUpcomingApproved -> "UPCOMING"
    else -> "COMPLETED"
  }
  val (timingBg, timingFg) = when {
    !leave.isBlockingDuty -> DepotTealContainer to DepotTealOnContainer
    isBlockingToday -> SignalCrimsonContainer to SignalCrimsonOnContainer
    isUpcomingApproved -> DepotTealContainer to DepotTealOnContainer
    else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
  }

  GlassTile(
    shape = RoundedCornerShape(22.dp),
    topColor = GlassSurfaceBright,
    bottomColor = GlassSurfaceMid,
    tintColor = if (isBlockingToday) SignalCrimsonContainer.copy(alpha = 0.25f) else Color.Transparent
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = leave.staffName,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          maxLines = 2,
          modifier = Modifier
            .weight(1f)
            .padding(end = 6.dp)
        )

        if (canApproveOrEdit || canDelete) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            if (canApproveOrEdit) {
              IconButton(
                onClick = onEdit,
                modifier = Modifier
                  .size(48.dp)
                  .testTag("edit_leave_${leave.id}")
              ) {
                Icon(
                  imageVector = Icons.Default.Edit,
                  contentDescription = "Update leave status or period",
                  tint = MaterialTheme.colorScheme.primary
                )
              }
            }
            if (canDelete) {
              IconButton(
                onClick = onDelete,
                modifier = Modifier
                  .size(48.dp)
                  .testTag("delete_leave_${leave.id}")
              ) {
                Icon(
                  imageVector = Icons.Default.Delete,
                  contentDescription = "Delete leave record",
                  tint = SignalCrimson
                )
              }
            }
          }
        }
      }

      val isTrainingLeave = leave.leaveTypeEnum == LeaveType.TRAINING
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        GlassPillBadge(
          text = if (isTrainingLeave) "TRAINING" else leave.leaveTypeEnum.code,
          tintColor = if (isTrainingLeave) DepotTealContainer.copy(alpha = 0.72f) else SignalCrimsonContainer.copy(alpha = 0.65f),
          textColor = if (isTrainingLeave) DepotTealOnContainer else SignalCrimsonOnContainer,
          borderColor = if (isTrainingLeave) DepotTeal else SignalCrimson
        )
        GlassPillBadge(
          text = approvalStatus.label.uppercase(),
          tintColor = approvalBg.copy(alpha = 0.72f),
          textColor = approvalFg,
          borderColor = approvalBorder
        )
        GlassPillBadge(
          text = timingLabel,
          tintColor = timingBg.copy(alpha = 0.65f),
          textColor = timingFg,
          borderColor = if (isBlockingToday) SignalCrimson else GlassRimHighlight
        )
      }

      Text(
        text = "Period: ${leave.startDate} to ${leave.endDate}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      if (leave.forwardedToName.isNotBlank()) {
        Text(
          text = "Forwarded to SSE: ${leave.forwardedToName}" +
            if (leave.appliedByName.isNotBlank()) " · Applied by ${leave.appliedByName}" else "",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary
        )
      }
      if (leave.reviewedByName.isNotBlank()) {
        Text(
          text = "Reviewed by: ${leave.reviewedByName}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      if (leave.notes.isNotBlank()) {
        Text(
          text = leave.notes,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      if (canApproveOrEdit && approvalStatus == LeaveStatus.PENDING) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Button(
            onClick = onApprove,
            enabled = !isBusy,
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 48.dp)
              .liquidGlassAccent(shape = RoundedCornerShape(12.dp), baseColor = SignalEmerald)
              .testTag("approve_leave_${leave.id}")
          ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Approve")
          }
          OutlinedButton(
            onClick = onReject,
            enabled = !isBusy,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 48.dp)
              .testTag("reject_leave_${leave.id}")
          ) {
            Icon(Icons.Default.Close, contentDescription = null, tint = SignalCrimson, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Reject", color = SignalCrimson)
          }
        }
      }
    }
  }
}

@Composable
private fun StaffLeavePickerDialog(
  staffList: List<StaffMember>,
  selectedStaffId: String?,
  leaves: List<LeaveRecord>,
  referenceDateStr: String,
  onDismiss: () -> Unit,
  onSelectStaff: (StaffMember) -> Unit
) {
  var query by remember { mutableStateOf("") }
  val filtered = remember(staffList, query) {
    if (query.isBlank()) staffList
    else staffList.filter { member ->
      member.name.contains(query, ignoreCase = true) ||
        member.designationEnum.label.contains(query, ignoreCase = true) ||
        member.serialNo.toString() == query.trim()
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Select PPIO Staff Member", style = MaterialTheme.typography.titleMedium) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          placeholder = { Text("Search name, S.NO, or role...") },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("leave_staff_search_input")
        )

        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          items(filtered, key = { it.id }) { member ->
            val isSelected = member.id == selectedStaffId
            val activeLeaveToday = leaves.any { it.staffId == member.id && it.isBlockingDate(referenceDateStr) }
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
              border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable { onSelectStaff(member) }
                .testTag("leave_staff_option_${member.id}")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "${member.serialNo}. ${member.name}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Text(
                    text = buildStaffCapabilitySummary(member),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                if (activeLeaveToday) {
                  Surface(color = SignalCrimsonContainer, shape = RoundedCornerShape(4.dp)) {
                    Text(
                      text = "ON LEAVE",
                      style = MaterialTheme.typography.labelSmall,
                      color = SignalCrimsonOnContainer,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                  }
                } else if (isSelected) {
                  Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                  )
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Close")
      }
    }
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleDatePickerModal(
  initialDate: LocalDate,
  title: String,
  onDismiss: () -> Unit,
  onConfirm: (LocalDate) -> Unit
) {
  val initialMillis = remember(initialDate) {
    initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
  }
  val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

  DatePickerDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(
        onClick = {
          val millis = state.selectedDateMillis ?: initialMillis
          val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
          onConfirm(picked)
        }
      ) {
        Text("Apply")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  ) {
    DatePicker(
      state = state,
      title = { Text(title, modifier = Modifier.padding(start = 24.dp, top = 16.dp)) }
    )
  }
}

@Composable
private fun DateRangePickerModal(
  initialStart: LocalDate,
  initialEnd: LocalDate,
  onDismiss: () -> Unit,
  onConfirm: (LocalDate, LocalDate) -> Unit
) {
  CompactDateRangePickerDialog(
    initialStart = initialStart,
    initialEnd = initialEnd,
    onDismiss = onDismiss,
    onConfirm = onConfirm
  )
}

@Composable
private fun DateStepperField(
  label: String,
  date: LocalDate,
  onPickDate: () -> Unit,
  onDec: () -> Unit,
  onInc: () -> Unit,
  modifier: Modifier = Modifier,
  tagPrefix: String
) {
  Box(
    modifier = modifier.liquidGlassSurface(
      shape = RoundedCornerShape(16.dp),
      topColor = GlassSurfaceBright,
      bottomColor = GlassSurfaceSoft,
      borderWidth = 1.dp
    )
  ) {
    Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = label,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(
          imageVector = Icons.Default.CalendarMonth,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(14.dp)
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        IconButton(
          onClick = onDec,
          modifier = Modifier
            .size(48.dp)
            .testTag("${tagPrefix}_dec")
        ) {
          Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Decrease $label")
        }
        Box(
          modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .clickable(onClick = onPickDate)
            .testTag("${tagPrefix}_picker"),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = date.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
          )
        }
        IconButton(
          onClick = onInc,
          modifier = Modifier
            .size(48.dp)
            .testTag("${tagPrefix}_inc")
        ) {
          Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Increase $label")
        }
      }
    }
  }
}

@Composable
private fun RestDayRow(
  member: StaffMember,
  canEdit: Boolean,
  onSelectRestDay: (WeeklyRestDay) -> Unit
) {
  var expanded by remember { mutableStateOf(false) }

  GlassTile(
    shape = RoundedCornerShape(22.dp),
    topColor = GlassSurfaceBright,
    bottomColor = GlassSurfaceMid,
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "${member.serialNo}. ${member.name}",
          style = MaterialTheme.typography.titleSmall
        )
        Text(
          text = buildStaffCapabilitySummary(member),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      Box {
        Surface(
          color = if (member.restDayEnum == WeeklyRestDay.NONE) {
            MaterialTheme.colorScheme.surfaceVariant
          } else {
            DepotTealContainer
          },
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier
            .heightIn(min = 48.dp)
            .then(if (canEdit) Modifier.clickable { expanded = true } else Modifier)
            .testTag("rest_dropdown_${member.id}")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Text(
              text = member.restDayEnum.label,
              style = MaterialTheme.typography.labelMedium,
              color = if (member.restDayEnum == WeeklyRestDay.NONE) {
                MaterialTheme.colorScheme.onSurfaceVariant
              } else {
                DepotTealOnContainer
              }
            )
            if (canEdit) {
              Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Change weekly rest day",
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }

        if (canEdit) {
          DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            WeeklyRestDay.entries.forEach { day ->
              DropdownMenuItem(
                text = { Text(day.label) },
                onClick = {
                  expanded = false
                  onSelectRestDay(day)
                }
              )
            }
          }
        }
      }
    }
  }
}

private fun buildStaffCapabilitySummary(member: StaffMember): String = buildString {
  append(member.designationEnum.label)
  if (member.isPointsman) append(" · Pointsman")
  if (member.stationDuty && member.shedDuty) append(" · Station & Shed")
  else if (member.stationDuty) append(" · Station")
  else if (member.shedDuty) append(" · Shed")
  else append(" · Supervisory")
}
