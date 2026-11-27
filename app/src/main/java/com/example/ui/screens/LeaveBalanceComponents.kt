package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.SpecialLeaveCreditReason
import com.example.domain.StaffLeaveBalanceSummary
import com.example.model.LapUpdateStatus
import com.example.model.StaffLeaveAccount
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
import com.example.ui.theme.liquidGlassSurface
import java.time.format.DateTimeFormatter

private val creditDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy (EEE)")

@Composable
fun LeaveBalanceOverviewCard(
  summary: StaffLeaveBalanceSummary,
  canUpdateLap: Boolean,
  isApprover: Boolean,
  isBusy: Boolean,
  onClickUpdateLap: () -> Unit,
  modifier: Modifier = Modifier
) {
  val account = summary.account
  val requiresApprovalForSelf = !isApprover && summary.lapSelfUpdated

  GlassTile(
    shape = RoundedCornerShape(24.dp),
    topColor = GlassSurfaceBright,
    bottomColor = GlassSurfaceMid,
    modifier = modifier
      .fillMaxWidth()
      .testTag("leave_balance_overview_card")
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
            tintColor = DepotTealContainer.copy(alpha = 0.65f),
            modifier = Modifier.size(42.dp)
          ) {
            Icon(
              imageVector = Icons.Default.AccountBalanceWallet,
              contentDescription = null,
              tint = DepotTeal
            )
          }
          Column {
            Text(
              text = "Leave Balance (${summary.year})",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "#${summary.staff.serialNo} ${summary.staff.name} · Jan–Dec",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 2
            )
          }
        }
        GlassPillBadge(
          text = "JAN–DEC ${summary.year}",
          tintColor = DepotTealContainer.copy(alpha = 0.55f),
          textColor = DepotTealOnContainer,
          borderColor = DepotTeal
        )
      }

      // Row 1: CL & LAP
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        LeaveQuotaMiniTile(
          code = "CL",
          balance = summary.clRemaining,
          availed = summary.clAvailed,
          accent = RailwayCobalt,
          containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f),
          modifier = Modifier
            .weight(1f)
            .testTag("balance_tile_cl")
        )
        LeaveQuotaMiniTile(
          code = "LAP",
          balance = summary.lapRemaining,
          availed = summary.lapAvailed,
          accent = DepotTeal,
          containerColor = DepotTealContainer.copy(alpha = 0.36f),
          modifier = Modifier
            .weight(1f)
            .testTag("balance_tile_lap")
        )
      }

      // Row 2: LHAP & CR
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        LeaveQuotaMiniTile(
          code = "LHAP",
          balance = summary.lhapRemaining,
          availed = summary.lhapAvailed,
          accent = SafetyAmber,
          containerColor = SafetyAmberContainer.copy(alpha = 0.36f),
          modifier = Modifier
            .weight(1f)
            .testTag("balance_tile_lhap")
        )
        LeaveQuotaMiniTile(
          code = "CR",
          balance = summary.specialRemaining,
          availed = summary.specialAvailedTotal,
          accent = SignalEmerald,
          containerColor = SignalEmeraldContainer.copy(alpha = 0.36f),
          modifier = Modifier
            .weight(1f)
            .testTag("balance_tile_special")
        )
      }

      // Pending / Reviewed LAP Correction Status Banner
      if (account != null) {
        when (account.pendingLapStatusEnum) {
          LapUpdateStatus.PENDING -> {
            Surface(
              color = SafetyAmberContainer.copy(alpha = 0.85f),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("lap_pending_approval_banner")
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.PendingActions,
                  contentDescription = null,
                  tint = SafetyAmber
                )
                Column(modifier = Modifier.weight(1f)) {
                  val lhapText = if (account.pendingLhapBalance >= 0) " · LHAP: ${account.pendingLhapBalance}" else ""
                  Text(
                    text = "Leave Balance Update Pending Approval (LAP: ${account.pendingLapBalance}$lhapText)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = SafetyAmberOnContainer
                  )
                  if (account.pendingLapReason.isNotBlank()) {
                    Text(
                      text = "Reason: ${account.pendingLapReason}",
                      style = MaterialTheme.typography.bodySmall,
                      color = SafetyAmberOnContainer
                    )
                  }
                }
              }
            }
          }
          LapUpdateStatus.REJECTED -> {
            val lhapText = if (account.pendingLhapBalance >= 0) " · LHAP: ${account.pendingLhapBalance}" else ""
            Surface(
              color = SignalCrimsonContainer.copy(alpha = 0.78f),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = "Last leave balance update request (LAP: ${account.pendingLapBalance}$lhapText) was rejected" +
                  if (account.pendingLapReviewedByName.isNotBlank()) " by ${account.pendingLapReviewedByName}." else ".",
                style = MaterialTheme.typography.bodySmall,
                color = SignalCrimsonOnContainer,
                modifier = Modifier.padding(12.dp)
              )
            }
          }
          LapUpdateStatus.APPROVED -> {
            if (account.pendingLapReviewedByName.isNotBlank()) {
              Surface(
                color = SignalEmeraldContainer.copy(alpha = 0.7f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.Verified,
                    contentDescription = null,
                    tint = SignalEmerald,
                    modifier = Modifier.size(18.dp)
                  )
                  Text(
                    text = "Leave balance verified by ${account.pendingLapReviewedByName}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SignalEmeraldOnContainer
                  )
                }
              }
            }
          }
          LapUpdateStatus.NONE -> Unit
        }
      }

      if (canUpdateLap) {
        OutlinedButton(
          onClick = onClickUpdateLap,
          enabled = !isBusy,
          colors = ButtonDefaults.outlinedButtonColors(containerColor = GlassSurfaceSoft),
          border = BorderStroke(1.dp, if (requiresApprovalForSelf) SafetyAmber else DepotTeal),
          shape = RoundedCornerShape(14.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("update_lap_balance_button")
        ) {
          Icon(
            imageVector = if (requiresApprovalForSelf) Icons.Default.Lock else Icons.Default.Edit,
            contentDescription = null,
            tint = if (requiresApprovalForSelf) SafetyAmber else DepotTeal,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = when {
              isApprover -> "Update ${summary.staff.name.substringBefore(" ")}'s Leave Balance"
              requiresApprovalForSelf -> "Request Leave Balance Update (Approval)"
              else -> "Update Leave Balance (One-Time Self-Update)"
            },
            color = if (requiresApprovalForSelf) SafetyAmberOnContainer else DepotTealOnContainer,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2
          )
        }
      }
    }
  }
}

@Composable
private fun LeaveQuotaMiniTile(
  code: String,
  balance: Int,
  availed: Int,
  accent: Color,
  containerColor: Color,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .liquidGlassSurface(
        shape = RoundedCornerShape(18.dp),
        topColor = GlassSurfaceBright,
        bottomColor = GlassSurfaceSoft,
        tintColor = containerColor,
        borderWidth = 1.dp
      )
      .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Text(
      text = code,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold,
      color = accent,
      maxLines = 1
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(
        text = "Balance",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1
      )
      Text(
        text = balance.toString(),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1
      )
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(
        text = "Availed",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1
      )
      Text(
        text = availed.toString(),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1
      )
    }
  }
}

@Composable
fun LapUpdateDialog(
  summary: StaffLeaveBalanceSummary,
  isApprover: Boolean,
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onConfirm: (enteredRemainingLap: Int, enteredRemainingLhap: Int, reason: String) -> Unit
) {
  val requiresApproval = !isApprover && summary.lapSelfUpdated
  var lapInput by remember(summary) {
    mutableStateOf(
      if (summary.hasPendingLapRequest && summary.account != null) {
        summary.account.pendingLapBalance.toString()
      } else {
        summary.lapRemaining.toString()
      }
    )
  }
  var lhapInput by remember(summary) {
    mutableStateOf(
      if (summary.hasPendingLapRequest && summary.account != null && summary.account.pendingLhapBalance >= 0) {
        summary.account.pendingLhapBalance.toString()
      } else {
        summary.lhapRemaining.toString()
      }
    )
  }
  var reasonInput by remember(summary) {
    mutableStateOf(summary.account?.pendingLapReason.orEmpty())
  }

  val parsedLap = lapInput.trim().toIntOrNull()
  val parsedLhap = lhapInput.trim().toIntOrNull()
  val lapValid = parsedLap != null && parsedLap in 0..999
  val lhapValid = parsedLhap != null && parsedLhap in 0..999
  val reasonValid = !requiresApproval || reasonInput.trim().isNotBlank()

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = GlassPopupSurface,
    title = {
      Text(
        text = when {
          isApprover -> "Update Leave Balance (${summary.year})"
          requiresApproval -> "Request Leave Balance Update"
          else -> "Update Leave Balance"
        },
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          text = "#${summary.staff.serialNo} ${summary.staff.name} · Current LAP: ${summary.lapRemaining} · LHAP: ${summary.lhapRemaining}",
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold
        )
        Surface(
          color = if (requiresApproval) SafetyAmberContainer.copy(alpha = 0.8f) else DepotTealContainer.copy(alpha = 0.7f),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = when {
              isApprover ->
                "Supervisor/Admin adjustment updates LAP and LHAP balances immediately and carries unused days forward each year (+30 LAP / +20 LHAP per year)."
              requiresApproval ->
                "You have already used your one-time leave balance self-update. Enter the corrected LAP and LHAP balances and reason — Supervisor or Admin approval is required."
              else ->
                "You can update your LAP and LHAP balances directly only ONCE. Please double-check your Service Register; any future change will require Supervisor or Admin approval."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (requiresApproval) SafetyAmberOnContainer else DepotTealOnContainer,
            modifier = Modifier.padding(10.dp)
          )
        }

        OutlinedTextField(
          value = lapInput,
          onValueChange = { raw ->
            lapInput = raw.filter { it.isDigit() }.take(3)
          },
          label = { Text("Current Remaining LAP Days (0–999)") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          isError = lapInput.isNotBlank() && !lapValid,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("lap_balance_input")
        )

        OutlinedTextField(
          value = lhapInput,
          onValueChange = { raw ->
            lhapInput = raw.filter { it.isDigit() }.take(3)
          },
          label = { Text("Current Remaining LHAP Days (0–999)") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          isError = lhapInput.isNotBlank() && !lhapValid,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("lhap_balance_input")
        )

        if (requiresApproval) {
          OutlinedTextField(
            value = reasonInput,
            onValueChange = { reasonInput = it.take(300) },
            label = { Text("Reason for Correction (Required)") },
            placeholder = { Text("e.g., Entered wrong figure from service sheet") },
            minLines = 2,
            maxLines = 3,
            isError = !reasonValid,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("lap_correction_reason_input")
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val lapVal = parsedLap ?: return@Button
          val lhapVal = parsedLhap ?: return@Button
          onConfirm(lapVal, lhapVal, reasonInput.trim())
        },
        enabled = lapValid && lhapValid && reasonValid && !isBusy,
        modifier = Modifier
          .heightIn(min = 48.dp)
          .testTag("confirm_lap_update_button")
      ) {
        Text(
          text = when {
            isApprover -> "Save Leave Balance"
            requiresApproval -> "Send for Approval"
            else -> "Save Balance (One-Time)"
          }
        )
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.heightIn(min = 48.dp)
      ) {
        Text("Cancel")
      }
    }
  )
}

@Composable
fun PendingLapApprovalsCard(
  pendingAccounts: List<StaffLeaveAccount>,
  isBusy: Boolean,
  onApprove: (StaffLeaveAccount) -> Unit,
  onReject: (StaffLeaveAccount) -> Unit,
  modifier: Modifier = Modifier
) {
  if (pendingAccounts.isEmpty()) return

  GlassTile(
    shape = RoundedCornerShape(24.dp),
    topColor = GlassSurfaceBright,
    bottomColor = GlassSurfaceMid,
    tintColor = SafetyAmberContainer.copy(alpha = 0.25f),
    modifier = modifier
      .fillMaxWidth()
      .testTag("pending_lap_approvals_card")
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
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
            tintColor = SafetyAmberContainer.copy(alpha = 0.65f),
            modifier = Modifier.size(40.dp)
          ) {
            Icon(Icons.Default.PendingActions, contentDescription = null, tint = SafetyAmber)
          }
          Column {
            Text(
              text = "Pending Leave Balance Approvals",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "Staff requesting post-first-update LAP / LHAP balance correction",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
        GlassPillBadge(
          text = "${pendingAccounts.size} PENDING",
          tintColor = SafetyAmberContainer,
          textColor = SafetyAmberOnContainer,
          borderColor = SafetyAmber
        )
      }

      pendingAccounts.forEachIndexed { idx, acc ->
        if (idx > 0) HorizontalDivider(color = GlassRimHighlight)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = acc.staffName,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
              maxLines = 2
            )
            val lhapPart = if (acc.pendingLhapBalance >= 0) " · ${acc.pendingLhapBalance} LHAP" else ""
            GlassPillBadge(
              text = "REQ: ${acc.pendingLapBalance} LAP$lhapPart (${acc.pendingLapYear})",
              tintColor = DepotTealContainer.copy(alpha = 0.7f),
              textColor = DepotTealOnContainer,
              borderColor = DepotTeal
            )
          }
          if (acc.pendingLapReason.isNotBlank()) {
            Text(
              text = "Reason: ${acc.pendingLapReason}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Button(
              onClick = { onApprove(acc) },
              enabled = !isBusy,
              colors = ButtonDefaults.buttonColors(containerColor = SignalEmerald),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("approve_lap_req_${acc.staffId}")
            ) {
              Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Approve")
            }
            OutlinedButton(
              onClick = { onReject(acc) },
              enabled = !isBusy,
              border = BorderStroke(1.dp, SignalCrimson),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("reject_lap_req_${acc.staffId}")
            ) {
              Icon(
                Icons.Default.Close,
                contentDescription = null,
                tint = SignalCrimson,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Reject", color = SignalCrimson)
            }
          }
        }
      }
    }
  }
}

@Composable
fun SpecialLeaveCreditsCard(
  summary: StaffLeaveBalanceSummary,
  modifier: Modifier = Modifier
) {
  GlassTile(
    shape = RoundedCornerShape(24.dp),
    topColor = GlassSurfaceBright,
    bottomColor = GlassSurfaceMid,
    modifier = modifier
      .fillMaxWidth()
      .testTag("special_leave_credits_card")
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
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
            tintColor = SignalEmeraldContainer.copy(alpha = 0.65f),
            modifier = Modifier.size(40.dp)
          ) {
            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = SignalEmerald)
          }
          Column {
            Text(
              text = "Special Leave Account",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "Auto-awarded for Rest Day duty or 2 duties in a single day",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
        GlassPillBadge(
          text = "${summary.specialRemaining} BAL",
          tintColor = SignalEmeraldContainer.copy(alpha = 0.75f),
          textColor = SignalEmeraldOnContainer,
          borderColor = SignalEmerald
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        SpecialStatChip(
          label = "Rest Day Duty (${summary.year})",
          value = "+${summary.specialRestDayCreditsThisYear}",
          accent = DepotTeal,
          modifier = Modifier.weight(1f)
        )
        SpecialStatChip(
          label = "Double Duty (${summary.year})",
          value = "+${summary.specialDoubleDutyCreditsThisYear}",
          accent = SafetyAmber,
          modifier = Modifier.weight(1f)
        )
        SpecialStatChip(
          label = "Availed (${summary.year})",
          value = "${summary.specialAvailedThisYear}",
          accent = SignalCrimson,
          modifier = Modifier.weight(1f)
        )
      }

      if (summary.specialCreditEventsInYear.isEmpty()) {
        Text(
          text = "No Rest Day or Double Duty special leave awards recorded in ${summary.year} yet.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      } else {
        HorizontalDivider(color = GlassRimHighlight)
        summary.specialCreditEventsInYear.take(6).forEach { event ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "${event.date.format(creditDateFormatter)} · ${event.reason.label}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2
              )
              Text(
                text = event.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
              )
            }
            Spacer(modifier = Modifier.width(8.dp))
            GlassPillBadge(
              text = "+${event.credits} CR",
              tintColor = if (event.reason == SpecialLeaveCreditReason.REST_DAY_DUTY) {
                DepotTealContainer.copy(alpha = 0.7f)
              } else {
                SafetyAmberContainer.copy(alpha = 0.7f)
              },
              textColor = if (event.reason == SpecialLeaveCreditReason.REST_DAY_DUTY) {
                DepotTealOnContainer
              } else {
                SafetyAmberOnContainer
              },
              borderColor = if (event.reason == SpecialLeaveCreditReason.REST_DAY_DUTY) {
                DepotTeal
              } else {
                SafetyAmber
              }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SpecialStatChip(
  label: String,
  value: String,
  accent: Color,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .liquidGlassSurface(
        shape = RoundedCornerShape(14.dp),
        topColor = GlassSurfaceBright,
        bottomColor = GlassSurfaceSoft,
        borderWidth = 1.dp
      )
      .padding(horizontal = 8.dp, vertical = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = value,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = accent
    )
    Spacer(modifier = Modifier.height(2.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      maxLines = 2
    )
  }
}
