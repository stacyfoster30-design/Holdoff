package live.shouldiholdoff.holdoff

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import live.shouldiholdoff.holdoff.data.api.CompanionRequest
import live.shouldiholdoff.holdoff.data.api.RetrofitClient
import live.shouldiholdoff.holdoff.data.db.AppDatabase
import live.shouldiholdoff.holdoff.data.repositories.AuthRepository
import live.shouldiholdoff.holdoff.data.repositories.SMSRepository
import live.shouldiholdoff.holdoff.domain.models.CompanionMessage
import live.shouldiholdoff.holdoff.domain.models.SMSThread
import live.shouldiholdoff.holdoff.domain.models.User

data class AppUiState(
    val threads: List<SMSThread> = emptyList(),
    val companionMessages: List<CompanionMessage> = emptyList(),
    val user: User? = null,
    val isLoadingThreads: Boolean = false,
    val isCompanionLoading: Boolean = false,
    val selectedThread: SMSThread? = null,
    val isAnalyzingThread: Boolean = false,
    val error: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db          = AppDatabase.getDatabase(application)
    private val smsRepo     = SMSRepository(application)
    private val authRepo    = AuthRepository(application)

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        loadCachedThreads()
        loadUser()
    }

    // ── Threads ────────────────────────────────────────────────────────────────

    fun loadThreads() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingThreads = true)
            try {
                val threads = smsRepo.readThreads()
                if (threads.isNotEmpty()) {
                    db.threadDao().upsertThreads(threads)
                }
                val allThreads = db.threadDao().getAllThreads()
                _uiState.value = _uiState.value.copy(
                    threads = allThreads,
                    isLoadingThreads = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingThreads = false,
                    error = e.message
                )
            }
        }
    }

    private fun loadCachedThreads() {
        viewModelScope.launch {
            val cached = db.threadDao().getAllThreads()
            _uiState.value = _uiState.value.copy(threads = cached)
        }
    }

    fun selectThread(thread: SMSThread) {
        _uiState.value = _uiState.value.copy(selectedThread = thread, isAnalyzingThread = true)
        analyzeThread(thread)
    }

    fun clearSelectedThread() {
        _uiState.value = _uiState.value.copy(selectedThread = null)
    }

    private fun analyzeThread(thread: SMSThread) {
        viewModelScope.launch {
            try {
                val result = RetrofitClient.api.analyze(
                    request = live.shouldiholdoff.holdoff.data.api.AnalyzeRequest(
                        message = thread.lastMessage,
                        contactName = thread.contactName
                    ),
                    token = authRepo.authToken ?: ""
                )
                db.threadDao().updateVerdict(
                    id = thread.threadId,
                    verdict = result.verdict,
                    score = result.score,
                    insight = result.insight
                )
                val updated = db.threadDao().getThread(thread.threadId)
                _uiState.value = _uiState.value.copy(
                    selectedThread = updated,
                    isAnalyzingThread = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isAnalyzingThread = false)
            }
        }
    }

    // ── Companion ──────────────────────────────────────────────────────────────

    fun loadCompanionMessages() {
        viewModelScope.launch {
            val messages = db.companionDao().getAllMessages()
            _uiState.value = _uiState.value.copy(companionMessages = messages)
        }
    }

    fun sendCompanionMessage(text: String) {
        viewModelScope.launch {
            val userMsg = CompanionMessage(role = "user", content = text)
            db.companionDao().insertMessage(userMsg)
            val current = db.companionDao().getAllMessages()
            _uiState.value = _uiState.value.copy(
                companionMessages = current,
                isCompanionLoading = true
            )

            try {
                val history = current.takeLast(10).map { msg ->
                    mapOf("role" to msg.role, "content" to msg.content)
                }
                val response = RetrofitClient.api.companion(
                    request = CompanionRequest(
                        message = text,
                        conversationHistory = history,
                        userId = authRepo.userId ?: ""
                    ),
                    token = authRepo.authToken ?: ""
                )
                val assistantMsg = CompanionMessage(
                    role = "assistant",
                    content = response.reply,
                    mood = response.mood
                )
                db.companionDao().insertMessage(assistantMsg)
                db.companionDao().pruneOldMessages()
                val updated = db.companionDao().getAllMessages()
                _uiState.value = _uiState.value.copy(
                    companionMessages = updated,
                    isCompanionLoading = false
                )
            } catch (e: Exception) {
                val errorMsg = CompanionMessage(
                    role = "assistant",
                    content = "I'm having a little trouble connecting right now. Try again in a moment? 💜"
                )
                db.companionDao().insertMessage(errorMsg)
                val updated = db.companionDao().getAllMessages()
                _uiState.value = _uiState.value.copy(
                    companionMessages = updated,
                    isCompanionLoading = false
                )
            }
        }
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    private fun loadUser() {
        if (authRepo.isSignedIn) {
            _uiState.value = _uiState.value.copy(
                user = User(
                    userId = authRepo.userId ?: "",
                    email = authRepo.userEmail ?: "",
                    displayName = authRepo.userEmail?.substringBefore("@") ?: "User",
                    subscriptionStatus = authRepo.subscriptionStatus
                )
            )
        }
    }

    suspend fun signInWithGoogle(idToken: String): Result<Unit> {
        val result = authRepo.signInWithGoogle(idToken)
        if (result.isSuccess) loadUser()
        return result
    }

    fun signOut() {
        authRepo.signOut()
        _uiState.value = _uiState.value.copy(user = null)
    }

    val isPremium get() = authRepo.isPremium
    val authToken get() = authRepo.authToken
}
