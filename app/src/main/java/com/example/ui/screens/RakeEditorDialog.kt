package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.domain.RakeFormationEngine
import com.example.model.CoachRole
import com.example.model.RakeCategory
import com.example.model.RakeCoach
import com.example.model.RakeFormation
import com.example.ui.theme.DepotTeal
import com.example.ui.theme.GlassPopupSurface
import com.example.ui.theme.GlassSurfaceSoft
import com.example.ui.theme.RailwayCobalt
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SignalCrimson
import com.example.ui.theme.SignalEmerald

private val DIRECTION_OPTIONS = listOf("KZJ", "SC")

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RakeEditorDialog(
  initialRake: RakeFormation?,
  isBusy: Boolean,
  onSave: (RakeFormation, Boolean) -> Unit,
  onDismiss: () -> Unit
) {
  val isEdit = initialRake != null
  var selectedCategory by remember(initialRake) {
    mutableStateOf(initialRake?.categoryEnum ?: RakeCategory.EMU_3PHASE_12CAR)
  }
  var selectedPresetCars by remember(initialRake) {
    mutableIntStateOf(initialRake?.carCount ?: selectedCategory.defaultCarCount)
  }
  var rakeName by remember(initialRake) {
    mutableStateOf(initialRake?.rakeName.orEmpty())
  }
  var autoTrackName by remember(initialRake) {
    mutableStateOf(
      initialRake == null ||
        initialRake.rakeName.trim() == RakeFormationEngine.deriveRakeName(
          initialRake.categoryEnum,
          initialRake.sortedCoaches
        )
    )
  }
  var make by remember(initialRake) {
    mutableStateOf(initialRake?.make.orEmpty())
  }
  var tcasFitted by remember(initialRake) {
    mutableStateOf(initialRake?.tcasFitted ?: false)
  }
  var startEnd by remember(initialRake) {
    mutableStateOf(initialRake?.startEnd.orEmpty())
  }
  var finishEnd by remember(initialRake) {
    mutableStateOf(initialRake?.finishEnd.orEmpty())
  }
  var remarks by remember(initialRake) {
    mutableStateOf(initialRake?.remarks.orEmpty())
  }
  var coaches by remember(initialRake) {
    mutableStateOf(
      if (initialRake != null && initialRake.sortedCoaches.isNotEmpty()) {
        RakeFormationEngine.reindexCoaches(initialRake.sortedCoaches)
      } else {
        RakeFormationEngine.buildTemplateCoaches(
          category = RakeCategory.EMU_3PHASE_12CAR,
          carCount = RakeCategory.EMU_3PHASE_12CAR.defaultCarCount
        )
      }
    )
  }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  fun updateCoachesAndSyncName(nextCoaches: List<RakeCoach>, category: RakeCategory = selectedCategory) {
    coaches = nextCoaches
    if (autoTrackName) {
      rakeName = RakeFormationEngine.deriveRakeName(category, nextCoaches)
    }
  }

  fun applyTemplate(category: RakeCategory, carCount: Int) {
    val template = RakeFormationEngine.buildTemplateCoaches(category, carCount)
    val expandingFromTwoCabs = coaches.size == 2 &&
      coaches.all { it.coachRoleEnum.isDrivingCab } &&
      template.size > 2
    val next = if (expandingFromTwoCabs) {
      val leadCab = coaches.first()
      val tailCab = coaches.last()
      template.mapIndexed { idx, slot ->
        when (idx) {
          0 -> slot.copy(
            coachNumber = leadCab.coachNumber,
            altNumber = leadCab.altNumber,
            endTag = leadCab.endTag
          )
          template.lastIndex -> slot.copy(
            coachNumber = tailCab.coachNumber,
            altNumber = tailCab.altNumber,
            endTag = tailCab.endTag
          )
          else -> slot
        }
      }
    } else {
      template.mapIndexed { idx, slot ->
        val prev = coaches.getOrNull(idx)
        if (prev != null) {
          slot.copy(
            coachNumber = prev.coachNumber,
            altNumber = prev.altNumber,
            endTag = prev.endTag
          )
        } else {
          slot
        }
      }
    }
    updateCoachesAndSyncName(next, category)
  }

  Dialog(
    onDismissRequest = { if (!isBusy) onDismiss() },
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      decorFitsSystemWindows = true
    )
  ) {
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(horizontal = 14.dp, vertical = 20.dp),
      contentAlignment = Alignment.Center
    ) {
      val dialogMaxHeight = (maxHeight * 0.78f).coerceAtLeast(320.dp)
      Surface(
        shape = RoundedCornerShape(26.dp),
        color = GlassPopupSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        tonalElevation = 8.dp,
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 680.dp)
          .heightIn(max = dialogMaxHeight)
          .testTag("rake_editor_dialog")
      ) {
        Column(modifier = Modifier.fillMaxWidth()) {
          // Dialog Header
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
          ) {
            Text(
              text = stringResource(
                if (isEdit) R.string.rake_editor_edit_title else R.string.rake_editor_create_title
              ),
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "Configure rake type, direction, TCAS status, and coach composition",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          HorizontalDivider()

          // Scrollable Body
          Column(
            modifier = Modifier
              .weight(1f, fill = false)
              .verticalScroll(rememberScrollState())
              .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
          if (errorMessage != null) {
            Surface(
              color = MaterialTheme.colorScheme.errorContainer,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("rake_editor_error_banner")
            ) {
              Text(
                text = errorMessage!!,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(12.dp)
              )
            }
          }

          // Rake Category Selector
          Text(
            text = "Rake Type",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            RakeCategory.entries.forEach { cat ->
              FilterChip(
                selected = selectedCategory == cat,
                onClick = {
                  selectedCategory = cat
                  selectedPresetCars = cat.defaultCarCount
                  if (!isEdit) {
                    applyTemplate(cat, cat.defaultCarCount)
                  } else if (autoTrackName) {
                    rakeName = RakeFormationEngine.deriveRakeName(cat, coaches)
                  }
                  errorMessage = null
                },
                label = { Text(cat.label, maxLines = 1) },
                modifier = Modifier
                  .heightIn(min = 48.dp)
                  .testTag("rake_editor_cat_${cat.name.lowercase()}")
              )
            }
          }

          // Car Count Template Presets
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = "Formation Template",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold
            )
            TextButton(
              onClick = {
                applyTemplate(selectedCategory, selectedPresetCars)
                errorMessage = null
              },
              modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("rake_editor_apply_template_btn")
            ) {
              Text(stringResource(R.string.rake_editor_apply_template), fontWeight = FontWeight.Bold)
            }
          }
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            selectedCategory.presetCarCounts.forEach { presetCount ->
              FilterChip(
                selected = selectedPresetCars == presetCount,
                onClick = {
                  selectedPresetCars = presetCount
                  applyTemplate(selectedCategory, presetCount)
                  errorMessage = null
                },
                label = {
                  Text(
                    text = if (presetCount == 2 && selectedCategory == RakeCategory.VANDE_BHARAT) {
                      "2 Driving Cabs"
                    } else {
                      "$presetCount-Car"
                    },
                    maxLines = 1
                  )
                },
                modifier = Modifier.heightIn(min = 48.dp)
              )
            }
          }

          // Rake Name (auto-derived from DTCs / DMCs / MCs or custom)
          OutlinedTextField(
            value = rakeName,
            onValueChange = {
              rakeName = it.take(80)
              autoTrackName = it.isBlank()
              errorMessage = null
            },
            label = { Text("Rake Name (Auto: DMCs / DTCs / MCs)", maxLines = 1) },
            placeholder = { Text("e.g. 188396-188397 or 109049B-61B(4B)-52B") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("rake_editor_name_input")
          )

          // Make Selector + Custom Input
          Text(
            text = "Coach Make (Optional)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            listOf("" to "None", "ICF" to "ICF", "RCF" to "RCF", "MCF" to "MCF").forEach { (value, label) ->
              FilterChip(
                selected = make.equals(value, ignoreCase = true),
                onClick = { make = value },
                label = { Text(label, maxLines = 1) },
                modifier = Modifier.heightIn(min = 48.dp)
              )
            }
          }

          // TCAS Toggle
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = GlassSurfaceSoft,
            border = BorderStroke(
              1.dp,
              if (tcasFitted) SignalEmerald.copy(alpha = 0.45f)
              else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "TCAS / Kavach Fitted",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold
                )
                Text(
                  text = "Mark if Train Collision Avoidance System is commissioned",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Switch(
                checked = tcasFitted,
                onCheckedChange = { tcasFitted = it },
                modifier = Modifier.testTag("rake_editor_tcas_switch")
              )
            }
          }

          // Rake Direction Fields (Start <-> Finish: KZJ / SC)
          Text(
            text = "Rake Direction",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            DirectionDropdownField(
              value = startEnd,
              label = "Start",
              onSelect = { chosen ->
                startEnd = chosen
                if (chosen == "KZJ" && (finishEnd.isBlank() || finishEnd == "KZJ")) {
                  finishEnd = "SC"
                } else if (chosen == "SC" && (finishEnd.isBlank() || finishEnd == "SC")) {
                  finishEnd = "KZJ"
                }
              },
              testTag = "rake_editor_start_end_input",
              modifier = Modifier.weight(1f)
            )
            IconButton(
              onClick = {
                val tmp = startEnd
                startEnd = finishEnd
                finishEnd = tmp
              },
              modifier = Modifier
                .size(48.dp)
                .testTag("rake_editor_swap_ends_btn")
            ) {
              Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = stringResource(R.string.rake_editor_swap_ends_cd),
                tint = RailwayCobalt
              )
            }
            DirectionDropdownField(
              value = finishEnd,
              label = "Finish",
              onSelect = { chosen ->
                finishEnd = chosen
                if (chosen == "KZJ" && (startEnd.isBlank() || startEnd == "KZJ")) {
                  startEnd = "SC"
                } else if (chosen == "SC" && (startEnd.isBlank() || startEnd == "SC")) {
                  startEnd = "KZJ"
                }
              },
              testTag = "rake_editor_finish_end_input",
              modifier = Modifier.weight(1f)
            )
          }

          // Remarks
          OutlinedTextField(
            value = remarks,
            onValueChange = { remarks = it.take(300) },
            label = { Text("Remarks (swapped coach, POH note, BT Rake, etc.)") },
            maxLines = 3,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("rake_editor_remarks_input")
          )

          HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

          // Coaches Section Header
          FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Column {
              Text(
                text = "Coach Composition (${coaches.size}/${RakeFormationEngine.MAX_COACHES})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "Enter coach numbers in order; use arrows to swap positions",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedButton(
                onClick = {
                  updateCoachesAndSyncName(RakeFormationEngine.reindexCoaches(coaches.reversed()))
                  val tmp = startEnd
                  startEnd = finishEnd
                  finishEnd = tmp
                },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier
                  .heightIn(min = 48.dp)
                  .testTag("rake_editor_reverse_coaches_btn")
              ) {
                Icon(
                  imageVector = Icons.Default.SwapHoriz,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reverse", maxLines = 1)
              }
              OutlinedButton(
                onClick = {
                  if (coaches.size < RakeFormationEngine.MAX_COACHES) {
                    val defaultRole = when (selectedCategory) {
                      RakeCategory.VANDE_BHARAT -> CoachRole.CC
                      else -> CoachRole.TC
                    }
                    updateCoachesAndSyncName(
                      RakeFormationEngine.reindexCoaches(
                        coaches + RakeCoach(
                          position = (coaches.size + 1).toLong(),
                          coachNumber = "",
                          coachType = defaultRole.name
                        )
                      )
                    )
                    errorMessage = null
                  }
                },
                enabled = coaches.size < RakeFormationEngine.MAX_COACHES,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier
                  .heightIn(min = 48.dp)
                  .testTag("rake_editor_add_coach_btn")
              ) {
                Icon(
                  imageVector = Icons.Default.Add,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.rake_editor_add_coach), maxLines = 1)
              }
            }
          }

          // Coach Rows
          coaches.forEachIndexed { index, coach ->
            CoachEditorRow(
              index = index,
              totalCount = coaches.size,
              coach = coach,
              onCoachChange = { updated ->
                updateCoachesAndSyncName(
                  coaches.toMutableList().also { list -> list[index] = updated }
                )
                errorMessage = null
              },
              onMoveUp = {
                if (index > 0) {
                  updateCoachesAndSyncName(RakeFormationEngine.swapCoaches(coaches, index, index - 1))
                }
              },
              onMoveDown = {
                if (index < coaches.lastIndex) {
                  updateCoachesAndSyncName(RakeFormationEngine.swapCoaches(coaches, index, index + 1))
                }
              },
              onRemove = {
                if (coaches.size > 1) {
                  updateCoachesAndSyncName(
                    RakeFormationEngine.reindexCoaches(
                      coaches.filterIndexed { i, _ -> i != index }
                    )
                  )
                }
              }
            )
          }

          Spacer(modifier = Modifier.height(4.dp))
        }

        HorizontalDivider()

        // Footer Buttons
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically
        ) {
          TextButton(
            onClick = onDismiss,
            enabled = !isBusy,
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("rake_editor_cancel_btn")
          ) {
            Text("Cancel")
          }
          Spacer(modifier = Modifier.width(8.dp))
          Button(
            onClick = {
              if (coaches.isEmpty()) {
                errorMessage = "Add at least one coach to the formation."
                return@Button
              }
              val blankIndex = coaches.indexOfFirst { it.coachNumber.trim().isBlank() }
              if (blankIndex >= 0) {
                errorMessage = "Coach #${blankIndex + 1} is missing a coach number."
                return@Button
              }
              val cleanName = rakeName.trim().ifBlank {
                RakeFormationEngine.deriveRakeName(selectedCategory, coaches)
              }.take(80)
              if (cleanName.isBlank()) {
                errorMessage = "Please enter a rake name."
                return@Button
              }
              val saved = RakeFormation(
                id = initialRake?.id.orEmpty(),
                userId = initialRake?.userId.orEmpty(),
                rakeName = cleanName,
                category = selectedCategory.name,
                make = make.trim(),
                tcasFitted = tcasFitted,
                startEnd = startEnd.trim(),
                finishEnd = finishEnd.trim(),
                remarks = remarks.trim(),
                sortOrder = initialRake?.sortOrder ?: 0L,
                coaches = RakeFormationEngine.reindexCoaches(coaches)
              )
              onSave(saved, isEdit)
            },
            enabled = !isBusy,
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("rake_editor_save_btn")
          ) {
            Text(stringResource(R.string.rake_editor_save_button), fontWeight = FontWeight.Bold)
          }
        }
      }
      }
    }
  }
}

@Composable
private fun CoachEditorRow(
  index: Int,
  totalCount: Int,
  coach: RakeCoach,
  onCoachChange: (RakeCoach) -> Unit,
  onMoveUp: () -> Unit,
  onMoveDown: () -> Unit,
  onRemove: () -> Unit
) {
  var showRoleMenu by remember { mutableStateOf(false) }
  val role = coach.coachRoleEnum
  val accentColor = when {
    role.isDrivingCab -> RailwayCobalt
    role.isMotorized -> SafetyAmber
    role == CoachRole.EC || role == CoachRole.CC -> DepotTeal
    else -> MaterialTheme.colorScheme.outline
  }

  Surface(
    shape = RoundedCornerShape(14.dp),
    color = GlassSurfaceSoft,
    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("rake_editor_coach_row_$index")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = accentColor.copy(alpha = 0.16f)
          ) {
            Text(
              text = "#${index + 1}",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = accentColor,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
          }
          Box {
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = MaterialTheme.colorScheme.surface,
              border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
              modifier = Modifier
                .heightIn(min = 40.dp)
                .clickable { showRoleMenu = true }
                .testTag("rake_editor_coach_role_$index")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Text(
                  text = role.code,
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold,
                  color = accentColor
                )
                Icon(
                  imageVector = Icons.Default.KeyboardArrowDown,
                  contentDescription = null,
                  tint = accentColor,
                  modifier = Modifier.size(16.dp)
                )
              }
            }
            DropdownMenu(
              expanded = showRoleMenu,
              onDismissRequest = { showRoleMenu = false },
              containerColor = GlassPopupSurface
            ) {
              CoachRole.entries.forEach { option ->
                DropdownMenuItem(
                  text = { Text("${option.code} — ${option.label}") },
                  onClick = {
                    showRoleMenu = false
                    onCoachChange(coach.copy(coachType = option.name))
                  }
                )
              }
            }
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = onMoveUp,
            enabled = index > 0,
            modifier = Modifier.size(44.dp)
          ) {
            Icon(
              imageVector = Icons.Default.KeyboardArrowUp,
              contentDescription = stringResource(R.string.rake_editor_move_up_cd),
              modifier = Modifier.size(20.dp)
            )
          }
          IconButton(
            onClick = onMoveDown,
            enabled = index < totalCount - 1,
            modifier = Modifier.size(44.dp)
          ) {
            Icon(
              imageVector = Icons.Default.KeyboardArrowDown,
              contentDescription = stringResource(R.string.rake_editor_move_down_cd),
              modifier = Modifier.size(20.dp)
            )
          }
          IconButton(
            onClick = onRemove,
            enabled = totalCount > 1,
            modifier = Modifier.size(44.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Delete,
              contentDescription = stringResource(R.string.rake_editor_remove_coach_cd),
              tint = if (totalCount > 1) SignalCrimson else MaterialTheme.colorScheme.outlineVariant,
              modifier = Modifier.size(18.dp)
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedTextField(
          value = coach.coachNumber,
          onValueChange = { onCoachChange(coach.copy(coachNumber = it.take(24))) },
          label = { Text("Coach No.*", maxLines = 1) },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .weight(1.15f)
            .testTag("rake_editor_coach_num_$index")
        )
        DirectionDropdownField(
          value = coach.endTag,
          label = "Direction",
          onSelect = { onCoachChange(coach.copy(endTag = it)) },
          testTag = "rake_editor_coach_end_$index",
          cornerRadius = 12,
          modifier = Modifier.weight(1f)
        )
      }
      OutlinedTextField(
        value = coach.altNumber,
        onValueChange = { onCoachChange(coach.copy(altNumber = it.take(24))) },
        label = { Text("Alt / Old No. (Optional)", maxLines = 1) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("rake_editor_coach_alt_$index")
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectionDropdownField(
  value: String,
  label: String,
  onSelect: (String) -> Unit,
  testTag: String,
  modifier: Modifier = Modifier,
  cornerRadius: Int = 14
) {
  var expanded by remember { mutableStateOf(false) }

  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = it },
    modifier = modifier
  ) {
    OutlinedTextField(
      value = value,
      onValueChange = {},
      readOnly = true,
      label = { Text(label, maxLines = 1) },
      placeholder = { Text("Select", maxLines = 1) },
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      singleLine = true,
      shape = RoundedCornerShape(cornerRadius.dp),
      modifier = Modifier
        .fillMaxWidth()
        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        .testTag(testTag)
    )
    ExposedDropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
      containerColor = GlassPopupSurface
    ) {
      DIRECTION_OPTIONS.forEach { option ->
        DropdownMenuItem(
          text = { Text(option, fontWeight = FontWeight.SemiBold) },
          onClick = {
            expanded = false
            onSelect(option)
          },
          modifier = Modifier.testTag("${testTag}_opt_${option.lowercase()}")
        )
      }
      DropdownMenuItem(
        text = {
          Text(
            text = "None",
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        },
        onClick = {
          expanded = false
          onSelect("")
        },
        modifier = Modifier.testTag("${testTag}_opt_none")
      )
    }
  }
}
