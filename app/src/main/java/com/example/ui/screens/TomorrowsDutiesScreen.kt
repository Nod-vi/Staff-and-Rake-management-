package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.DutyDateLock
import com.example.model.ShiftSlotAssignment
import com.example.model.StaffMember
import com.example.model.UserRole
import com.example.share.DailyDutiesExporter
import com.example.share.DutyShiftSheetBlock
import com.example.share.GeneralStaffRow
import com.example.ui.theme.DutySheetBorder
import com.example.ui.theme.DutySheetMedRowBg
import com.example.ui.theme.DutySheetNavy
import com.example.ui.theme.DutySheetPointsmanBg
import com.example.ui.theme.DutySheetPointsmanInk
import com.example.ui.theme.DutySheetShedHeaderBg
import com.example.ui.theme.DutySheetShunterBg
import com.example.ui.theme.DutySheetShunterInk
import com.example.ui.theme.DutySheetStationHeaderBg
import com.example.ui.theme.DutySheetStationRowBg
import com.example.ui.theme.DutySheetSubHeaderBg
import com.example.ui.theme.GlassPillBadge
import com.example.ui.theme.GlassRimHighlight
import com.example.ui.theme.GlassSurfaceBright
import com.example.ui.theme.GlassSurfaceMid
import com.example.ui.theme.GlassSurfaceSoft
import com.example.ui.theme.GlassTile
import com.example.ui.theme.NavyInk
import com.example.ui.theme.SignalCrimsonOnContainer
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.SignalEmeraldBright
import com.example.ui.theme.SignalEmeraldContainer
import com.example.ui.theme.SignalEmeraldOnContainer
import com.example.ui.theme.liquidGlassAccent
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun TomorrowsDutiesScreen(
  staffList: List<StaffMember>,
  assignments: List<ShiftSlotAssignment>,
  userRole: UserRole = UserRole.SUPERVISOR,
  roleAccess: com.example.model.ResolvedRoleAccess = com.example.model.RoleAccessPolicy.resolve(userRole, emptyMap()),
  dutyLocks: List<DutyDateLock> = emptyList(),
  allowTechnicianShare: Boolean = true,
  showShunterForTechnicians: Boolean = false,
  includeShunterInExport: Boolean = false,
  isBusy: Boolean,
  onToggleTechnicianShare: (Boolean) -> Unit = {},
  onToggleShowShunterForTechnicians: (Boolean) -> Unit = {},
  onToggleIncludeShunterInExport: (Boolean) -> Unit = {},
  onAutoFillRange: (LocalDate) -> Unit,
  onNotifyMessage: (String) -> Unit
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  var baseTodayEpoch by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
  val baseToday = remember(baseTodayEpoch) { LocalDate.ofEpochDay(baseTodayEpoch) }
  var isExporting by remember { mutableStateOf(false) }
  val canShowShareOptions = roleAccess.canExportDuties || (userRole == UserRole.TECHNICIAN && allowTechnicianShare)

  val isShiftInchargeOrAbove = roleAccess.canApproveDuties ||
    userRole == UserRole.ADMIN ||
    userRole == UserRole.SSE ||
    userRole == UserRole.SUPERVISOR ||
    userRole == UserRole.JE ||
    userRole == UserRole.SHIFT_INCHARGE
  val showShunterOnScreen = isShiftInchargeOrAbove || showShunterForTechnicians

  // ShiftIncharge/JE can locally toggle Shunter for their own WhatsApp/PDF export, initialized from depot setting.
  var localShiftInchargeExportShunter by rememberSaveable(includeShunterInExport) {
    mutableStateOf(includeShunterInExport)
  }
  val effectiveExportShunter = when {
    roleAccess.canApproveDuties -> includeShunterInExport
    userRole == UserRole.SHIFT_INCHARGE || userRole == UserRole.JE -> localShiftInchargeExportShunter
    else -> showShunterForTechnicians && includeShunterInExport
  }

  val sheet = remember(baseToday, staffList, assignments, showShunterOnScreen) {
    DailyDutiesExporter.buildSheetData(
      todayDate = baseToday,
      staffList = staffList,
      assignments = assignments,
      includeShunter = showShunterOnScreen
    )
  }
  val exportSheet = remember(baseToday, staffList, assignments, effectiveExportShunter, sheet) {
    if (effectiveExportShunter == sheet.showShunter) {
      sheet
    } else {
      DailyDutiesExporter.buildSheetData(
        todayDate = baseToday,
        staffList = staffList,
        assignments = assignments,
        includeShunter = effectiveExportShunter
      )
    }
  }
  val tomorrowLock = remember(dutyLocks, sheet.tomorrowDate) {
    dutyLocks.firstOrNull { it.dutyDate == sheet.tomorrowDate.toString() }
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
      item(key = "duties_controls", contentType = "controls") {
        GlassTile(
          shape = RoundedCornerShape(24.dp),
          topColor = GlassSurfaceBright,
          bottomColor = GlassSurfaceMid
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              IconButton(
                onClick = { baseTodayEpoch -= 1L },
                modifier = Modifier
                  .size(48.dp)
                  .testTag("duties_prev_day")
              ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous duty cycle")
              }

              Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Text(
                  text = "Tomorrow's Duties Sheet",
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = "Tonight (${sheet.todayDate}) 22/06 · Tomorrow (${sheet.tomorrowDate}) 06/14, 14/22 & 08/17",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  textAlign = TextAlign.Center
                )
              }

              IconButton(
                onClick = { baseTodayEpoch += 1L },
                modifier = Modifier
                  .size(48.dp)
                  .testTag("duties_next_day")
              ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next duty cycle")
              }
            }

            if (baseToday != LocalDate.now()) {
              Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(
                  onClick = { baseTodayEpoch = LocalDate.now().toEpochDay() },
                  modifier = Modifier.heightIn(min = 48.dp)
                ) {
                  Text("Jump to Current Date (${LocalDate.now()})")
                }
              }
            }

            if (canShowShareOptions) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Button(
                  onClick = {
                    coroutineScope.launch {
                      isExporting = true
                      try {
                        val pngFile = DailyDutiesExporter.createPngFile(context, exportSheet)
                        val uri = DailyDutiesExporter.getShareUri(context, pngFile)
                        DailyDutiesExporter.launchWhatsAppShare(context, uri)
                      } catch (e: Exception) {
                        onNotifyMessage("Share failed: ${e.localizedMessage ?: "Unable to export sheet"}")
                      } finally {
                        isExporting = false
                      }
                    }
                  },
                  enabled = !isExporting,
                  elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
                  colors = ButtonDefaults.buttonColors(
                    containerColor = if (!isExporting) Color.Transparent else SignalEmerald.copy(alpha = 0.5f),
                    contentColor = Color.White
                  ),
                  modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .let { mod ->
                      if (!isExporting) {
                        mod.liquidGlassAccent(
                          shape = RoundedCornerShape(16.dp),
                          baseColor = SignalEmerald,
                          highlightColor = SignalEmeraldBright
                        )
                      } else {
                        mod
                      }
                    }
                    .testTag("share_whatsapp_button"),
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                  shape = RoundedCornerShape(16.dp)
                ) {
                  Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = if (isExporting) "Preparing..." else "Share WhatsApp",
                    maxLines = 2,
                    textAlign = TextAlign.Center
                  )
                }

                OutlinedButton(
                  onClick = {
                    coroutineScope.launch {
                      isExporting = true
                      try {
                        val pdfFile = DailyDutiesExporter.createPdfFile(context, exportSheet)
                        val uri = DailyDutiesExporter.getShareUri(context, pdfFile)
                        val caption = DailyDutiesExporter.buildPlainTextSummary(exportSheet)
                        DailyDutiesExporter.launchFileShare(
                          context = context,
                          fileUri = uri,
                          mimeType = "application/pdf",
                          captionText = caption,
                          chooserTitle = "Share Daily Duties PDF"
                        )
                      } catch (e: Exception) {
                        onNotifyMessage("PDF export failed: ${e.localizedMessage ?: "Error"}")
                      } finally {
                        isExporting = false
                      }
                    }
                  },
                  enabled = !isExporting,
                  colors = ButtonDefaults.outlinedButtonColors(containerColor = GlassSurfaceSoft),
                  border = BorderStroke(1.dp, GlassRimHighlight),
                  modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("share_pdf_button"),
                  shape = RoundedCornerShape(16.dp)
                ) {
                  Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("PDF")
                }
              }
            }

            if (roleAccess.canApproveDuties) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(14.dp))
                  .background(GlassSurfaceSoft)
                  .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Show Shunter to Technicians",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Text(
                    text = if (showShunterForTechnicians) {
                      "Visible to Technicians in Duties & Shifts"
                    } else {
                      "Hidden from Technicians (visible to ShiftIncharge & above)"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                Switch(
                  checked = showShunterForTechnicians,
                  onCheckedChange = onToggleShowShunterForTechnicians,
                  enabled = !isBusy,
                  modifier = Modifier.testTag("duties_toggle_shunter_tech_switch")
                )
              }

              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(14.dp))
                  .background(GlassSurfaceSoft)
                  .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Include Shunter in WhatsApp & PDF",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Text(
                    text = if (includeShunterInExport) {
                      "WhatsApp image & PDF include Shunter rows"
                    } else {
                      "WhatsApp image & PDF exclude Shunter rows"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                Switch(
                  checked = includeShunterInExport,
                  onCheckedChange = onToggleIncludeShunterInExport,
                  enabled = !isBusy,
                  modifier = Modifier.testTag("duties_toggle_shunter_export_switch")
                )
              }
            } else if ((userRole == UserRole.SHIFT_INCHARGE || userRole == UserRole.JE) && canShowShareOptions) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(14.dp))
                  .background(GlassSurfaceSoft)
                  .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Include Shunter in WhatsApp & PDF",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Text(
                    text = if (localShiftInchargeExportShunter) {
                      "WhatsApp image & PDF will include Shunter"
                    } else {
                      "WhatsApp image & PDF will exclude Shunter"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                Switch(
                  checked = localShiftInchargeExportShunter,
                  onCheckedChange = { localShiftInchargeExportShunter = it },
                  enabled = !isBusy,
                  modifier = Modifier.testTag("duties_toggle_shunter_export_switch")
                )
              }
            }

            if (roleAccess.isAdmin) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(14.dp))
                  .background(GlassSurfaceSoft)
                  .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Technician Share Options",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Text(
                    text = if (allowTechnicianShare) {
                      "Visible to Technicians (WhatsApp & PDF)"
                    } else {
                      "Hidden from Technicians"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                Switch(
                  checked = allowTechnicianShare,
                  onCheckedChange = onToggleTechnicianShare,
                  enabled = !isBusy,
                  modifier = Modifier.testTag("duties_toggle_technician_share_switch")
                )
              }
            }

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
              GlassPillBadge(
                text = if (tomorrowLock != null) {
                  "APPROVED & LOCKED BY ${tomorrowLock.lockedByName.ifBlank { "SUPERVISOR" }.uppercase()}"
                } else {
                  "SHARED DEPOT PUBLISHED ROSTER"
                },
                tintColor = if (tomorrowLock != null) SignalEmeraldContainer.copy(alpha = 0.72f) else GlassSurfaceSoft,
                textColor = if (tomorrowLock != null) SignalEmeraldOnContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                borderColor = if (tomorrowLock != null) SignalEmerald else GlassRimHighlight
              )
            }

            if (roleAccess.canDirectEditDuties) {
              OutlinedButton(
                onClick = { onAutoFillRange(baseToday) },
                enabled = !isBusy,
                colors = ButtonDefaults.outlinedButtonColors(containerColor = GlassSurfaceSoft),
                border = BorderStroke(1.dp, GlassRimHighlight),
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(min = 48.dp)
                  .testTag("duties_auto_fill_button"),
                shape = RoundedCornerShape(16.dp)
              ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Auto-Fill Missing Slots (Today & Tomorrow)")
              }
            }
          }
        }
      }

      // Main Printable / Shareable Sheet Preview framed in Liquid Glass bezel
      item(key = "duties_sheet_header", contentType = "sheet_header") {
        GlassTile(
          shape = RoundedCornerShape(22.dp),
          topColor = GlassSurfaceBright,
          bottomColor = GlassSurfaceMid
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(6.dp)
              .clip(RoundedCornerShape(16.dp))
              .border(1.5.dp, DutySheetBorder, RoundedCornerShape(16.dp))
              .background(Color.White)
          ) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .background(DutySheetNavy)
                .padding(vertical = 12.dp, horizontal = 12.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "DAILY DUTIES",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
            }
            HorizontalDivider(color = DutySheetBorder, thickness = 1.dp)
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .background(DutySheetSubHeaderBg)
                .padding(vertical = 8.dp, horizontal = 12.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = sheet.headerRangeText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = NavyInk,
                textAlign = TextAlign.Center
              )
            }
          }
        }
      }

      item(key = "shift_3_block", contentType = "shift_block") {
        GlassTile(
          shape = RoundedCornerShape(22.dp),
          topColor = GlassSurfaceBright,
          bottomColor = GlassSurfaceMid
        ) {
          Box(modifier = Modifier.padding(6.dp).clip(RoundedCornerShape(16.dp))) {
            DutyShiftTableCard(block = sheet.shift3Night, showShunter = sheet.showShunter)
          }
        }
      }
      item(key = "shift_1_block", contentType = "shift_block") {
        GlassTile(
          shape = RoundedCornerShape(22.dp),
          topColor = GlassSurfaceBright,
          bottomColor = GlassSurfaceMid
        ) {
          Box(modifier = Modifier.padding(6.dp).clip(RoundedCornerShape(16.dp))) {
            DutyShiftTableCard(block = sheet.shift1Morning, showShunter = sheet.showShunter)
          }
        }
      }
      item(key = "shift_2_block", contentType = "shift_block") {
        GlassTile(
          shape = RoundedCornerShape(22.dp),
          topColor = GlassSurfaceBright,
          bottomColor = GlassSurfaceMid
        ) {
          Box(modifier = Modifier.padding(6.dp).clip(RoundedCornerShape(16.dp))) {
            DutyShiftTableCard(block = sheet.shift2Afternoon, showShunter = sheet.showShunter)
          }
        }
      }
      item(key = "general_shift_block", contentType = "general_block") {
        GlassTile(
          shape = RoundedCornerShape(22.dp),
          topColor = GlassSurfaceBright,
          bottomColor = GlassSurfaceMid
        ) {
          Box(modifier = Modifier.padding(6.dp).clip(RoundedCornerShape(16.dp))) {
            GeneralShiftTableCard(
              headerTitle = sheet.generalHeaderTitle,
              rows = sheet.generalStaff,
              shunterName = sheet.generalShunterName,
              showShunter = sheet.showShunter
            )
          }
        }
      }

      item(key = "duties_bottom_spacer", contentType = "spacer") {
        Spacer(modifier = Modifier.height(16.dp))
      }
    }
  }
}

@Composable
private fun DutyShiftTableCard(
  block: DutyShiftSheetBlock,
  showShunter: Boolean = false
) {
  val rowCount = maxOf(4, block.shedStaff.size, block.stationStaff.size + if (block.showMedRow) 1 else 0)

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .border(1.5.dp, DutySheetBorder)
      .background(Color.White)
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(DutySheetNavy)
        .padding(vertical = 10.dp, horizontal = 12.dp),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = block.title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        textAlign = TextAlign.Center
      )
    }

    Row(modifier = Modifier.fillMaxWidth()) {
      Box(
        modifier = Modifier
          .weight(1f)
          .heightIn(min = 34.dp)
          .background(DutySheetShedHeaderBg)
          .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
      ) {
        Text(
          text = "SHED STAFF",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = DutySheetNavy
        )
      }
      Box(
        modifier = Modifier
          .weight(1f)
          .heightIn(min = 34.dp)
          .background(DutySheetStationHeaderBg)
          .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterEnd
      ) {
        Text(
          text = "STATION STAFF",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = SignalEmeraldOnContainer
        )
      }
    }
    HorizontalDivider(color = DutySheetBorder.copy(alpha = 0.6f))

    for (idx in 0 until rowCount) {
      val shedName = block.shedStaff.getOrNull(idx).orEmpty()
      val isMedRow = block.showMedRow && idx == block.stationStaff.size
      val stationName = block.stationStaff.getOrNull(idx).orEmpty()

      Row(modifier = Modifier.fillMaxWidth()) {
        Box(
          modifier = Modifier
            .weight(1f)
            .heightIn(min = 38.dp)
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 9.dp),
          contentAlignment = Alignment.CenterStart
        ) {
          Text(
            text = shedName,
            style = MaterialTheme.typography.bodyMedium,
            color = NavyInk,
            maxLines = 2
          )
        }

        if (isMedRow) {
          Row(
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 38.dp)
              .background(DutySheetMedRowBg),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .weight(0.35f)
                .padding(horizontal = 6.dp, vertical = 9.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "MED",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = SignalCrimsonOnContainer
              )
            }
            Box(
              modifier = Modifier
                .weight(0.65f)
                .padding(horizontal = 10.dp, vertical = 9.dp),
              contentAlignment = Alignment.CenterEnd
            ) {
              Text(
                text = block.medStaff ?: "UNASSIGNED",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = SignalCrimsonOnContainer,
                textAlign = TextAlign.End,
                maxLines = 2
              )
            }
          }
        } else {
          Box(
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 38.dp)
              .background(DutySheetStationRowBg)
              .padding(horizontal = 12.dp, vertical = 9.dp),
            contentAlignment = Alignment.CenterEnd
          ) {
            Text(
              text = stationName,
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Bold,
              color = SignalEmeraldOnContainer,
              textAlign = TextAlign.End,
              maxLines = 2
            )
          }
        }
      }
      HorizontalDivider(color = DutySheetBorder.copy(alpha = 0.45f))
    }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(DutySheetPointsmanBg)
        .padding(horizontal = 12.dp, vertical = 10.dp),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = "POINTSMAN: ${block.pointsmanName ?: "UNASSIGNED"}",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = DutySheetPointsmanInk,
        textAlign = TextAlign.Center
      )
    }

    if (showShunter) {
      HorizontalDivider(color = DutySheetBorder.copy(alpha = 0.6f))
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(DutySheetShunterBg)
          .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = "SHUNTER: ${block.shunterName ?: "UNASSIGNED"}",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color = DutySheetShunterInk,
          textAlign = TextAlign.Center
        )
      }
    }
  }
}

@Composable
private fun GeneralShiftTableCard(
  headerTitle: String,
  rows: List<GeneralStaffRow>,
  shunterName: String? = null,
  showShunter: Boolean = false
) {
  val rowCount = maxOf(2, rows.size)

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .border(1.5.dp, DutySheetBorder)
      .background(Color.White)
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(DutySheetNavy)
        .padding(vertical = 10.dp, horizontal = 12.dp),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = headerTitle,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        textAlign = TextAlign.Center
      )
    }

    Row(modifier = Modifier.fillMaxWidth()) {
      Box(
        modifier = Modifier
          .weight(1f)
          .heightIn(min = 34.dp)
          .background(DutySheetShedHeaderBg)
          .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
      ) {
        Text(
          text = "GENERAL STAFF",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = DutySheetNavy
        )
      }
      Box(
        modifier = Modifier
          .weight(1f)
          .heightIn(min = 34.dp)
          .background(DutySheetStationHeaderBg)
          .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterEnd
      ) {
        Text(
          text = "DESIGNATION",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = SignalEmeraldOnContainer
        )
      }
    }
    HorizontalDivider(color = DutySheetBorder.copy(alpha = 0.6f))

    for (idx in 0 until rowCount) {
      val row = rows.getOrNull(idx)
      Row(modifier = Modifier.fillMaxWidth()) {
        Box(
          modifier = Modifier
            .weight(1f)
            .heightIn(min = 38.dp)
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 9.dp),
          contentAlignment = Alignment.CenterStart
        ) {
          Text(
            text = row?.name ?: if (idx == 0 && rows.isEmpty()) "No general shift staff (0/3)" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = if (row == null) MaterialTheme.colorScheme.onSurfaceVariant else NavyInk,
            maxLines = 2
          )
        }
        Box(
          modifier = Modifier
            .weight(1f)
            .heightIn(min = 38.dp)
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 9.dp),
          contentAlignment = Alignment.CenterEnd
        ) {
          Text(
            text = row?.designation.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = SignalEmeraldOnContainer,
            textAlign = TextAlign.End,
            maxLines = 2
          )
        }
      }
      if (idx < rowCount - 1) {
        HorizontalDivider(color = DutySheetBorder.copy(alpha = 0.45f))
      }
    }

    if (showShunter) {
      HorizontalDivider(color = DutySheetBorder.copy(alpha = 0.6f))
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(DutySheetShunterBg)
          .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = "SHUNTER: ${shunterName ?: "UNASSIGNED"}",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color = DutySheetShunterInk,
          textAlign = TextAlign.Center
        )
      }
    }
  }
}
