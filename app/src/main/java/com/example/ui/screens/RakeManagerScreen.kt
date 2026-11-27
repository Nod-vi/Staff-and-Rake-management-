package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.RakeFormationEngine
import com.example.model.CoachRole
import com.example.model.RakeCategory
import com.example.model.RakeCoach
import com.example.model.RakeFormation
import com.example.model.UserRole
import com.example.ui.theme.DepotTeal
import com.example.ui.theme.DepotTealContainer
import com.example.ui.theme.DepotTealOnContainer
import com.example.ui.theme.GlassIconOrb
import com.example.ui.theme.GlassPillBadge
import com.example.ui.theme.GlassPopupSurface
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RakeManagerScreen(
  rakes: List<RakeFormation>,
  userRole: UserRole,
  roleAccess: com.example.model.ResolvedRoleAccess = com.example.model.RoleAccessPolicy.resolve(userRole, emptyMap()),
  isBusy: Boolean,
  onSaveRake: (RakeFormation, Boolean) -> Unit,
  onReverseRake: (RakeFormation) -> Unit,
  onDeleteRake: (RakeFormation) -> Unit,
  onNotifyMessage: (String) -> Unit
) {
  val canEdit = roleAccess.canEditRakes
  val canDelete = roleAccess.canDeleteRakes
  val clipboardManager = LocalClipboardManager.current

  var searchQuery by rememberSaveable { mutableStateOf("") }
  var selectedCategoryName by rememberSaveable { mutableStateOf<String?>(null) }
  var showCreateDialog by remember { mutableStateOf(false) }
  var editingRake by remember { mutableStateOf<RakeFormation?>(null) }
  var deletingRake by remember { mutableStateOf<RakeFormation?>(null) }

  val selectedCategory = remember(selectedCategoryName) {
    selectedCategoryName?.let { raw -> RakeCategory.entries.firstOrNull { it.name == raw } }
  }

  val countByCategory = remember(rakes) {
    RakeCategory.entries.associateWith { cat -> rakes.count { it.categoryEnum == cat } }
  }
  val totalCoaches = remember(rakes) { rakes.sumOf { it.carCount } }
  val tcasCount = remember(rakes) { rakes.count { it.tcasFitted } }

  val filteredRakes = remember(rakes, selectedCategory, searchQuery) {
    rakes.filter { rake ->
      (selectedCategory == null || rake.categoryEnum == selectedCategory) &&
        RakeFormationEngine.matchesQuery(rake, searchQuery)
    }
  }

  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 760.dp)
        .testTag("rake_manager_list"),
      contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // Hero Fleet Summary Card
      item(key = "rake_hero_header") {
        GlassTile(
          shape = RoundedCornerShape(24.dp),
          topColor = GlassSurfaceBright,
          bottomColor = GlassSurfaceMid,
          tintColor = RailwayCobalt.copy(alpha = 0.08f),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("rake_manager_hero_card")
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              GlassIconOrb(
                shape = RoundedCornerShape(15.dp),
                tintColor = RailwayCobalt.copy(alpha = 0.16f),
                modifier = Modifier.size(48.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Train,
                  contentDescription = null,
                  tint = RailwayCobalt
                )
              }
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = stringResource(R.string.rake_manager_title),
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold,
                  maxLines = 2
                )
                Text(
                  text = stringResource(R.string.rake_manager_subtitle),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 2
                )
              }
            }

            FlowRow(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              GlassPillBadge(
                text = "${rakes.size} RAKES",
                tintColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                borderColor = RailwayCobalt
              )
              GlassPillBadge(
                text = "$totalCoaches COACHES",
                tintColor = DepotTealContainer.copy(alpha = 0.78f),
                textColor = DepotTealOnContainer,
                borderColor = DepotTeal
              )
              GlassPillBadge(
                text = "$tcasCount TCAS FITTED",
                tintColor = SignalEmeraldContainer.copy(alpha = 0.85f),
                textColor = SignalEmeraldOnContainer,
                borderColor = SignalEmerald
              )
              if (!canEdit) {
                GlassPillBadge(
                  text = stringResource(R.string.rake_read_only_badge).uppercase(),
                  tintColor = SafetyAmberContainer.copy(alpha = 0.85f),
                  textColor = SafetyAmberOnContainer,
                  borderColor = SafetyAmber
                )
              } else if (!canDelete) {
                GlassPillBadge(
                  text = "EDIT ACCESS",
                  tintColor = DepotTealContainer.copy(alpha = 0.85f),
                  textColor = DepotTealOnContainer,
                  borderColor = DepotTeal
                )
              }
            }

            if (canEdit) {
              Button(
                onClick = { showCreateDialog = true },
                enabled = !isBusy,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(min = 48.dp)
                  .testTag("rake_add_button")
              ) {
                Icon(
                  imageVector = Icons.Default.Add,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = stringResource(R.string.rake_add_button),
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }
        }
      }

      // Search & Category Filter Card
      item(key = "rake_filters") {
        GlassTile(
          shape = RoundedCornerShape(22.dp),
          topColor = GlassSurfaceBright,
          bottomColor = GlassSurfaceMid,
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            OutlinedTextField(
              value = searchQuery,
              onValueChange = { searchQuery = it },
              placeholder = {
                Text(
                  text = stringResource(R.string.rake_search_placeholder),
                  maxLines = 2
                )
              },
              leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = null)
              },
              trailingIcon = {
                if (searchQuery.isNotBlank()) {
                  IconButton(
                    onClick = { searchQuery = "" },
                    modifier = Modifier.size(48.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Default.Clear,
                      contentDescription = stringResource(R.string.rake_clear_search_cd)
                    )
                  }
                }
              },
              singleLine = true,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("rake_search_input")
            )

            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              FilterChip(
                selected = selectedCategory == null,
                onClick = { selectedCategoryName = null },
                label = { Text("All (${rakes.size})", maxLines = 1) },
                modifier = Modifier
                  .heightIn(min = 48.dp)
                  .testTag("rake_filter_all")
              )
              RakeCategory.entries.forEach { cat ->
                val count = countByCategory[cat] ?: 0
                FilterChip(
                  selected = selectedCategory == cat,
                  onClick = {
                    selectedCategoryName = if (selectedCategory == cat) null else cat.name
                  },
                  label = { Text("${cat.shortLabel} ($count)", maxLines = 1) },
                  modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("rake_filter_${cat.name.lowercase()}")
                )
              }
            }
          }
        }
      }

      if (filteredRakes.isEmpty()) {
        item(key = "rake_empty_state") {
          GlassTile(
            shape = RoundedCornerShape(22.dp),
            topColor = GlassSurfaceBright,
            bottomColor = GlassSurfaceMid,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("rake_empty_state")
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Train,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp)
              )
              Text(
                text = stringResource(R.string.rake_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
              )
              Text(
                text = stringResource(R.string.rake_empty_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
              )
            }
          }
        }
      } else {
        items(filteredRakes, key = { it.id.ifBlank { it.rakeName } }) { rake ->
          val matchedPositions = remember(rake, searchQuery) {
            RakeFormationEngine.matchingCoachPositions(rake, searchQuery)
          }
          RakeFormationCard(
            rake = rake,
            matchedCoachPositions = matchedPositions,
            canEdit = canEdit,
            canDelete = canDelete,
            isBusy = isBusy,
            onCopy = {
              val summary = RakeFormationEngine.formatRakeShareText(rake)
              clipboardManager.setText(AnnotatedString(summary))
              onNotifyMessage("Copied ${rake.rakeName} formation to clipboard.")
            },
            onEdit = { editingRake = rake },
            onReverse = { onReverseRake(rake) },
            onDelete = { deletingRake = rake }
          )
        }
      }

      item(key = "rake_bottom_spacer") {
        Spacer(modifier = Modifier.height(24.dp))
      }
    }
  }

  if (showCreateDialog && canEdit) {
    RakeEditorDialog(
      initialRake = null,
      isBusy = isBusy,
      onSave = { created, isEdit ->
        onSaveRake(created, isEdit)
        showCreateDialog = false
      },
      onDismiss = { showCreateDialog = false }
    )
  }

  editingRake?.takeIf { canEdit }?.let { target ->
    RakeEditorDialog(
      initialRake = target,
      isBusy = isBusy,
      onSave = { updated, isEdit ->
        onSaveRake(updated, isEdit)
        editingRake = null
      },
      onDismiss = { editingRake = null }
    )
  }

  deletingRake?.takeIf { canDelete }?.let { target ->
    AlertDialog(
      onDismissRequest = { deletingRake = null },
      containerColor = GlassPopupSurface,
      title = {
        Text(
          text = stringResource(R.string.rake_delete_confirm_title),
          fontWeight = FontWeight.Bold
        )
      },
      text = {
        Text(
          text = stringResource(
            R.string.rake_delete_confirm_message,
            target.rakeName,
            target.carCount
          ),
          style = MaterialTheme.typography.bodyMedium
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onDeleteRake(target)
            deletingRake = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = SignalCrimson),
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("rake_confirm_delete_btn")
        ) {
          Text(stringResource(R.string.rake_delete_action), fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(
          onClick = { deletingRake = null },
          modifier = Modifier.heightIn(min = 48.dp)
        ) {
          Text("Cancel")
        }
      }
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RakeFormationCard(
  rake: RakeFormation,
  matchedCoachPositions: Set<Long>,
  canEdit: Boolean,
  canDelete: Boolean,
  isBusy: Boolean,
  onCopy: () -> Unit,
  onEdit: () -> Unit,
  onReverse: () -> Unit,
  onDelete: () -> Unit
) {
  val (catTint, catInk, catBorder) = categoryBadgeStyle(rake.categoryEnum)
  val coaches = remember(rake.coaches) { rake.sortedCoaches }

  GlassTile(
    shape = RoundedCornerShape(22.dp),
    topColor = GlassSurfaceBright,
    bottomColor = GlassSurfaceMid,
    tintColor = catBorder.copy(alpha = 0.05f),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("rake_card_${rake.id}")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      // Header Title + Metadata Pills
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = rake.rakeName,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          maxLines = 2
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          GlassPillBadge(
            text = rake.categoryEnum.label.uppercase(),
            tintColor = catTint,
            textColor = catInk,
            borderColor = catBorder
          )
          GlassPillBadge(
            text = if (rake.categoryEnum == RakeCategory.VANDE_BHARAT && rake.carCount == 2) {
              "2 CABS"
            } else {
              "${rake.carCount}-CAR"
            },
            tintColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
            textColor = MaterialTheme.colorScheme.onSurfaceVariant,
            borderColor = MaterialTheme.colorScheme.outline
          )
          if (rake.make.isNotBlank()) {
            GlassPillBadge(
              text = rake.make.uppercase(),
              tintColor = DepotTealContainer.copy(alpha = 0.78f),
              textColor = DepotTealOnContainer,
              borderColor = DepotTeal
            )
          }
          if (rake.tcasFitted) {
            GlassPillBadge(
              text = "TCAS ✓",
              tintColor = SignalEmeraldContainer.copy(alpha = 0.88f),
              textColor = SignalEmeraldOnContainer,
              borderColor = SignalEmerald
            )
          }
          if (rake.directionLabel.isNotBlank()) {
            GlassPillBadge(
              text = rake.directionLabel,
              tintColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
              textColor = MaterialTheme.colorScheme.onPrimaryContainer,
              borderColor = RailwayCobalt
            )
          }
        }
      }

      // Visual Coach Composition Grid (FlowRow guarantees zero portrait truncation)
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        coaches.forEach { coach ->
          CoachFormationChip(
            coach = coach,
            highlighted = matchedCoachPositions.contains(coach.position)
          )
        }
      }

      if (rake.remarks.isNotBlank()) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = SafetyAmberContainer.copy(alpha = 0.45f),
          border = BorderStroke(1.dp, SafetyAmber.copy(alpha = 0.35f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = Icons.Default.VerifiedUser,
              contentDescription = null,
              tint = SafetyAmberOnContainer,
              modifier = Modifier.size(15.dp)
            )
            Text(
              text = rake.remarks,
              style = MaterialTheme.typography.bodySmall,
              color = SafetyAmberOnContainer
            )
          }
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

      // Action Buttons (FlowRow avoids squished text in narrow portrait mode)
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        OutlinedButton(
          onClick = onCopy,
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("rake_copy_btn_${rake.id}")
        ) {
          Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.rake_copy_action), maxLines = 1)
        }

        if (canEdit) {
          OutlinedButton(
            onClick = onEdit,
            enabled = !isBusy,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("rake_edit_btn_${rake.id}")
          ) {
            Icon(
              imageVector = Icons.Default.Edit,
              contentDescription = null,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.rake_edit_action), maxLines = 1)
          }

          OutlinedButton(
            onClick = onReverse,
            enabled = !isBusy,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("rake_reverse_btn_${rake.id}")
          ) {
            Icon(
              imageVector = Icons.Default.SwapHoriz,
              contentDescription = null,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.rake_reverse_action), maxLines = 1)
          }
        }

        if (canDelete) {
          OutlinedButton(
            onClick = onDelete,
            enabled = !isBusy,
            border = BorderStroke(1.dp, SignalCrimson.copy(alpha = 0.55f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = SignalCrimson),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("rake_delete_btn_${rake.id}")
          ) {
            Icon(
              imageVector = Icons.Default.Delete,
              contentDescription = null,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.rake_delete_action), maxLines = 1)
          }
        }
      }
    }
  }
}

@Composable
private fun CoachFormationChip(
  coach: RakeCoach,
  highlighted: Boolean
) {
  val role = coach.coachRoleEnum
  val borderColor = when {
    highlighted -> SignalEmerald
    role.isDrivingCab -> RailwayCobalt
    role.isMotorized -> SafetyAmber
    role == CoachRole.EC || role == CoachRole.CC -> DepotTeal
    else -> MaterialTheme.colorScheme.outlineVariant
  }
  val bgColor = when {
    highlighted -> SignalEmeraldContainer.copy(alpha = 0.90f)
    role.isDrivingCab -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    role.isMotorized -> SafetyAmberContainer.copy(alpha = 0.48f)
    role == CoachRole.EC || role == CoachRole.CC -> DepotTealContainer.copy(alpha = 0.48f)
    else -> GlassSurfaceSoft
  }
  val headerColor = when {
    highlighted -> SignalEmeraldOnContainer
    role.isDrivingCab -> RailwayCobalt
    role.isMotorized -> SafetyAmberOnContainer
    role == CoachRole.EC || role == CoachRole.CC -> DepotTealOnContainer
    else -> MaterialTheme.colorScheme.onSurfaceVariant
  }

  Surface(
    shape = RoundedCornerShape(12.dp),
    color = bgColor,
    border = BorderStroke(if (highlighted || role.isDrivingCab) 1.5.dp else 1.dp, borderColor),
    modifier = Modifier.widthIn(min = 72.dp)
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "${coach.position}.${role.code}",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = headerColor
        )
        if (coach.endTag.isNotBlank()) {
          Text(
            text = coach.endTag,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = SignalCrimson
          )
        }
      }
      Text(
        text = coach.coachNumber,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
      )
      if (coach.altNumber.isNotBlank()) {
        Text(
          text = "(${coach.altNumber})",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

private fun categoryBadgeStyle(category: RakeCategory): Triple<Color, Color, Color> = when (category) {
  RakeCategory.EMU_3PHASE_12CAR -> Triple(
    RailwayCobalt.copy(alpha = 0.14f),
    RailwayCobalt,
    RailwayCobalt
  )
  RakeCategory.EMU_CONV_9CAR -> Triple(
    DepotTealContainer.copy(alpha = 0.80f),
    DepotTealOnContainer,
    DepotTeal
  )
  RakeCategory.MEMU_3PHASE_12CAR -> Triple(
    SignalEmeraldContainer.copy(alpha = 0.80f),
    SignalEmeraldOnContainer,
    SignalEmerald
  )
  RakeCategory.MEMU_BHEL -> Triple(
    SafetyAmberContainer.copy(alpha = 0.85f),
    SafetyAmberOnContainer,
    SafetyAmber
  )
  RakeCategory.VANDE_BHARAT -> Triple(
    SignalCrimsonContainer.copy(alpha = 0.78f),
    SignalCrimsonOnContainer,
    SignalCrimson
  )
  RakeCategory.SPARE_UNIT -> Triple(
    DepotTeal.copy(alpha = 0.14f),
    DepotTealOnContainer,
    DepotTeal
  )
}
