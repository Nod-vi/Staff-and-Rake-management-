package com.example.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FirebaseAiRepository
import com.example.model.UserRole
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class ChatMessage(
  val id: String,
  val isUser: Boolean,
  val text: String,
  val attachedImage: Bitmap? = null,
  val searchQueries: List<String> = emptyList(),
  val sources: List<Pair<String, String>> = emptyList(),
  val isError: Boolean = false
)

sealed interface AiChatUiState {
  data object Idle : AiChatUiState
  data object Streaming : AiChatUiState
  data class Error(val message: String) : AiChatUiState
}

class AiChatViewModel(
  private val repository: FirebaseAiRepository = FirebaseAiRepository()
) : ViewModel() {

  private val _messages = MutableStateFlow(
    listOf(
      ChatMessage(
        id = "welcome",
        isUser = false,
        text = "PPIO Depot AI Assistant ready. Ask about shift coverage, staffing gaps, leave rules, personal duties, or attach a depot notice/photo for analysis."
      )
    )
  )
  val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

  private val _uiState = MutableStateFlow<AiChatUiState>(AiChatUiState.Idle)
  val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

  private var activeStreamJob: Job? = null
  private var lastSubmittedPrompt: String? = null
  private var lastSubmittedImage: Bitmap? = null

  fun sendMessage(
    userText: String,
    liveRosterContext: String,
    userRole: UserRole = UserRole.SUPERVISOR,
    attachedImage: Bitmap? = null
  ) {
    val trimmed = userText.trim()
    if ((trimmed.isEmpty() && attachedImage == null) || _uiState.value is AiChatUiState.Streaming) return

    val effectiveUserPrompt = if (trimmed.isNotEmpty()) trimmed else "Analyze this attached image in the context of our depot operations."
    lastSubmittedPrompt = effectiveUserPrompt
    lastSubmittedImage = attachedImage

    val userMsg = ChatMessage(
      id = "u_${System.currentTimeMillis()}",
      isUser = true,
      text = effectiveUserPrompt,
      attachedImage = attachedImage
    )
    val historyBeforePrompt = buildHistory(_messages.value)
    val placeholderId = "m_${System.currentTimeMillis() + 1}"
    val initialBotMsg = ChatMessage(
      id = placeholderId,
      isUser = false,
      text = ""
    )

    _messages.value = _messages.value + userMsg + initialBotMsg
    _uiState.value = AiChatUiState.Streaming

    streamPrompt(
      placeholderId = placeholderId,
      historyBeforePrompt = historyBeforePrompt,
      userPrompt = effectiveUserPrompt,
      liveRosterContext = liveRosterContext,
      userRole = userRole,
      attachedImage = attachedImage
    )
  }

  fun retryLastMessage(
    liveRosterContext: String,
    userRole: UserRole = UserRole.SUPERVISOR
  ) {
    if (_uiState.value is AiChatUiState.Streaming) return
    val prompt = lastSubmittedPrompt ?: return
    val image = lastSubmittedImage

    // Replace trailing error message if present, otherwise append fresh assistant bubble
    val currentList = _messages.value
    val cleanedList = if (currentList.lastOrNull()?.isError == true) {
      currentList.dropLast(1)
    } else {
      currentList
    }
    val historyBeforePrompt = buildHistory(cleanedList.dropLast(1))
    val placeholderId = "m_${System.currentTimeMillis()}"
    _messages.value = cleanedList + ChatMessage(
      id = placeholderId,
      isUser = false,
      text = ""
    )
    _uiState.value = AiChatUiState.Streaming

    streamPrompt(
      placeholderId = placeholderId,
      historyBeforePrompt = historyBeforePrompt,
      userPrompt = prompt,
      liveRosterContext = liveRosterContext,
      userRole = userRole,
      attachedImage = image
    )
  }

  fun cancelStreaming() {
    val job = activeStreamJob ?: return
    activeStreamJob = null
    job.cancel()
    _messages.value = _messages.value.map { msg ->
      if (!msg.isUser && msg.text.isBlank()) {
        msg.copy(text = "Generation stopped by user.")
      } else {
        msg
      }
    }
    _uiState.value = AiChatUiState.Idle
  }

  private fun streamPrompt(
    placeholderId: String,
    historyBeforePrompt: List<Content>,
    userPrompt: String,
    liveRosterContext: String,
    userRole: UserRole,
    attachedImage: Bitmap?
  ) {
    val augmentedPrompt = """
      [Active User Role]
      ${userRole.label}

      [Current Depot Context]
      $liveRosterContext

      [User Request]
      $userPrompt
    """.trimIndent()

    activeStreamJob?.cancel()
    activeStreamJob = viewModelScope.launch {
      val accumulated = StringBuilder()
      val collectedQueries = linkedSetOf<String>()
      val collectedSources = linkedMapOf<String, String>()
      try {
        withTimeout(60_000L) {
          repository.sendMessageStream(
            history = historyBeforePrompt,
            promptWithContext = augmentedPrompt,
            attachedImage = attachedImage
          ).collect { chunk ->
            accumulated.append(chunk.text)
            collectedQueries.addAll(chunk.searchQueries)
            chunk.sources.forEach { (title, url) -> collectedSources[url] = title }
            updateBotMessage(
              id = placeholderId,
              text = accumulated.toString(),
              queries = collectedQueries.toList(),
              sources = collectedSources.entries.map { it.value to it.key }
            )
          }
        }
        if (accumulated.isBlank()) {
          val emptyMsg = "No response received from Gemini. Please try again."
          updateBotMessage(placeholderId, emptyMsg, isError = true)
          _uiState.value = AiChatUiState.Error(emptyMsg)
        } else {
          _uiState.value = AiChatUiState.Idle
        }
      } catch (e: TimeoutCancellationException) {
        Log.e(TAG, "AI generation timed out", e)
        val msg = "Assistant timed out after 60 seconds. Please retry."
        updateBotMessage(placeholderId, msg, isError = true)
        _uiState.value = AiChatUiState.Error(msg)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Firebase AI chat failed", e)
        val msg = e.localizedMessage ?: "Failed to generate assistant response."
        updateBotMessage(placeholderId, msg, isError = true)
        _uiState.value = AiChatUiState.Error(msg)
      } finally {
        activeStreamJob = null
      }
    }
  }

  fun clearConversation(userRole: UserRole = UserRole.SUPERVISOR) {
    activeStreamJob?.cancel()
    activeStreamJob = null
    lastSubmittedPrompt = null
    lastSubmittedImage = null
    val roleHint = when (userRole) {
      UserRole.TECHNICIAN -> "Conversation cleared. Ask about your upcoming duties, leave balances, or depot safety guidelines."
      UserRole.SHIFT_INCHARGE -> "Conversation cleared. Ask about who is on the active shift, relief permissions, or station coverage."
      UserRole.STOREMATE -> "Conversation cleared. Ask about shift draft rules, rest eligibility, or replacement suggestions."
      UserRole.JE -> "Conversation cleared. Ask about shift draft proposals, rake formations, relief permissions, or leave applications."
      UserRole.ADMIN, UserRole.SUPERVISOR, UserRole.SSE -> "Conversation cleared. Ask about shift coverage gaps, leave approvals, training due schedules, or railway safety."
    }
    _messages.value = listOf(
      ChatMessage(
        id = "welcome_${System.currentTimeMillis()}",
        isUser = false,
        text = roleHint
      )
    )
    _uiState.value = AiChatUiState.Idle
  }

  private fun updateBotMessage(
    id: String,
    text: String,
    queries: List<String> = emptyList(),
    sources: List<Pair<String, String>> = emptyList(),
    isError: Boolean = false
  ) {
    _messages.value = _messages.value.map { msg ->
      if (msg.id == id) {
        msg.copy(
          text = text,
          searchQueries = queries,
          sources = sources,
          isError = isError
        )
      } else {
        msg
      }
    }
  }

  private fun buildHistory(items: List<ChatMessage>): List<Content> {
    return items
      .filter { it.id != "welcome" && !it.id.startsWith("welcome_") && !it.isError && it.text.isNotBlank() }
      .takeLast(12)
      .map { msg ->
        content(role = if (msg.isUser) "user" else "model") {
          text(msg.text)
        }
      }
  }

  private companion object {
    const val TAG = "AiChatViewModel"
  }
}
