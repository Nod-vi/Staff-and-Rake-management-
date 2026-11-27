package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class RakeCategory(
  val label: String,
  val shortLabel: String,
  val defaultCarCount: Int,
  val presetCarCounts: List<Int>
) {
  EMU_3PHASE_12CAR(
    label = "3-Phase EMU",
    shortLabel = "3-Ph EMU",
    defaultCarCount = 12,
    presetCarCounts = listOf(12, 9)
  ),
  EMU_CONV_9CAR(
    label = "EMU Conv",
    shortLabel = "EMU Conv",
    defaultCarCount = 9,
    presetCarCounts = listOf(9, 12, 6)
  ),
  MEMU_3PHASE_12CAR(
    label = "3-Phase MEMU",
    shortLabel = "3-Ph MEMU",
    defaultCarCount = 12,
    presetCarCounts = listOf(12, 8)
  ),
  MEMU_BHEL(
    label = "BHEL MEMU",
    shortLabel = "BHEL MEMU",
    defaultCarCount = 12,
    presetCarCounts = listOf(12, 8)
  ),
  VANDE_BHARAT(
    label = "Vande Bharat",
    shortLabel = "Vande Bharat",
    defaultCarCount = 16,
    presetCarCounts = listOf(16, 20, 2)
  ),
  SPARE_UNIT(
    label = "Spare Unit",
    shortLabel = "Spare Unit",
    defaultCarCount = 4,
    presetCarCounts = listOf(4, 3)
  );

  companion object {
    fun fromRaw(value: String?): RakeCategory =
      entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: EMU_3PHASE_12CAR
  }
}

enum class CoachRole(val code: String, val label: String) {
  DTC("DTC", "Driving Trailer Coach"),
  DMC("DMC", "Driving Motor Coach"),
  MC("MC", "Motor Coach"),
  MC1("MC1", "Motor Coach 1"),
  MC2("MC2", "Motor Coach 2"),
  TC("TC", "Trailer Coach"),
  TC1("TC1", "Trailer Coach 1"),
  TC2("TC2", "Trailer Coach 2"),
  TC3("TC3", "Trailer Coach 3"),
  NDTC("NDTC", "Non-Driving Trailer Coach"),
  EC("EC", "Executive Chair Car"),
  CC("CC", "AC Chair Car");

  val isDrivingCab: Boolean get() = this == DTC || this == DMC
  val isMotorized: Boolean get() = this == DMC || this == MC || this == MC1 || this == MC2

  companion object {
    fun fromRaw(value: String?): CoachRole =
      entries.firstOrNull {
        it.name.equals(value, ignoreCase = true) || it.code.equals(value, ignoreCase = true)
      } ?: TC
  }
}

@Keep
@IgnoreExtraProperties
data class RakeCoach(
  val position: Long = 1L,
  val coachNumber: String = "",
  val coachType: String = CoachRole.TC.name,
  val altNumber: String = "",
  val endTag: String = ""
) {
  @get:Exclude
  val coachRoleEnum: CoachRole get() = CoachRole.fromRaw(coachType)

  fun toFirestoreMap(): Map<String, Any> = mapOf(
    "position" to position.coerceIn(1L, 24L),
    "coachNumber" to coachNumber.trim().take(24),
    "coachType" to coachRoleEnum.name,
    "altNumber" to altNumber.trim().take(24),
    "endTag" to endTag.trim().take(16)
  )
}

@Keep
@IgnoreExtraProperties
data class RakeFormation(
  @DocumentId val id: String = "",
  val userId: String = "",
  val rakeName: String = "",
  val category: String = RakeCategory.EMU_3PHASE_12CAR.name,
  val make: String = "",
  val tcasFitted: Boolean = false,
  val startEnd: String = "",
  val finishEnd: String = "",
  val remarks: String = "",
  val sortOrder: Long = 0L,
  val coaches: List<RakeCoach> = emptyList(),
  val updatedByUid: String = "",
  val updatedByName: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
) {
  @get:Exclude
  val categoryEnum: RakeCategory get() = RakeCategory.fromRaw(category)

  @get:Exclude
  val carCount: Int get() = coaches.size

  @get:Exclude
  val sortedCoaches: List<RakeCoach> get() = coaches.sortedBy { it.position }

  @get:Exclude
  val directionLabel: String
    get() {
      val s = startEnd.trim()
      val f = finishEnd.trim()
      return when {
        s.isNotBlank() && f.isNotBlank() -> "$s ⇄ $f"
        s.isNotBlank() -> "$s End"
        f.isNotBlank() -> "$f End"
        else -> ""
      }
    }
}
