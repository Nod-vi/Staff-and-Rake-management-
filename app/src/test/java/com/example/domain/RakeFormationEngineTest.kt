package com.example.domain

import com.example.data.DefaultRakeFormations
import com.example.model.CoachRole
import com.example.model.RakeCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RakeFormationEngineTest {

  @Test
  fun defaultFormationsMatchConfirmedWhiteboardEntries() {
    val all = DefaultRakeFormations.all
    assertEquals(33, all.size)

    // Rake names use DTC / MC / DMC coach numbers
    val emu1 = all.first { it.id == "rake_emu3p_1" }
    assertEquals("188396-188397", emu1.rakeName)

    val conv6 = all.first { it.id == "rake_emuc_6" }
    assertEquals("109049B-61B(4B)-52B", conv6.rakeName)

    val memu1 = all.first { it.id == "rake_memu3p_1" }
    assertEquals("198870-198866-198869", memu1.rakeName)

    val bhel4 = all.first { it.id == "rake_bhel_4" }
    assertEquals("248625-248626", bhel4.rakeName)

    // 3-Phase EMU Rake 5: confirmed 115, 116 (not 11 5 / 11 6)
    val emu5 = all.first { it.id == "rake_emu3p_5" }
    assertEquals("198131-198132", emu5.rakeName)
    assertEquals(12, emu5.carCount)
    assertEquals("115", emu5.sortedCoaches[1].coachNumber)
    assertEquals("116", emu5.sortedCoaches[4].coachNumber)

    // EMU Conv Rake 1: confirmed 109026 (21B)
    val conv1 = all.first { it.id == "rake_emuc_1" }
    assertEquals("51B-109026(21B)-31B", conv1.rakeName)
    assertEquals(9, conv1.carCount)
    assertEquals("109026", conv1.sortedCoaches[5].coachNumber)
    assertEquals("21B", conv1.sortedCoaches[5].altNumber)

    // EMU Conv Rake 7: confirmed 038147B -> B coach is MC (MC, TC, TC basic unit)
    val conv7 = all.first { it.id == "rake_emuc_7" }
    assertEquals("62B-038147B-84B", conv7.rakeName)
    assertEquals("038147B", conv7.sortedCoaches[3].coachNumber)
    assertEquals(CoachRole.MC, conv7.sortedCoaches[3].coachRoleEnum)
    assertEquals(CoachRole.MC, conv7.sortedCoaches[0].coachRoleEnum)
    assertEquals(CoachRole.TC, conv7.sortedCoaches[1].coachRoleEnum)
    assertEquals(CoachRole.TC, conv7.sortedCoaches[2].coachRoleEnum)
    assertEquals(CoachRole.MC, conv7.sortedCoaches[8].coachRoleEnum)

    // 3-Phase MEMU Rake 2: confirmed col 12 = 198661 and basic unit DMC, TC1, TC2, TC3
    val memu2 = all.first { it.id == "rake_memu3p_2" }
    assertEquals("198662-198868-198661", memu2.rakeName)
    assertEquals(12, memu2.carCount)
    assertEquals("198661", memu2.sortedCoaches[11].coachNumber)
    assertEquals(
      listOf(CoachRole.DMC, CoachRole.TC1, CoachRole.TC2, CoachRole.TC3),
      memu2.sortedCoaches.take(4).map { it.coachRoleEnum }
    )
    assertEquals(
      listOf(CoachRole.TC3, CoachRole.TC2, CoachRole.TC1, CoachRole.DMC),
      memu2.sortedCoaches.takeLast(4).map { it.coachRoleEnum }
    )

    // 4-Car Spare Unit uses DMC, TC1, TC2, TC3
    val spare = all.first { it.id == "rake_spare_1" }
    assertEquals("198865", spare.rakeName)
    assertEquals(
      listOf(CoachRole.DMC, CoachRole.TC1, CoachRole.TC2, CoachRole.TC3),
      spare.sortedCoaches.map { it.coachRoleEnum }
    )

    // Vande Bharat included as a RakeCategory with 5 rakes
    val vbRakes = all.filter { it.categoryEnum == RakeCategory.VANDE_BHARAT }
    assertEquals(5, vbRakes.size)
    assertTrue(vbRakes.any { it.rakeName == "251124-251125 (R-83)" && it.coaches.first().coachNumber == "251124" })
  }

  @Test
  fun searchMatchesCoachNumbersAltNumbersMakeAndTcas() {
    val conv1 = DefaultRakeFormations.all.first { it.id == "rake_emuc_1" }
    assertTrue(RakeFormationEngine.matchesQuery(conv1, "21B"))
    assertEquals(setOf(6L), RakeFormationEngine.matchingCoachPositions(conv1, "21B"))
    assertFalse(RakeFormationEngine.matchesQuery(conv1, "TCAS"))

    val bhel2 = DefaultRakeFormations.all.first { it.id == "rake_bhel_2" }
    assertTrue(RakeFormationEngine.matchesQuery(bhel2, "ICF"))
    assertTrue(RakeFormationEngine.matchesQuery(bhel2, "TCAS"))
    assertTrue(RakeFormationEngine.matchesQuery(bhel2, "258069"))
    assertEquals(setOf(5L), RakeFormationEngine.matchingCoachPositions(bhel2, "258069"))
  }

  @Test
  fun templateGenerationReversalAndLegacyMigrationWorkCorrectly() {
    val conv9 = RakeFormationEngine.buildTemplateCoaches(RakeCategory.EMU_CONV_9CAR, 9)
    assertEquals(9, conv9.size)
    assertEquals(
      listOf(
        CoachRole.MC, CoachRole.TC, CoachRole.TC,
        CoachRole.MC, CoachRole.TC, CoachRole.TC,
        CoachRole.TC, CoachRole.TC, CoachRole.MC
      ),
      conv9.map { it.coachRoleEnum }
    )

    val memu8 = RakeFormationEngine.buildTemplateCoaches(RakeCategory.MEMU_BHEL, 8)
    assertEquals(
      listOf(
        CoachRole.DMC, CoachRole.TC1, CoachRole.TC2, CoachRole.TC3,
        CoachRole.TC3, CoachRole.TC2, CoachRole.TC1, CoachRole.DMC
      ),
      memu8.map { it.coachRoleEnum }
    )

    val vb16 = RakeFormationEngine.buildTemplateCoaches(RakeCategory.VANDE_BHARAT, 16)
    assertEquals(16, vb16.size)
    assertEquals(
      listOf(CoachRole.DTC, CoachRole.MC1, CoachRole.TC, CoachRole.MC2),
      vb16.take(4).map { it.coachRoleEnum }
    )
    assertEquals(CoachRole.DTC, vb16.last().coachRoleEnum)

    val vb20 = RakeFormationEngine.buildTemplateCoaches(RakeCategory.VANDE_BHARAT, 20)
    assertEquals(20, vb20.size)
    assertEquals(
      listOf(CoachRole.DTC, CoachRole.MC1, CoachRole.TC, CoachRole.MC2),
      vb20.take(4).map { it.coachRoleEnum }
    )
    assertEquals(CoachRole.DTC, vb20.last().coachRoleEnum)

    // Legacy v1/v2 -> v3 migration preserves coach numbers while fixing roles & legacy names
    val legacyConv = DefaultRakeFormations.all.first { it.id == "rake_emuc_6" }.let { rake ->
      rake.copy(
        rakeName = "EMU Conv Rake 6",
        coaches = rake.coaches.mapIndexed { idx, c ->
          val oldRole = when (idx) {
            0, 3, 8 -> CoachRole.DTC.name
            1, 4, 7 -> CoachRole.MC.name
            else -> CoachRole.TC.name
          }
          c.copy(coachType = oldRole)
        }
      )
    }
    val migratedConv = RakeFormationEngine.migrateLegacyCoachRolesIfNeeded(legacyConv)!!
    assertEquals("109049B-61B(4B)-52B", migratedConv.rakeName)
    assertEquals(CoachRole.MC, migratedConv.sortedCoaches[0].coachRoleEnum)
    assertEquals(CoachRole.TC, migratedConv.sortedCoaches[1].coachRoleEnum)

    val legacyVb = DefaultRakeFormations.all.first { it.id == "rake_vb_83" }.copy(rakeName = "Vande Bharat R-83")
    val migratedVb = RakeFormationEngine.migrateLegacyCoachRolesIfNeeded(legacyVb)!!
    assertEquals("251124-251125 (R-83)", migratedVb.rakeName)

    val memu1 = DefaultRakeFormations.all.first { it.id == "rake_memu3p_1" }
    val reversed = RakeFormationEngine.reverseFormation(memu1)
    assertEquals("198869-198866-198870", reversed.rakeName)
    assertEquals("SC", reversed.startEnd)
    assertEquals("KZJ", reversed.finishEnd)
    assertEquals("198869", reversed.sortedCoaches.first().coachNumber)
    assertEquals("198870", reversed.sortedCoaches.last().coachNumber)
    assertEquals(1L, reversed.sortedCoaches.first().position)
    assertEquals(12L, reversed.sortedCoaches.last().position)
  }

  @Test
  fun swapCoachesReindexesPositionsCleanly() {
    val emu3 = DefaultRakeFormations.all.first { it.id == "rake_emu3p_3" }
    val swapped = RakeFormationEngine.swapCoaches(emu3.sortedCoaches, 0, 1)
    assertEquals("400", swapped[0].coachNumber)
    assertEquals(1L, swapped[0].position)
    assertEquals("188408", swapped[1].coachNumber)
    assertEquals(2L, swapped[1].position)
  }
}
