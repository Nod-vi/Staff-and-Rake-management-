package com.example.data

import com.example.model.Competency
import com.example.model.Designation
import com.example.model.StaffMember
import com.example.model.WeeklyRestDay

object DefaultPpioRoster {
  /**
   * Canonical 35 unique staff entries extracted from PPIO_STAFF_SEGREGATED.
   * Duplicate S.NO 38 (VIVEK CHOUDHARY) is deduplicated into a single record (staff_38),
   * and competency typo "PIONTSMAN" is normalized to Competency.POINTSMAN.
   */
  fun initialStaff(userId: String): List<StaffMember> = listOf(
    staff(userId, 1, "VENKATA KRISHNA", Designation.SSE, Competency.NONE, station = false, shed = false),
    staff(userId, 2, "PRAKASH", Designation.SSE, Competency.NONE, station = false, shed = false),
    staff(userId, 3, "Y. SAI KRISHNA", Designation.JE, Competency.NONE, station = false, shed = false),
    staff(userId, 4, "CH. VINOD", Designation.JE, Competency.NONE, station = false, shed = false),
    staff(userId, 5, "N. CHAITANYA PRASAD", Designation.JE, Competency.NONE, station = false, shed = false),
    staff(userId, 6, "M. PRAVEEN KUMAR", Designation.JE, Competency.NONE, station = false, shed = false),
    staff(userId, 7, "P. RAM KUMAR", Designation.SR_TECH, Competency.NONE, station = true, shed = false),
    staff(userId, 8, "K. DANIEL RAJ", Designation.SR_TECH, Competency.NONE, station = true, shed = false),
    staff(userId, 9, "R. YADAGIRI", Designation.SR_TECH, Competency.NONE, station = false, shed = true),
    staff(userId, 10, "R. MURALI KRISHNA", Designation.SR_TECH, Competency.NONE, station = false, shed = true),
    staff(userId, 11, "I. ANANTHA RAJ", Designation.TECH_I, Competency.NONE, station = true, shed = false),
    staff(userId, 12, "S. SRINIVAS", Designation.TECH_I, Competency.NONE, station = true, shed = false),
    staff(userId, 13, "G. KRISHNA KUMAR", Designation.TECH_I, Competency.NONE, station = false, shed = true),
    staff(userId, 15, "B. KISHAN", Designation.TECH_I, Competency.NONE, station = false, shed = true),
    staff(userId, 16, "G. RAMA KRISHNA", Designation.TECH_I, Competency.NONE, station = false, shed = true),
    staff(userId, 17, "RITU RAJ", Designation.TECH_I, Competency.NONE, station = true, shed = true),
    staff(userId, 18, "RAUSHAN KUMAR", Designation.TECH_I, Competency.NONE, station = true, shed = true),
    staff(userId, 19, "AMIT DEBNATH", Designation.TECH_I, Competency.NONE, station = false, shed = true),
    staff(userId, 20, "B. RAJESH", Designation.TECH_II, Competency.NONE, station = false, shed = true),
    staff(userId, 21, "K. MAHESH", Designation.TECH_II, Competency.NONE, station = false, shed = true),
    staff(userId, 24, "GIRIRAJ MEENA", Designation.TECH_III, Competency.NONE, station = false, shed = true),
    staff(userId, 25, "RAJ MANGAL SINGH", Designation.TECH_III, Competency.NONE, station = false, shed = true),
    staff(userId, 26, "P. SRINIVAS RAO", Designation.TECH_III, Competency.NONE, station = true, shed = false),
    staff(userId, 27, "ANJANEYULU", Designation.TECH_III, Competency.NONE, station = false, shed = true),
    staff(userId, 28, "ASIN", Designation.KHP, Competency.NONE, station = true, shed = true),
    staff(userId, 29, "G. RAJESH", Designation.KHP, Competency.POINTSMAN, station = true, shed = true),
    staff(userId, 30, "A. CHIRANJEEVI", Designation.KHP, Competency.NONE, station = false, shed = true),
    staff(userId, 31, "MANJIT KUMAR", Designation.KHP, Competency.POINTSMAN, station = true, shed = true),
    staff(userId, 32, "Y. HARI PRASHANTH", Designation.KHP, Competency.NONE, station = false, shed = true),
    staff(userId, 33, "SURYA SUMAN", Designation.KHP, Competency.NONE, station = false, shed = true),
    staff(userId, 34, "A. BHASKAR", Designation.KHP, Competency.NONE, station = false, shed = true),
    staff(userId, 35, "R. NAGARAJU", Designation.TECH_III, Competency.POINTSMAN, station = false, shed = true),
    staff(userId, 36, "P. VAMSHI", Designation.TECH_III, Competency.POINTSMAN, station = false, shed = true),
    staff(userId, 37, "G. BALA MURALI KRISHNA", Designation.TECH_III, Competency.POINTSMAN, station = false, shed = true),
    staff(userId, 38, "VIVEK CHOUDHARY", Designation.KHP, Competency.POINTSMAN, station = false, shed = true)
  )

  private fun staff(
    userId: String,
    serialNo: Int,
    name: String,
    designation: Designation,
    competency: Competency,
    station: Boolean,
    shed: Boolean
  ): StaffMember = StaffMember(
    id = "staff_$serialNo",
    userId = userId,
    serialNo = serialNo.toLong(),
    name = name,
    designation = designation.name,
    competency = competency.name,
    stationDuty = station,
    shedDuty = shed,
    weeklyRestDay = WeeklyRestDay.NONE.name
  )
}
