package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Base64
import androidx.compose.ui.graphics.Path
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.*
import com.example.db.AppDatabase
import com.example.db.PromptRecord
import com.example.db.PromptRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

sealed interface RequestState {
    object Idle : RequestState
    object Loading : RequestState
    data class Success(val text: String) : RequestState
    data class Error(val errorMessage: String) : RequestState
}

data class ChatMessage(
    val sender: String, // "USER" or "AI"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatPersona(
    val id: String,
    val name: String,
    val description: String,
    val systemPrompt: String,
    val initialGreeting: String,
    val iconEmoji: String
)

class PlaygroundViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: PromptRepository

    init {
        val database = AppDatabase.getDatabase(application)
        repository = PromptRepository(database.promptDao())
    }

    // API Key availability check
    val apiKey: String = BuildConfig.GEMINI_API_KEY
    val isApiKeyConfigured = apiKey.isNotEmpty() && apiKey != "MY_GEMINI_API_KEY"

    // Model & Settings Configuration
    val availableModels = listOf("gemini-3.5-flash", "gemini-3.1-pro-preview")
    val selectedModel = MutableStateFlow("gemini-3.5-flash")
    val temperature = MutableStateFlow(0.7f)
    val maxTokens = MutableStateFlow(1024)
    val systemInstruction = MutableStateFlow("You are a helpful, creative AI coding assistant and playground companion in Google AI Studio.")

    // Main Playground Screen State
    val promptInput = MutableStateFlow("")
    private val _playgroundState = MutableStateFlow<RequestState>(RequestState.Idle)
    val playgroundState: StateFlow<RequestState> = _playgroundState.asStateFlow()

    // Interactive Doodle Canvas Drawing State
    // We store drawing paths as an in-memory list of stroke coordinates
    val doodleInputPrompt = MutableStateFlow("Analyze my dynamic sketch: what did I draw and how would you build on this design concept?")
    private val _doodleState = MutableStateFlow<RequestState>(RequestState.Idle)
    val doodleState: StateFlow<RequestState> = _doodleState.asStateFlow()

    // Structured Outline/Templates Panel
    val templates = listOf(
        TemplateData(
            title = "🚀 Interactive Project Architect",
            promptPrefix = "Review this web development project idea, list exactly 3 structural features, suggest technology stack recommendations, and explain in simple bullets: ",
            parametersPlaceholder = "A localized organic seed swap directory where growers can barter, geo-route swaps, and check germination rates with photos."
        ),
        TemplateData(
            title = "🗓️ Dynamic Travel Planner",
            promptPrefix = "Create a detailed 3-day travel itinerary with hourly items (structured as high-contrast cards) for: ",
            parametersPlaceholder = "Kyoto, Japan during autumn, favoring historical micro-gardens and ramen shops."
        ),
        TemplateData(
            title = "🤖 Prompt Engineering Optimizer",
            promptPrefix = "Take the following basic prompt and rewrite it using Chain-of-Thought parameters, Roleplay anchors, and formatting constraints to get maximum precision from large models: ",
            parametersPlaceholder = "Explain how compilers work in Kotlin."
        )
    )
    val selectedTemplate = MutableStateFlow(templates[0])
    val templateParamInput = MutableStateFlow("")
    private val _templateState = MutableStateFlow<RequestState>(RequestState.Idle)
    val templateState: StateFlow<RequestState> = _templateState.asStateFlow()

    // Chatbot Companion Panel (Multi-Turn state)
    val chatPersonas = listOf(
        ChatPersona(
            id = "mentor",
            name = "Socratic AI Buddy",
            description = "Guides you by asking deep questions instead of giving plain answers.",
            systemPrompt = "You are a friendly Socratic mentor. Never give direct answers. Instead, respond with reflective questions, encouraging thoughts, and helpful metaphors that guide the user to discover answers.",
            initialGreeting = "Greetings seeker! What question or problem shall we look into together today?",
            iconEmoji = "🎓"
        ),
        ChatPersona(
            id = "brutalist",
            name = "Brutalist Code Coach",
            description = "Direct, punchy, high-intensity feedback on code, layout, and logic.",
            systemPrompt = "You are an expert brutalist code reviewer. Be highly opinionated, direct, and extremely terse. Cut out fluff, polite transitions, and self-praise. Do not insult the user, but point out anti-patterns directly with code examples.",
            initialGreeting = "Code review starting. Paste your logic. Let's make it robust.",
            iconEmoji = "🛠️"
        ),
        ChatPersona(
            id = "writer",
            name = "Stellar Storysmith",
            description = "Unlocks creative sci-fi, cyberpunk, and fantasy narratives.",
            systemPrompt = "You are a stellar creative writing assistant. Respond using rich description, neon cyberpunk atmospheres, dramatic sensory details, and vivid metaphors.",
            initialGreeting = "The electric rain patters on the glass. What neon fantasy shall we formulate tonight?",
            iconEmoji = "✍️"
        )
    )
    val activePersona = MutableStateFlow(chatPersonas[0])
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()
    private val _chatLoading = MutableStateFlow(false)
    val chatLoading: StateFlow<Boolean> = _chatLoading.asStateFlow()

    // History & Database Observation
    val isOnlyShowBookmarks = MutableStateFlow(false)
    val allHistory: StateFlow<List<PromptRecord>> = repository.allRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarkedHistory: StateFlow<List<PromptRecord>> = repository.bookmarkedRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Combine history list dynamically based on bookmark filter
    val filteredHistory: StateFlow<List<PromptRecord>> = combine(
        allHistory,
        bookmarkedHistory,
        isOnlyShowBookmarks
    ) { all, bookmarked, showOnlyBookmarks ->
        if (showOnlyBookmarks) bookmarked else all
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Initialize chat with greeting for default persona
        resetChat()
    }

    // Reset Chat messages
    fun resetChat() {
        val greeting = activePersona.value.initialGreeting
        _chatMessages.value = listOf(
            ChatMessage(sender = "AI", text = greeting)
        )
    }

    // Change chat persona
    fun setChatPersona(persona: ChatPersona) {
        activePersona.value = persona
        resetChat()
    }

    // Execution method for single General prompt
    fun sendPlaygroundPrompt() {
        val input = promptInput.value.trim()
        if (input.isEmpty()) return

        _playgroundState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                val config = GenerationConfig(
                    temperature = temperature.value,
                    maxOutputTokens = maxTokens.value
                )
                val system = if (systemInstruction.value.isNotEmpty()) {
                    Content(parts = listOf(Part(text = systemInstruction.value)))
                } else null

                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = input)))),
                    generationConfig = config,
                    systemInstruction = system
                )

                val response = withContext(Dispatchers.IO) {
                    GeminiClient.service.generateContent(
                        model = selectedModel.value,
                        apiKey = apiKey,
                        request = request
                    )
                }

                val textResponse = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "No text response generated by the model. Check safety settings/tokens."

                _playgroundState.value = RequestState.Success(textResponse)

                // Save to SQLite
                repository.insertRecord(
                    PromptRecord(
                        prompt = input,
                        response = textResponse,
                        modelUsed = selectedModel.value,
                        category = "General",
                        isError = false
                    )
                )

                promptInput.value = "" // clear input on success

            } catch (e: Exception) {
                val errorMessage = "Error: " + (e.localizedMessage ?: "Unknown API exception")
                _playgroundState.value = RequestState.Error(errorMessage)
                repository.insertRecord(
                    PromptRecord(
                        prompt = input,
                        response = errorMessage,
                        modelUsed = selectedModel.value,
                        category = "General",
                        isError = true
                    )
                )
            }
        }
    }

    // Send Structured Template Prompt
    fun sendTemplatePrompt() {
        val dynamicParam = templateParamInput.value.trim()
        val template = selectedTemplate.value
        val fullPrompt = template.promptPrefix + dynamicParam

        if (dynamicParam.isEmpty()) return

        _templateState.value = RequestState.Loading
        viewModelScope.launch {
            try {
                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = fullPrompt)))),
                    generationConfig = GenerationConfig(temperature = 0.5f, maxOutputTokens = 1500)
                )

                val response = withContext(Dispatchers.IO) {
                    GeminiClient.service.generateContent(
                        model = selectedModel.value,
                        apiKey = apiKey,
                        request = request
                    )
                }

                val textResponse = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "No content generated."

                _templateState.value = RequestState.Success(textResponse)

                repository.insertRecord(
                    PromptRecord(
                        prompt = "[Template - ${template.title}] $dynamicParam",
                        response = textResponse,
                        modelUsed = selectedModel.value,
                        category = "Template",
                        isError = false
                    )
                )

                templateParamInput.value = ""

            } catch (e: Exception) {
                val err = "Error: " + (e.localizedMessage ?: "Failed API Connection")
                _templateState.value = RequestState.Error(err)
                repository.insertRecord(
                    PromptRecord(
                        prompt = "[Template - ${template.title}] $dynamicParam",
                        response = err,
                        modelUsed = selectedModel.value,
                        category = "Template",
                        isError = true
                    )
                )
            }
        }
    }

    // Send Multi-turn Chat Conversation
    fun sendChatMessage(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty()) return

        // Add user message to list
        val currentList = _chatMessages.value.toMutableList()
        currentList.add(ChatMessage(sender = "USER", text = trimmed))
        _chatMessages.value = currentList

        _chatLoading.value = true

        viewModelScope.launch {
            try {
                // Construct conversations in role/parts format
                val mContents = mutableListOf<Content>()
                
                // Add system instructions first if available in the model configuration parameters
                val system = Content(parts = listOf(Part(text = activePersona.value.systemPrompt)))

                // Populate conversation history
                currentList.forEach { msg ->
                    val role = if (msg.sender == "USER") "user" else "model"
                    mContents.add(Content(
                        role = role,
                        parts = listOf(Part(text = msg.text))
                    ))
                }

                val request = GenerateContentRequest(
                    contents = mContents,
                    generationConfig = GenerationConfig(temperature = 0.8f, maxOutputTokens = 800),
                    systemInstruction = system
                )

                val response = withContext(Dispatchers.IO) {
                    GeminiClient.service.generateContent(
                        model = selectedModel.value,
                        apiKey = apiKey,
                        request = request
                    )
                }

                val aiResponseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "Persona remained silent. Please try another phrase."

                // Append response
                val updatedList = _chatMessages.value.toMutableList()
                updatedList.add(ChatMessage(sender = "AI", text = aiResponseText))
                _chatMessages.value = updatedList

                _chatLoading.value = false

                // Record final response in DB
                repository.insertRecord(
                    PromptRecord(
                        prompt = "[Chat - ${activePersona.value.name}] $trimmed",
                        response = aiResponseText,
                        modelUsed = selectedModel.value,
                        category = "Chat",
                        isError = false
                    )
                )

            } catch (e: Exception) {
                val err = "Oops! My antennas failed: " + (e.localizedMessage ?: "Connection error")
                val updatedList = _chatMessages.value.toMutableList()
                updatedList.add(ChatMessage(sender = "AI", text = err))
                _chatMessages.value = updatedList
                _chatLoading.value = false
            }
        }
    }

    // Handle Image Doodle Sketch Uploads
    fun sendDoodleSketch(bitmap: Bitmap) {
        val promptText = doodleInputPrompt.value.trim()
        _doodleState.value = RequestState.Loading

        viewModelScope.launch {
            try {
                // Convert bitmap to Base64 in background
                val base64Image = withContext(Dispatchers.Default) {
                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
                    val byteArray = outputStream.toByteArray()
                    Base64.encodeToString(byteArray, Base64.NO_WRAP)
                }

                val inlineData = InlineData(
                    mimeType = "image/jpeg",
                    data = base64Image
                )

                val request = GenerateContentRequest(
                    contents = listOf(
                        Content(
                            parts = listOf(
                                Part(text = promptText),
                                Part(inlineData = inlineData)
                            )
                        )
                    ),
                    generationConfig = GenerationConfig(temperature = 0.5f, maxOutputTokens = 1024)
                )

                // Models that support imagery are currently gemini-3.5-flash
                val model = "gemini-3.5-flash"

                val response = withContext(Dispatchers.IO) {
                    GeminiClient.service.generateContent(
                        model = model,
                        apiKey = apiKey,
                        request = request
                    )
                }

                val result = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "Doodle analyzed, but prompt returned no feedback text."

                _doodleState.value = RequestState.Success(result)

                repository.insertRecord(
                    PromptRecord(
                        prompt = "[Doodle Drawing Analysis] $promptText",
                        response = result,
                        modelUsed = model,
                        category = "Doodle",
                        isError = false
                    )
                )

            } catch (e: Exception) {
                val err = "Error analyzing sketch: " + (e.localizedMessage ?: "Unknown drawing parse issue")
                _doodleState.value = RequestState.Error(err)
                repository.insertRecord(
                    PromptRecord(
                        prompt = "[Doodle Drawing Analysis] $promptText",
                        response = err,
                        modelUsed = "gemini-3.5-flash",
                        category = "Doodle",
                        isError = true
                    )
                )
            }
        }
    }

    // Database action triggers
    fun toggleBookmark(id: Long, isBookmarked: Boolean) {
        viewModelScope.launch {
            repository.updateBookmarkStatus(id, isBookmarked)
        }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            repository.deleteRecordById(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }
}

data class TemplateData(
    val title: String,
    val promptPrefix: String,
    val parametersPlaceholder: String
)
