package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.UserRole
import com.example.ui.theme.DepotTeal
import com.example.ui.theme.DepotTealContainer
import com.example.ui.theme.DepotTealOnContainer
import com.example.ui.theme.GlassIconOrb
import com.example.ui.theme.GlassPillBadge
import com.example.ui.theme.GlassRimHighlight
import com.example.ui.theme.GlassSurfaceBright
import com.example.ui.theme.GlassSurfaceMid
import com.example.ui.theme.GlassSurfaceSoft
import com.example.ui.theme.GlassTile
import com.example.ui.theme.RailwayCobalt
import com.example.ui.theme.SignalCrimson
import com.example.ui.theme.SignalCrimsonContainer
import com.example.ui.theme.SignalCrimsonOnContainer
import com.example.ui.theme.liquidGlassAccent
import com.example.ui.theme.liquidGlassSurface
import com.example.viewmodel.AiChatUiState
import com.example.viewmodel.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiAssistantScreen(
  messages: List<ChatMessage>,
  uiState: AiChatUiState,
  userRole: UserRole = UserRole.SUPERVISOR,
  liveContextProvider: () -> String,
  onSendMessage: (String, String, UserRole, Bitmap?) -> Unit,
  onCancelStreaming: () -> Unit = {},
  onRetryLastMessage: (String, UserRole) -> Unit = { _, _ -> },
  onClearChat: (UserRole) -> Unit,
  onNotifyMessage: (String) -> Unit = {}
) {
  var inputText by remember { mutableStateOf("") }
  var pendingImage by remember { mutableStateOf<Bitmap?>(null) }
  val listState = rememberLazyListState()
  val isStreaming = uiState is AiChatUiState.Streaming
  val context = LocalContext.current
  val clipboardManager = LocalClipboardManager.current
  val coroutineScope = rememberCoroutineScope()

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) {
      coroutineScope.launch {
        val loaded = decodeScaledBitmap(context, uri, maxDimension = 1024)
        if (loaded != null) {
          pendingImage = loaded
        } else {
          onNotifyMessage("Unable to load selected image.")
        }
      }
    }
  }

  val quickPrompts = remember(userRole) {
    when (userRole) {
      UserRole.TECHNICIAN -> listOf(
        "Summarize my tomorrow's duty & timing",
        "Check my remaining CL, LAP, LHAP & Special CR",
        "Indian Railways HOER rest period rules",
        "Night shift 22/06 EMU depot safety checklist"
      )
      UserRole.SHIFT_INCHARGE -> listOf(
        "Analyze today's shift coverage & gaps",
        "Check Pointsman and MED duty readiness",
        "Best practices for EMU trainset depot night safety",
        "Emergency staff relief guidelines"
      )
      UserRole.STOREMATE -> listOf(
        "Check shift coverage before drafting duties",
        "Who is on leave or weekly rest today?",
        "Pointsman & Shed staffing rules summary",
        "Indian Railways HOER rest period rules"
      )
      UserRole.JE -> listOf(
        "Analyze today's shift coverage & draft needs",
        "Check Pointsman and MED duty readiness",
        "Who is on leave or weekly rest today?",
        "Indian Railways HOER rest period rules"
      )
      UserRole.ADMIN, UserRole.SSE, UserRole.SUPERVISOR -> listOf(
        "Analyze today's shift coverage & gaps",
        "Check Pointsman and MED duty readiness",
        "Summarize pending approvals & tight staff risks",
        "Indian Railways HOER rest period rules",
        "Best practices for EMU trainset depot night safety"
      )
    }
  }

  val streamingTextLength = messages.lastOrNull()?.text?.length ?: 0
  LaunchedEffect(messages.size, streamingTextLength) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.size - 1)
    }
  }

  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 780.dp)
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 8.dp)
      ) {
        GlassTile(
          shape = RoundedCornerShape(22.dp),
          topColor = GlassSurfaceBright,
          bottomColor = GlassSurfaceMid,
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.weight(1f)
            ) {
              GlassIconOrb(
                shape = RoundedCornerShape(14.dp),
                tintColor = RailwayCobalt.copy(alpha = 0.14f),
                modifier = Modifier.size(40.dp)
              ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              }
              Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FlowRow(
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Text(
                    text = "PPIO AI Assistant",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                  )
                  GlassPillBadge(
                    text = userRole.label.uppercase(),
                    tintColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    borderColor = RailwayCobalt
                  )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                  Icon(Icons.Default.Public, contentDescription = null, tint = DepotTeal, modifier = Modifier.size(13.dp))
                  Text(
                    text = "Gemini 3.8 Flash · Live Roster + Search",
                    style = MaterialTheme.typography.labelSmall,
                    color = DepotTeal,
                    maxLines = 2
                  )
                }
              }
            }

            IconButton(
              onClick = {
                pendingImage = null
                onClearChat(userRole)
              },
              modifier = Modifier
                .size(48.dp)
                .testTag("clear_chat_button")
            ) {
              Icon(Icons.Default.DeleteOutline, contentDescription = "Clear chat history")
            }
          }
        }
      }

      LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        itemsIndexed(quickPrompts) { index, prompt ->
          Box(
            modifier = Modifier
              .heightIn(min = 48.dp)
              .liquidGlassSurface(
                shape = RoundedCornerShape(50),
                topColor = GlassSurfaceBright,
                bottomColor = GlassSurfaceSoft,
                borderWidth = 1.dp
              )
              .clickable(enabled = !isStreaming) {
                val img = pendingImage
                pendingImage = null
                onSendMessage(prompt, liveContextProvider(), userRole, img)
              }
              .padding(horizontal = 14.dp, vertical = 10.dp)
              .testTag("ai_quick_prompt_$index"),
            contentAlignment = Alignment.Center
          ) {
            Text(prompt, style = MaterialTheme.typography.bodySmall)
          }
        }
      }

      LazyColumn(
        state = listState,
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .testTag("ai_chat_message_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        items(
          items = messages,
          key = { it.id },
          contentType = { "message" }
        ) { message ->
          val bubbleFg = when {
            message.isError -> SignalCrimsonOnContainer
            message.isUser -> Color.White
            else -> MaterialTheme.colorScheme.onSurface
          }

          val bubbleModifier = when {
            message.isUser -> Modifier
              .widthIn(max = 540.dp)
              .liquidGlassAccent(shape = RoundedCornerShape(22.dp))
            message.isError -> Modifier
              .widthIn(max = 540.dp)
              .liquidGlassSurface(
                shape = RoundedCornerShape(22.dp),
                topColor = GlassSurfaceBright,
                bottomColor = GlassSurfaceSoft,
                tintColor = SignalCrimsonContainer.copy(alpha = 0.72f)
              )
            else -> Modifier
              .widthIn(max = 540.dp)
              .liquidGlassSurface(
                shape = RoundedCornerShape(22.dp),
                topColor = GlassSurfaceBright,
                bottomColor = GlassSurfaceMid
              )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
          ) {
            Box(modifier = bubbleModifier) {
              Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (message.attachedImage != null) {
                  Image(
                    bitmap = message.attachedImage.asImageBitmap(),
                    contentDescription = "Attached depot photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(160.dp)
                      .clip(RoundedCornerShape(14.dp))
                  )
                }

                if (!message.isUser && message.text.isBlank() && isStreaming) {
                  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Analyzing depot roster & search sources...", style = MaterialTheme.typography.bodySmall)
                  }
                } else if (message.isUser || message.isError) {
                  Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = bubbleFg
                  )
                } else {
                  Text(
                    text = formatAssistantText(message.text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = bubbleFg
                  )
                }

                if (message.isError && !isStreaming) {
                  OutlinedButton(
                    onClick = { onRetryLastMessage(liveContextProvider(), userRole) },
                    border = BorderStroke(1.dp, SignalCrimson.copy(alpha = 0.65f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                      .heightIn(min = 48.dp)
                      .testTag("ai_retry_button")
                  ) {
                    Icon(
                      imageVector = Icons.Default.Refresh,
                      contentDescription = null,
                      tint = SignalCrimsonOnContainer,
                      modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry", color = SignalCrimsonOnContainer)
                  }
                } else if (!message.isUser && message.text.isNotBlank() && !isStreaming &&
                  message.id != "welcome" && !message.id.startsWith("welcome_")
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                  ) {
                    TextButton(
                      onClick = {
                        clipboardManager.setText(AnnotatedString(message.text))
                        onNotifyMessage("Copied assistant response.")
                      },
                      modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("ai_copy_response_${message.id}")
                    ) {
                      Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                      )
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("Copy", style = MaterialTheme.typography.labelMedium)
                    }
                  }
                }

                if (message.searchQueries.isNotEmpty() || message.sources.isNotEmpty()) {
                  GlassTile(
                    shape = RoundedCornerShape(14.dp),
                    topColor = GlassSurfaceBright,
                    bottomColor = GlassSurfaceSoft,
                    tintColor = DepotTealContainer.copy(alpha = 0.55f)
                  ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                          imageVector = Icons.Default.Public,
                          contentDescription = null,
                          tint = DepotTealOnContainer,
                          modifier = Modifier.size(14.dp)
                        )
                        Text(
                          text = "Grounded with Google Search",
                          style = MaterialTheme.typography.labelSmall,
                          color = DepotTealOnContainer,
                          fontWeight = FontWeight.Bold
                        )
                      }
                      if (message.searchQueries.isNotEmpty()) {
                        Text(
                          text = "Queries: ${message.searchQueries.joinToString(", ")}",
                          style = MaterialTheme.typography.bodySmall,
                          color = DepotTealOnContainer
                        )
                      }
                      if (message.sources.isNotEmpty()) {
                        FlowRow(
                          horizontalArrangement = Arrangement.spacedBy(6.dp),
                          verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                          message.sources.take(4).forEach { (title, url) ->
                            Surface(
                              color = GlassSurfaceBright,
                              shape = RoundedCornerShape(10.dp),
                              border = BorderStroke(1.dp, DepotTeal.copy(alpha = 0.4f)),
                              modifier = Modifier
                                .heightIn(min = 36.dp)
                                .clickable {
                                  runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                  }
                                }
                            ) {
                              Text(
                                text = title.take(36),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                              )
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }

      // Attached Image Preview Chip
      val previewBitmap = pendingImage
      if (previewBitmap != null) {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = GlassSurfaceSoft,
          border = BorderStroke(1.dp, GlassRimHighlight),
          modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("ai_attached_image_preview")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Image(
              bitmap = previewBitmap.asImageBitmap(),
              contentDescription = "Selected photo attachment",
              contentScale = ContentScale.Crop,
              modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
            )
            Text(
              text = "Photo attached for AI analysis",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Medium,
              modifier = Modifier.weight(1f)
            )
            IconButton(
              onClick = { pendingImage = null },
              modifier = Modifier
                .size(48.dp)
                .testTag("ai_remove_image_button")
            ) {
              Icon(Icons.Default.Close, contentDescription = "Remove attached photo")
            }
          }
        }
      }

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 8.dp)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .liquidGlassSurface(
              shape = RoundedCornerShape(24.dp),
              topColor = GlassSurfaceBright,
              bottomColor = GlassSurfaceMid,
              elevation = 6.dp
            )
            .padding(horizontal = 6.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(
            onClick = {
              photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
            enabled = !isStreaming,
            modifier = Modifier
              .size(48.dp)
              .testTag("ai_attach_photo_button")
          ) {
            Icon(
              imageVector = Icons.Default.AddPhotoAlternate,
              contentDescription = "Attach photo for AI analysis",
              tint = if (!isStreaming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            placeholder = { Text("Ask about shifts, duties, or railway rules...") },
            maxLines = 3,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
              unfocusedContainerColor = Color.Transparent,
              focusedContainerColor = Color.Transparent,
              unfocusedBorderColor = Color.Transparent,
              focusedBorderColor = Color.Transparent
            ),
            modifier = Modifier
              .weight(1f)
              .testTag("ai_chat_input")
          )
          Spacer(modifier = Modifier.width(4.dp))

          if (isStreaming) {
            IconButton(
              onClick = onCancelStreaming,
              modifier = Modifier
                .size(48.dp)
                .testTag("ai_chat_stop_button")
            ) {
              Icon(
                imageVector = Icons.Default.StopCircle,
                contentDescription = "Stop generating response",
                tint = SignalCrimson
              )
            }
          } else {
            val canSend = inputText.isNotBlank() || pendingImage != null
            IconButton(
              onClick = {
                val msg = inputText.trim()
                val img = pendingImage
                if ((msg.isNotEmpty() || img != null) && !isStreaming) {
                  inputText = ""
                  pendingImage = null
                  onSendMessage(msg, liveContextProvider(), userRole, img)
                }
              },
              enabled = canSend,
              modifier = Modifier
                .size(48.dp)
                .testTag("ai_chat_send_button")
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send message",
                tint = if (canSend) {
                  MaterialTheme.colorScheme.primary
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                }
              )
            }
          }
        }
      }
    }
  }
}

private fun formatAssistantText(raw: String): AnnotatedString {
  val normalized = raw
    .replace("\r\n", "\n")
    .lines()
    .joinToString("\n") { line ->
      val trimmed = line.trimStart()
      when {
        trimmed.startsWith("### ") -> "**${trimmed.removePrefix("### ").trim()}**"
        trimmed.startsWith("## ") -> "**${trimmed.removePrefix("## ").trim()}**"
        trimmed.startsWith("# ") -> "**${trimmed.removePrefix("# ").trim()}**"
        trimmed.startsWith("* ") -> "• ${trimmed.removePrefix("* ")}"
        trimmed.startsWith("- ") -> "• ${trimmed.removePrefix("- ")}"
        else -> line
      }
    }

  return buildAnnotatedString {
    var cursor = 0
    while (cursor < normalized.length) {
      val open = normalized.indexOf("**", cursor)
      if (open == -1) {
        append(normalized.substring(cursor))
        break
      }
      val close = normalized.indexOf("**", open + 2)
      if (close == -1) {
        append(normalized.substring(cursor))
        break
      }
      if (open > cursor) {
        append(normalized.substring(cursor, open))
      }
      val boldStart = length
      append(normalized.substring(open + 2, close))
      addStyle(
        style = SpanStyle(fontWeight = FontWeight.Bold),
        start = boldStart,
        end = length
      )
      cursor = close + 2
    }
  }
}

private suspend fun decodeScaledBitmap(
  context: Context,
  uri: Uri,
  maxDimension: Int
): Bitmap? = withContext(Dispatchers.IO) {
  runCatching {
    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { stream ->
      BitmapFactory.decodeStream(stream, null, boundsOpts)
    }
    val maxSide = max(boundsOpts.outWidth, boundsOpts.outHeight)
    var sampleSize = 1
    while (maxSide / sampleSize > maxDimension * 2) {
      sampleSize *= 2
    }
    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
      BitmapFactory.decodeStream(stream, null, decodeOpts)
    } ?: return@withContext null

    val largest = max(decoded.width, decoded.height)
    if (largest <= maxDimension) {
      decoded
    } else {
      val scale = maxDimension.toFloat() / largest.toFloat()
      val w = (decoded.width * scale).toInt().coerceAtLeast(1)
      val h = (decoded.height * scale).toInt().coerceAtLeast(1)
      Bitmap.createScaledBitmap(decoded, w, h, true).also {
        if (it !== decoded) decoded.recycle()
      }
    }
  }.getOrNull()
}
