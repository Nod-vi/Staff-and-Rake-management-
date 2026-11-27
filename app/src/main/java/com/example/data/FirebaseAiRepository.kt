package com.example.data

import android.graphics.Bitmap
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class GroundedChunk(
  val text: String,
  val searchQueries: List<String> = emptyList(),
  val sources: List<Pair<String, String>> = emptyList()
)

class FirebaseAiRepository {

  private val model by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      tools = listOf(Tool.googleSearch()),
      systemInstruction = content {
        text(
          """
          You are the PPIO Depot Operations & Scheduling Assistant for an Indian Railways Train Set / EMU Maintenance Depot.
          You support all depot personnel based on their active role:
          - Admin & Supervisor (SSE/JE): Roster optimization, shift coverage analysis, leave & LAP/LHAP balance approvals, training-due relief planning, and emergency override risk checks.
          - ShiftIncharge: Live active shift monitoring (06/14, 14/22, 22/06, 08/17), duty relief hourly permissions, and station/shed deployment.
          - Storemate: Preparing shift drafts for Supervisor approval and tracking depot staffing readiness.
          - Technician: Checking personal tomorrow's duties, weekly rest compliance, CL/LAP/LHAP/Special CR leave eligibility, and depot safety practices.

          Core Depot Rules:
          - Duty day cycle starts with the previous calendar date's 22/06 Night Shift followed by the date's 06/14 Morning, 14/22 Afternoon, and 08/17 General Shift (maximum one assignment per staff member per duty day).
          - Shift targets: 1 Pointsman + 3–5 Shed staff per regular shift; 0–3 General Shift staff; 2 Station staff at SC in 06/14 & 14/22; 2 Station staff at UR in 22/06; 1 Station staff at MED in 22/06 Mon–Thu.
          - Pointsman competency is required for Pointsman slots (Pointsmen may also cover Shed/Station slots when qualified). Shunter assignments are restricted to qualified Shunters.
          - Enforce weekly rest days, HOER rest periods, and approved leave exclusions.

          Use the provided [Current Depot Context] accurately when answering roster questions, and use Google Search grounding when asked about external railway safety rules, circulars, or maintenance best practices.
          Keep responses concise, structured with clear bullet points, and directly actionable.
          """.trimIndent()
        )
      }
    )
  }

  fun sendMessageStream(
    history: List<Content>,
    promptWithContext: String,
    attachedImage: Bitmap? = null
  ): Flow<GroundedChunk> {
    val chat = model.startChat(history = history)
    val userContent = content(role = "user") {
      if (attachedImage != null) {
        image(attachedImage)
      }
      text(promptWithContext)
    }
    return chat.sendMessageStream(userContent).map { response ->
      val grounding = response.candidates.firstOrNull()?.groundingMetadata
      val queries = grounding?.webSearchQueries ?: emptyList()
      val sources = grounding?.groundingChunks
        ?.mapNotNull { chunk ->
          val web = chunk.web
          val uri = web?.uri
          val title = web?.title
          if (!uri.isNullOrBlank()) (title ?: uri) to uri else null
        } ?: emptyList()
      GroundedChunk(
        text = response.text.orEmpty(),
        searchQueries = queries,
        sources = sources
      )
    }
  }
}
