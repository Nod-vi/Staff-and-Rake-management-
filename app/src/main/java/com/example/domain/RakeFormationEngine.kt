package com.example.domain

import com.example.model.CoachRole
import com.example.model.RakeCategory
import com.example.model.RakeCoach
import com.example.model.RakeFormation

/**
 * Pure domain logic for Rake Manager: standard formation templates,
 * coach search/highlighting, reordering/swapping, and direction reversal.
 */
object RakeFormationEngine {

  const val MAX_COACHES = 24
  const val CURRENT_RAKES_SCHEMA_VERSION = 3L

  private val MEMU_BASIC_UNIT = listOf(CoachRole.DMC, CoachRole.TC1, CoachRole.TC2, CoachRole.TC3)
  private val MEMU_BASIC_UNIT_REVERSED = MEMU_BASIC_UNIT.reversed()

  private val VB_BASIC_UNIT = listOf(CoachRole.DTC, CoachRole.MC1, CoachRole.TC, CoachRole.MC2)
  private val VB_BASIC_UNIT_REVERSED = VB_BASIC_UNIT.reversed()

  private val LEGACY_GENERIC_RAKE_NAME_REGEX = Regex(
    "^(?:3-Ph EMU Rake \\d+|EMU Conv Rake \\d+(?: \\(BT Rake\\))?|3-Ph MEMU Rake \\d+|BHEL MEMU Rake \\d+(?: \\(8-Car\\))?|3-Ph MEMU Spare Unit)$"
  )
  private val LEGACY_VB_NAME_REGEX = Regex("^Vande Bharat (R-\\d+)$")

  fun formatCoachTokenForName(coach: RakeCoach): String {
    val num = coach.coachNumber.trim()
    if (num.isBlank()) return ""
    val alt = coach.altNumber.trim()
    return if (alt.isNotBlank()) "$num($alt)" else num
  }

  /**
   * Derives the standard operational rake name from driving/motor coach numbers:
   * - 3-Phase EMU & Vande Bharat: DTC-DTC (e.g. 188396-188397)
   * - Conventional EMU: MC-MC-MC (e.g. 109049B-61B(4B)-52B)
   * - MEMU & Spare Unit: DMC-DMC-DMC (e.g. 198870-198866-198869)
   */
  fun deriveRakeName(category: RakeCategory, coaches: List<RakeCoach>): String {
    val sorted = coaches.sortedBy { it.position }
    val primaryCoaches = when (category) {
      RakeCategory.EMU_3PHASE_12CAR, RakeCategory.VANDE_BHARAT ->
        sorted.filter { it.coachRoleEnum == CoachRole.DTC }
      RakeCategory.EMU_CONV_9CAR ->
        sorted.filter { it.coachRoleEnum == CoachRole.MC }
      RakeCategory.MEMU_3PHASE_12CAR, RakeCategory.MEMU_BHEL, RakeCategory.SPARE_UNIT ->
        sorted.filter { it.coachRoleEnum == CoachRole.DMC }
    }
    val primaryTokens = primaryCoaches.map(::formatCoachTokenForName).filter { it.isNotBlank() }
    if (primaryTokens.isNotEmpty()) return primaryTokens.joinToString("-")

    val fallbackTokens = sorted
      .filter { it.coachRoleEnum.isDrivingCab || it.coachRoleEnum.isMotorized }
      .map(::formatCoachTokenForName)
      .filter { it.isNotBlank() }
    if (fallbackTokens.isNotEmpty()) return fallbackTokens.joinToString("-")

    val first = sorted.firstOrNull()?.let(::formatCoachTokenForName).orEmpty()
    val last = sorted.lastOrNull()?.let(::formatCoachTokenForName).orEmpty()
    return listOf(first, last).filter { it.isNotBlank() }.distinct().joinToString("-")
  }

  fun buildTemplateCoaches(category: RakeCategory, carCount: Int): List<RakeCoach> {
    val count = carCount.coerceIn(1, MAX_COACHES)
    val roles: List<CoachRole> = when (category) {
      RakeCategory.EMU_3PHASE_12CAR -> when (count) {
        12 -> listOf(
          CoachRole.DTC, CoachRole.MC, CoachRole.TC, CoachRole.NDTC,
          CoachRole.MC, CoachRole.TC, CoachRole.NDTC, CoachRole.MC,
          CoachRole.TC, CoachRole.TC, CoachRole.MC, CoachRole.DTC
        )
        9 -> listOf(
          CoachRole.DTC, CoachRole.MC, CoachRole.TC,
          CoachRole.NDTC, CoachRole.MC, CoachRole.TC,
          CoachRole.TC, CoachRole.MC, CoachRole.DTC
        )
        else -> genericDrivingFormation(count, CoachRole.DTC, CoachRole.MC, CoachRole.TC)
      }

      RakeCategory.EMU_CONV_9CAR -> when (count) {
        9 -> listOf(
          CoachRole.MC, CoachRole.TC, CoachRole.TC,
          CoachRole.MC, CoachRole.TC, CoachRole.TC,
          CoachRole.TC, CoachRole.TC, CoachRole.MC
        )
        12 -> listOf(
          CoachRole.MC, CoachRole.TC, CoachRole.TC,
          CoachRole.MC, CoachRole.TC, CoachRole.TC,
          CoachRole.MC, CoachRole.TC, CoachRole.TC,
          CoachRole.TC, CoachRole.TC, CoachRole.MC
        )
        6 -> listOf(
          CoachRole.MC, CoachRole.TC, CoachRole.TC,
          CoachRole.TC, CoachRole.TC, CoachRole.MC
        )
        else -> genericDrivingFormation(count, CoachRole.MC, CoachRole.TC, CoachRole.TC)
      }

      RakeCategory.MEMU_3PHASE_12CAR, RakeCategory.MEMU_BHEL -> when (count) {
        12 -> MEMU_BASIC_UNIT + MEMU_BASIC_UNIT + MEMU_BASIC_UNIT_REVERSED
        8 -> MEMU_BASIC_UNIT + MEMU_BASIC_UNIT_REVERSED
        4 -> MEMU_BASIC_UNIT
        else -> List(count) { idx ->
          if (count > 1 && idx == count - 1) CoachRole.DMC else MEMU_BASIC_UNIT[idx % MEMU_BASIC_UNIT.size]
        }
      }

      RakeCategory.VANDE_BHARAT -> when (count) {
        2 -> listOf(CoachRole.DTC, CoachRole.DTC)
        16 -> VB_BASIC_UNIT + VB_BASIC_UNIT + VB_BASIC_UNIT_REVERSED + VB_BASIC_UNIT_REVERSED
        20 -> VB_BASIC_UNIT + VB_BASIC_UNIT + VB_BASIC_UNIT + VB_BASIC_UNIT_REVERSED + VB_BASIC_UNIT_REVERSED
        else -> List(count) { idx ->
          if (count > 1 && idx == count - 1) CoachRole.DTC else VB_BASIC_UNIT[idx % VB_BASIC_UNIT.size]
        }
      }

      RakeCategory.SPARE_UNIT -> when (count) {
        4 -> MEMU_BASIC_UNIT
        3 -> listOf(CoachRole.MC, CoachRole.TC, CoachRole.TC)
        else -> List(count) { idx -> MEMU_BASIC_UNIT[idx % MEMU_BASIC_UNIT.size] }
      }
    }

    return roles.mapIndexed { idx, role ->
      RakeCoach(
        position = (idx + 1).toLong(),
        coachNumber = "",
        coachType = role.name
      )
    }
  }

  private fun genericDrivingFormation(
    count: Int,
    endRole: CoachRole,
    midRole: CoachRole,
    trailerRole: CoachRole
  ): List<CoachRole> = List(count) { idx ->
    when {
      idx == 0 || (count > 1 && idx == count - 1) -> endRole
      idx % 3 == 1 -> midRole
      else -> trailerRole
    }
  }

  /**
   * Upgrades legacy default coach roles (v1 -> v2) and generic rake names (v2 -> v3)
   * in-place while preserving user coach numbers, swaps, direction tags, and remarks:
   * - Conv EMU 9-car: B -> MC, C -> TC, D -> TC
   * - MEMU & Spare Unit: DMC, TC1, TC2, TC3
   * - Legacy rake names ("3-Ph EMU Rake 1", etc.) -> DTC/MC/DMC numbers
   */
  fun migrateLegacyCoachRolesIfNeeded(rake: RakeFormation): RakeFormation? {
    val sorted = rake.sortedCoaches
    if (sorted.isEmpty()) return null
    var changed = false

    val updatedCoaches = when (rake.categoryEnum) {
      RakeCategory.EMU_CONV_9CAR -> {
        val looksLegacy = sorted.any { it.coachRoleEnum == CoachRole.DTC }
        if (!looksLegacy) sorted else {
          changed = true
          sorted.map { coach ->
            val numUp = coach.coachNumber.trim().uppercase()
            val altUp = coach.altNumber.trim().uppercase()
            val mappedRole = when {
              numUp.endsWith("B") || altUp.endsWith("B") -> CoachRole.MC
              numUp.endsWith("C") || altUp.endsWith("C") ||
                numUp.endsWith("D") || altUp.endsWith("D") -> CoachRole.TC
              coach.coachRoleEnum == CoachRole.DTC -> CoachRole.MC
              coach.coachRoleEnum == CoachRole.MC -> CoachRole.TC
              else -> coach.coachRoleEnum
            }
            coach.copy(coachType = mappedRole.name)
          }
        }
      }

      RakeCategory.MEMU_3PHASE_12CAR, RakeCategory.MEMU_BHEL, RakeCategory.SPARE_UNIT -> {
        val hasNumberedTc = sorted.any {
          it.coachRoleEnum == CoachRole.TC1 || it.coachRoleEnum == CoachRole.TC2 || it.coachRoleEnum == CoachRole.TC3
        }
        if (hasNumberedTc || sorted.size !in setOf(4, 8, 12)) {
          sorted
        } else {
          val expectedRoles = buildTemplateCoaches(rake.categoryEnum, sorted.size).map { it.coachRoleEnum }
          val isReversedOrder = sorted.size > 4 &&
            sorted.first().coachRoleEnum != CoachRole.DMC &&
            sorted.last().coachRoleEnum == CoachRole.DMC
          val targetRoles = if (isReversedOrder) expectedRoles.reversed() else expectedRoles
          changed = true
          sorted.mapIndexed { idx, coach ->
            val targetRole = targetRoles.getOrElse(idx) { coach.coachRoleEnum }
            if (coach.coachRoleEnum == CoachRole.TC && targetRole != CoachRole.TC) {
              coach.copy(coachType = targetRole.name)
            } else {
              coach
            }
          }
        }
      }

      RakeCategory.VANDE_BHARAT, RakeCategory.EMU_3PHASE_12CAR -> sorted
    }

    val currentName = rake.rakeName.trim()
    val derivedName = deriveRakeName(rake.categoryEnum, updatedCoaches)
    val vbMatch = LEGACY_VB_NAME_REGEX.matchEntire(currentName)
    val updatedName = when {
      derivedName.isBlank() -> currentName
      LEGACY_GENERIC_RAKE_NAME_REGEX.matches(currentName) -> derivedName
      vbMatch != null -> "$derivedName (${vbMatch.groupValues[1]})"
      else -> currentName
    }
    if (updatedName != currentName) {
      changed = true
    }

    val updatedRemarks = if (
      rake.categoryEnum == RakeCategory.VANDE_BHARAT &&
      rake.remarks == "Driving ends recorded — full formation to be added"
    ) {
      changed = true
      "Driving cabs recorded — tap Edit to select 16-Car or 20-Car formation"
    } else {
      rake.remarks
    }

    return if (changed) {
      rake.copy(rakeName = updatedName, coaches = updatedCoaches, remarks = updatedRemarks)
    } else {
      null
    }
  }

  fun matchesQuery(rake: RakeFormation, rawQuery: String): Boolean {
    val q = rawQuery.trim()
    if (q.isBlank()) return true
    if (rake.rakeName.contains(q, ignoreCase = true)) return true
    if (rake.categoryEnum.label.contains(q, ignoreCase = true)) return true
    if (rake.make.contains(q, ignoreCase = true)) return true
    if (rake.remarks.contains(q, ignoreCase = true)) return true
    if (rake.startEnd.contains(q, ignoreCase = true) || rake.finishEnd.contains(q, ignoreCase = true)) return true
    if (q.equals("TCAS", ignoreCase = true) && rake.tcasFitted) return true
    return rake.coaches.any { coach ->
      coach.coachNumber.contains(q, ignoreCase = true) ||
        coach.altNumber.contains(q, ignoreCase = true) ||
        coach.coachRoleEnum.code.contains(q, ignoreCase = true) ||
        coach.endTag.contains(q, ignoreCase = true)
    }
  }

  fun matchingCoachPositions(rake: RakeFormation, rawQuery: String): Set<Long> {
    val q = rawQuery.trim()
    if (q.isBlank()) return emptySet()
    return rake.coaches.filter { coach ->
      coach.coachNumber.contains(q, ignoreCase = true) ||
        coach.altNumber.contains(q, ignoreCase = true)
    }.map { it.position }.toSet()
  }

  fun reindexCoaches(coaches: List<RakeCoach>): List<RakeCoach> =
    coaches.take(MAX_COACHES).mapIndexed { index, coach ->
      coach.copy(position = (index + 1).toLong())
    }

  fun swapCoaches(coaches: List<RakeCoach>, fromIndex: Int, toIndex: Int): List<RakeCoach> {
    if (fromIndex !in coaches.indices || toIndex !in coaches.indices || fromIndex == toIndex) {
      return reindexCoaches(coaches)
    }
    val mutable = coaches.toMutableList()
    val tmp = mutable[fromIndex]
    mutable[fromIndex] = mutable[toIndex]
    mutable[toIndex] = tmp
    return reindexCoaches(mutable)
  }

  fun reverseFormation(rake: RakeFormation): RakeFormation {
    val oldSorted = rake.sortedCoaches
    val oldDerived = deriveRakeName(rake.categoryEnum, oldSorted)
    val reversedCoaches = reindexCoaches(oldSorted.reversed())
    val newDerived = deriveRakeName(rake.categoryEnum, reversedCoaches)
    val trimmedName = rake.rakeName.trim()
    val updatedName = when {
      newDerived.isBlank() -> trimmedName
      trimmedName == oldDerived -> newDerived
      oldDerived.isNotBlank() && trimmedName.startsWith("$oldDerived ") ->
        newDerived + trimmedName.removePrefix(oldDerived)
      else -> trimmedName
    }
    return rake.copy(
      rakeName = updatedName,
      startEnd = rake.finishEnd,
      finishEnd = rake.startEnd,
      coaches = reversedCoaches
    )
  }

  fun formatRakeShareText(rake: RakeFormation): String = buildString {
    append(rake.rakeName)
    append(" [").append(rake.categoryEnum.label)
    if (rake.make.isNotBlank()) append(" · ").append(rake.make)
    append(" · ").append(rake.carCount).append("-Car")
    if (rake.tcasFitted) append(" · TCAS")
    append("]")
    if (rake.directionLabel.isNotBlank()) {
      append("\nDirection: ").append(rake.directionLabel)
    }
    append("\nFormation: ")
    append(
      rake.sortedCoaches.joinToString(" - ") { coach ->
        val altPart = coach.altNumber.takeIf { it.isNotBlank() }?.let { "($it)" }.orEmpty()
        val endPart = coach.endTag.takeIf { it.isNotBlank() }?.let { "[$it]" }.orEmpty()
        "${coach.coachNumber}$altPart${if (endPart.isNotBlank()) " $endPart" else ""}(${coach.coachRoleEnum.code})"
      }
    )
    if (rake.remarks.isNotBlank()) {
      append("\nRemarks: ").append(rake.remarks)
    }
  }
}
