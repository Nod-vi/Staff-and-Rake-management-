package com.example.data

import com.example.model.CoachRole
import com.example.model.RakeCategory
import com.example.model.RakeCoach
import com.example.model.RakeFormation

/**
 * Verified depot EMU, MEMU, Spare Unit, and Vande Bharat rake formations
 * extracted from the depot formation boards and confirmed by the user.
 */
object DefaultRakeFormations {

  private val EMU_3PHASE_12_ROLES = listOf(
    CoachRole.DTC,
    CoachRole.MC,
    CoachRole.TC,
    CoachRole.NDTC,
    CoachRole.MC,
    CoachRole.TC,
    CoachRole.NDTC,
    CoachRole.MC,
    CoachRole.TC,
    CoachRole.TC,
    CoachRole.MC,
    CoachRole.DTC
  )

  private val MEMU_12_ROLES = listOf(
    CoachRole.DMC,
    CoachRole.TC1,
    CoachRole.TC2,
    CoachRole.TC3,
    CoachRole.DMC,
    CoachRole.TC1,
    CoachRole.TC2,
    CoachRole.TC3,
    CoachRole.TC3,
    CoachRole.TC2,
    CoachRole.TC1,
    CoachRole.DMC
  )

  private val MEMU_8_ROLES = listOf(
    CoachRole.DMC,
    CoachRole.TC1,
    CoachRole.TC2,
    CoachRole.TC3,
    CoachRole.TC3,
    CoachRole.TC2,
    CoachRole.TC1,
    CoachRole.DMC
  )

  private fun buildCoaches(
    roles: List<CoachRole>,
    numbers: List<String>,
    altByIndex: Map<Int, String> = emptyMap(),
    endByIndex: Map<Int, String> = emptyMap()
  ): List<RakeCoach> = numbers.mapIndexed { idx, num ->
    RakeCoach(
      position = (idx + 1).toLong(),
      coachNumber = num,
      coachType = roles.getOrElse(idx) { CoachRole.TC }.name,
      altNumber = altByIndex[idx].orEmpty(),
      endTag = endByIndex[idx].orEmpty()
    )
  }

  private fun convCoach(
    position: Int,
    number: String,
    alt: String = ""
  ): RakeCoach {
    val suffixSource = number.trim().uppercase()
    val altSource = alt.trim().uppercase()
    val role = when {
      suffixSource.endsWith("B") || altSource.endsWith("B") -> CoachRole.MC
      suffixSource.endsWith("C") || altSource.endsWith("C") -> CoachRole.TC
      suffixSource.endsWith("D") || altSource.endsWith("D") -> CoachRole.TC
      else -> CoachRole.TC
    }
    return RakeCoach(
      position = position.toLong(),
      coachNumber = number,
      coachType = role.name,
      altNumber = alt
    )
  }

  private fun convFormation(
    id: String,
    sortOrder: Long,
    specs: List<Pair<String, String>>,
    remarks: String = ""
  ): RakeFormation {
    val builtCoaches = specs.mapIndexed { idx, (num, alt) -> convCoach(idx + 1, num, alt) }
    val derivedName = builtCoaches
      .filter { it.coachRoleEnum == CoachRole.MC }
      .joinToString("-") { c ->
        if (c.altNumber.isNotBlank()) "${c.coachNumber}(${c.altNumber})" else c.coachNumber
      }
    return RakeFormation(
      id = id,
      userId = "ppio_main",
      rakeName = derivedName,
      category = RakeCategory.EMU_CONV_9CAR.name,
      sortOrder = sortOrder,
      remarks = remarks,
      coaches = builtCoaches
    )
  }

  val all: List<RakeFormation> = listOf(
    // --- 3-PHASE EMU (12 CAR) — named by DTC-DTC ---
    RakeFormation(
      id = "rake_emu3p_1",
      userId = "ppio_main",
      rakeName = "188396-188397",
      category = RakeCategory.EMU_3PHASE_12CAR.name,
      sortOrder = 101L,
      coaches = buildCoaches(
        EMU_3PHASE_12_ROLES,
        listOf("188396", "388", "392", "398", "389", "393", "399", "390", "394", "395", "391", "188397")
      )
    ),
    RakeFormation(
      id = "rake_emu3p_2",
      userId = "ppio_main",
      rakeName = "188422-188423",
      category = RakeCategory.EMU_3PHASE_12CAR.name,
      sortOrder = 102L,
      coaches = buildCoaches(
        EMU_3PHASE_12_ROLES,
        listOf("188422", "414", "418", "424", "415", "419", "425", "416", "420", "421", "417", "188423")
      )
    ),
    RakeFormation(
      id = "rake_emu3p_3",
      userId = "ppio_main",
      rakeName = "188408-188409",
      category = RakeCategory.EMU_3PHASE_12CAR.name,
      remarks = "MC 449 swapped with 188456-188457 (401)",
      sortOrder = 103L,
      coaches = buildCoaches(
        EMU_3PHASE_12_ROLES,
        listOf("188408", "400", "404", "410", "449", "405", "411", "402", "406", "407", "403", "188409")
      )
    ),
    RakeFormation(
      id = "rake_emu3p_4",
      userId = "ppio_main",
      rakeName = "198047-198048",
      category = RakeCategory.EMU_3PHASE_12CAR.name,
      sortOrder = 104L,
      coaches = buildCoaches(
        EMU_3PHASE_12_ROLES,
        listOf("198047", "039", "043", "049", "040", "044", "050", "041", "045", "046", "042", "198048")
      )
    ),
    RakeFormation(
      id = "rake_emu3p_5",
      userId = "ppio_main",
      rakeName = "198131-198132",
      category = RakeCategory.EMU_3PHASE_12CAR.name,
      sortOrder = 105L,
      coaches = buildCoaches(
        EMU_3PHASE_12_ROLES,
        listOf("198131", "115", "123", "135", "116", "124", "136", "117", "125", "126", "118", "198132")
      )
    ),
    RakeFormation(
      id = "rake_emu3p_6",
      userId = "ppio_main",
      rakeName = "188456-188457",
      category = RakeCategory.EMU_3PHASE_12CAR.name,
      remarks = "MC 401 swapped with 188408-188409 (449)",
      sortOrder = 106L,
      coaches = buildCoaches(
        EMU_3PHASE_12_ROLES,
        listOf("188456", "448", "452", "462", "401", "453", "463", "450", "454", "455", "451", "188457")
      )
    ),

    // --- EMU CONV (9 CAR) — named by MC-MC-MC ---
    convFormation(
      id = "rake_emuc_1",
      sortOrder = 201L,
      specs = listOf(
        "51B" to "",
        "4C" to "",
        "4D" to "",
        "30D" to "",
        "30C" to "",
        "109026" to "21B",
        "5D" to "",
        "5C" to "",
        "31B" to ""
      )
    ),
    convFormation(
      id = "rake_emuc_2",
      sortOrder = 202L,
      specs = listOf(
        "80B" to "7B",
        "77C" to "28C",
        "78D" to "28D",
        "15B" to "",
        "28C" to "26C",
        "27D" to "26D",
        "25D" to "25D",
        "26C" to "25C",
        "02B" to ""
      )
    ),
    convFormation(
      id = "rake_emuc_3",
      sortOrder = 203L,
      remarks = "61B full coach no. 038161 (13B)",
      specs = listOf(
        "68B" to "03B",
        "64C" to "11C",
        "63D" to "11D",
        "100D" to "29D",
        "99C" to "29C",
        "61B" to "13B",
        "01D" to "",
        "01C" to "",
        "18B" to ""
      )
    ),
    convFormation(
      id = "rake_emuc_4",
      sortOrder = 204L,
      specs = listOf(
        "56B" to "12B",
        "57C" to "12C",
        "58D" to "12D",
        "81B" to "1B",
        "6C" to "",
        "6D" to "",
        "91D" to "3D",
        "90C" to "3C",
        "10B" to "20B"
      )
    ),
    convFormation(
      id = "rake_emuc_5",
      sortOrder = 205L,
      specs = listOf(
        "38B" to "27B",
        "23C" to "",
        "23D" to "",
        "50B" to "10B",
        "86C" to "8C",
        "87D" to "8D",
        "88D" to "9D",
        "89C" to "9C",
        "05B" to ""
      )
    ),
    convFormation(
      id = "rake_emuc_6",
      sortOrder = 206L,
      remarks = "61B full coach no. 028161 (4B)",
      specs = listOf(
        "109049B" to "",
        "36C" to "",
        "35D" to "",
        "61B" to "4B",
        "92C" to "",
        "93D" to "",
        "49D" to "10D",
        "51C" to "10C",
        "52B" to ""
      )
    ),
    convFormation(
      id = "rake_emuc_7",
      sortOrder = 207L,
      specs = listOf(
        "62B" to "",
        "65C" to "",
        "66D" to "",
        "038147B" to "",
        "02C" to "",
        "02D" to "",
        "20D" to "",
        "20C" to "",
        "84B" to ""
      )
    ),
    convFormation(
      id = "rake_emuc_8",
      sortOrder = 208L,
      specs = listOf(
        "32B" to "",
        "24C" to "",
        "24D" to "",
        "60B" to "14B",
        "94C" to "27C",
        "96D" to "27D",
        "97D" to "31D",
        "95C" to "31C",
        "85B" to "9B"
      )
    ),
    convFormation(
      id = "rake_emuc_9",
      sortOrder = 209L,
      specs = listOf(
        "118732B" to "",
        "70C" to "",
        "69D" to "",
        "48B" to "",
        "07C" to "",
        "06D" to "",
        "71D" to "",
        "72C" to "",
        "109047B" to ""
      )
    ),
    convFormation(
      id = "rake_emuc_10",
      sortOrder = 210L,
      remarks = "BT Rake",
      specs = listOf(
        "14B" to "",
        "17C" to "",
        "08D" to "",
        "13B" to "",
        "16C" to "",
        "19D" to "",
        "09D" to "",
        "18C" to "",
        "12B" to ""
      )
    ),

    // --- 3-PHASE MEMU (12 CAR) — named by DMC-DMC-DMC ---
    RakeFormation(
      id = "rake_memu3p_1",
      userId = "ppio_main",
      rakeName = "198870-198866-198869",
      category = RakeCategory.MEMU_3PHASE_12CAR.name,
      tcasFitted = true,
      startEnd = "KZJ",
      finishEnd = "SC",
      sortOrder = 301L,
      coaches = buildCoaches(
        MEMU_12_ROLES,
        listOf("198870", "888", "887", "886", "198866", "876", "875", "874", "885", "884", "883", "198869"),
        endByIndex = mapOf(0 to "KZJ", 4 to "KZJ", 11 to "SC")
      )
    ),
    RakeFormation(
      id = "rake_memu3p_2",
      userId = "ppio_main",
      rakeName = "198662-198868-198661",
      category = RakeCategory.MEMU_3PHASE_12CAR.name,
      tcasFitted = true,
      startEnd = "KZJ",
      finishEnd = "SC",
      sortOrder = 302L,
      coaches = buildCoaches(
        MEMU_12_ROLES,
        listOf("198662", "679", "680", "678", "198868", "882", "881", "880", "677", "676", "675", "198661"),
        endByIndex = mapOf(0 to "KZJ", 4 to "KZJ", 11 to "SC")
      )
    ),
    RakeFormation(
      id = "rake_memu3p_3",
      userId = "ppio_main",
      rakeName = "218252-218090-218089",
      category = RakeCategory.MEMU_3PHASE_12CAR.name,
      tcasFitted = true,
      startEnd = "KZJ",
      finishEnd = "SC",
      sortOrder = 303L,
      coaches = buildCoaches(
        MEMU_12_ROLES,
        listOf("218252", "266", "265", "264", "218090", "100", "099", "098", "097", "096", "095", "218089"),
        endByIndex = mapOf(0 to "KZJ", 4 to "KZJ", 11 to "SC")
      )
    ),
    RakeFormation(
      id = "rake_memu3p_4",
      userId = "ppio_main",
      rakeName = "218249-218251-218250",
      category = RakeCategory.MEMU_3PHASE_12CAR.name,
      tcasFitted = true,
      startEnd = "KZJ",
      finishEnd = "SC",
      remarks = "TCAS fitted on 218249",
      sortOrder = 304L,
      coaches = buildCoaches(
        MEMU_12_ROLES,
        listOf("218249", "255", "256", "257", "218251", "261", "262", "263", "258", "259", "260", "218250"),
        endByIndex = mapOf(0 to "KZJ", 4 to "KZJ", 11 to "SC")
      )
    ),
    RakeFormation(
      id = "rake_memu3p_5",
      userId = "ppio_main",
      rakeName = "218253-218093-218094",
      category = RakeCategory.MEMU_3PHASE_12CAR.name,
      tcasFitted = true,
      startEnd = "SC",
      finishEnd = "KZJ",
      remarks = "TCAS fitted on 218253",
      sortOrder = 305L,
      coaches = buildCoaches(
        MEMU_12_ROLES,
        listOf("218253", "267", "268", "269", "218093", "107", "108", "109", "110", "111", "112", "218094"),
        endByIndex = mapOf(0 to "SC", 4 to "SC", 11 to "KZJ")
      )
    ),
    RakeFormation(
      id = "rake_memu3p_7",
      userId = "ppio_main",
      rakeName = "218254-218092-218091",
      category = RakeCategory.MEMU_3PHASE_12CAR.name,
      tcasFitted = true,
      startEnd = "KZJ",
      finishEnd = "SC",
      sortOrder = 307L,
      coaches = buildCoaches(
        MEMU_12_ROLES,
        listOf("218254", "272", "271", "270", "218092", "106", "105", "104", "103", "102", "101", "218091"),
        endByIndex = mapOf(0 to "KZJ", 11 to "SC")
      )
    ),

    // --- BHEL MEMU (12 CAR & 8 CAR) — named by DMCs ---
    RakeFormation(
      id = "rake_bhel_1",
      userId = "ppio_main",
      rakeName = "258054-258062-258053",
      category = RakeCategory.MEMU_BHEL.name,
      make = "ICF",
      sortOrder = 401L,
      coaches = buildCoaches(
        MEMU_12_ROLES,
        listOf("258054", "068", "066", "067", "258062", "059", "058", "055", "060", "057", "056", "258053")
      )
    ),
    RakeFormation(
      id = "rake_bhel_2",
      userId = "ppio_main",
      rakeName = "258061-258069-258070",
      category = RakeCategory.MEMU_BHEL.name,
      make = "ICF",
      tcasFitted = true,
      sortOrder = 402L,
      coaches = buildCoaches(
        MEMU_12_ROLES,
        listOf("258061", "063", "065", "064", "258069", "075", "071", "072", "073", "074", "076", "258070")
      )
    ),
    RakeFormation(
      id = "rake_bhel_3",
      userId = "ppio_main",
      rakeName = "248627-248629-248630",
      category = RakeCategory.MEMU_BHEL.name,
      make = "RCF",
      sortOrder = 403L,
      coaches = buildCoaches(
        MEMU_12_ROLES,
        listOf("248627", "637", "638", "639", "248629", "643", "644", "645", "646", "647", "648", "248630")
      )
    ),
    RakeFormation(
      id = "rake_bhel_4",
      userId = "ppio_main",
      rakeName = "248625-248626",
      category = RakeCategory.MEMU_BHEL.name,
      make = "RCF",
      remarks = "8-Car BHEL MEMU",
      sortOrder = 404L,
      coaches = buildCoaches(
        MEMU_8_ROLES,
        listOf("248625", "631", "632", "633", "634", "635", "636", "248626")
      )
    ),
    RakeFormation(
      id = "rake_bhel_5",
      userId = "ppio_main",
      rakeName = "253538-253540-253539",
      category = RakeCategory.MEMU_BHEL.name,
      make = "RCF",
      sortOrder = 405L,
      coaches = buildCoaches(
        MEMU_12_ROLES,
        listOf("253538", "543", "542", "541", "253540", "547", "548", "549", "546", "545", "544", "253539")
      )
    ),

    // --- SPARE UNIT — named by DMC ---
    RakeFormation(
      id = "rake_spare_1",
      userId = "ppio_main",
      rakeName = "198865",
      category = RakeCategory.SPARE_UNIT.name,
      tcasFitted = true,
      remarks = "4-Car Spare Unit (TCAS fitted on 198865)",
      sortOrder = 501L,
      coaches = listOf(
        RakeCoach(position = 1L, coachNumber = "198865", coachType = CoachRole.DMC.name),
        RakeCoach(position = 2L, coachNumber = "198871", coachType = CoachRole.TC1.name),
        RakeCoach(position = 3L, coachNumber = "198872", coachType = CoachRole.TC2.name),
        RakeCoach(position = 4L, coachNumber = "198873", coachType = CoachRole.TC3.name)
      )
    ),

    // --- VANDE BHARAT — named by DTC-DTC (R-xx) ---
    RakeFormation(
      id = "rake_vb_83",
      userId = "ppio_main",
      rakeName = "251124-251125 (R-83)",
      category = RakeCategory.VANDE_BHARAT.name,
      make = "ICF",
      startEnd = "KZJ",
      finishEnd = "SC",
      remarks = "Driving cabs recorded — tap Edit to select 16-Car or 20-Car formation",
      sortOrder = 601L,
      coaches = listOf(
        RakeCoach(position = 1L, coachNumber = "251124", coachType = CoachRole.DTC.name, endTag = "KZJ"),
        RakeCoach(position = 2L, coachNumber = "251125", coachType = CoachRole.DTC.name, endTag = "SC")
      )
    ),
    RakeFormation(
      id = "rake_vb_91",
      userId = "ppio_main",
      rakeName = "252091-252092 (R-91)",
      category = RakeCategory.VANDE_BHARAT.name,
      make = "ICF",
      startEnd = "KZJ",
      finishEnd = "SC",
      remarks = "Driving cabs recorded — tap Edit to select 16-Car or 20-Car formation",
      sortOrder = 602L,
      coaches = listOf(
        RakeCoach(position = 1L, coachNumber = "252091", coachType = CoachRole.DTC.name, endTag = "KZJ"),
        RakeCoach(position = 2L, coachNumber = "252092", coachType = CoachRole.DTC.name, endTag = "SC")
      )
    ),
    RakeFormation(
      id = "rake_vb_6",
      userId = "ppio_main",
      rakeName = "235002-235001 (R-6)",
      category = RakeCategory.VANDE_BHARAT.name,
      make = "ICF",
      startEnd = "KZJ",
      finishEnd = "SC",
      remarks = "Driving cabs recorded — tap Edit to select 16-Car or 20-Car formation",
      sortOrder = 603L,
      coaches = listOf(
        RakeCoach(position = 1L, coachNumber = "235002", coachType = CoachRole.DTC.name, endTag = "KZJ"),
        RakeCoach(position = 2L, coachNumber = "235001", coachType = CoachRole.DTC.name, endTag = "SC")
      )
    ),
    RakeFormation(
      id = "rake_vb_86",
      userId = "ppio_main",
      rakeName = "251232-251231 (R-86)",
      category = RakeCategory.VANDE_BHARAT.name,
      make = "ICF",
      startEnd = "KZJ",
      finishEnd = "SC",
      remarks = "Driving cabs recorded — tap Edit to select 16-Car or 20-Car formation",
      sortOrder = 604L,
      coaches = listOf(
        RakeCoach(position = 1L, coachNumber = "251232", coachType = CoachRole.DTC.name, endTag = "KZJ"),
        RakeCoach(position = 2L, coachNumber = "251231", coachType = CoachRole.DTC.name, endTag = "SC")
      )
    ),
    RakeFormation(
      id = "rake_vb_88",
      userId = "ppio_main",
      rakeName = "251771-251770 (R-88)",
      category = RakeCategory.VANDE_BHARAT.name,
      make = "ICF",
      startEnd = "KZJ",
      finishEnd = "SC",
      remarks = "Driving cabs recorded — tap Edit to select 16-Car or 20-Car formation",
      sortOrder = 605L,
      coaches = listOf(
        RakeCoach(position = 1L, coachNumber = "251771", coachType = CoachRole.DTC.name, endTag = "KZJ"),
        RakeCoach(position = 2L, coachNumber = "251770", coachType = CoachRole.DTC.name, endTag = "SC")
      )
    )
  )
}
