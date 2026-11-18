package com.holdoff.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.holdoff.app.data.entity.DraftEntity
import com.holdoff.app.data.entity.MessageState
import com.holdoff.app.data.model.Message
import com.holdoff.app.data.model.Verdict
import com.holdoff.app.data.model.VerdictResult
import com.holdoff.app.data.network.HoldOffApi
import com.holdoff.app.data.prefs.TrustedContactsStore
import com.holdoff.app.data.repository.DraftRepository
import com.holdoff.app.data.repository.SMSRepository
import com.holdoff.app.data.sms.DefaultSmsManager
import com.holdoff.app.data.sms.SmsSender
import com.holdoff.app.data.spiral.SpiralLockManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The pre-send gate's state for the composer.
 *
 * The gate is the product: a message composed in HoldOff does not
 * leave until Sadie's verdict has run. Green sends; yellow pauses for
 * an explicit user decision; red (and spiral) physically holds the
 * message in the encrypted draft store instead of sending it.
 */
sealed class SendGateState {
    object Idle : SendGateState()
    object Checking : SendGateState()
    data class Sent(val body: String) : SendGateState()
    data class PausedYellow(val verdict: VerdictResult) : SendGateState()
    data class Held(val draftId: Long, val verdict: VerdictResult?, val reason: String) : SendGateState()
    data class BlockedByLock(val remainingMs: Long) : SendGateState()
    data class Failed(val reason: String) : SendGateState()
}

data class ThreadUiState(
    val messages: List<Message> = emptyList(),
    val contactName: String = "",
    val recipientPhone: String = "",
    val threadId: String = "",
    val isLoading: Boolean = true,
    val verdict: VerdictResult? = null,
    val isAnalyzing: Boolean = false,
    val draftMessage: String = "",
    val error: String? = null,
    val gate: SendGateState = SendGateState.Idle,
    val lockState: SpiralLockManager.LockState = SpiralLockManager.LockState(),
    val heldDrafts: List<DraftEntity> = emptyList()
)

class ThreadViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = SMSRepository(application)
    private val draftRepo = DraftRepository(application)

    private val _state = MutableStateFlow(ThreadUiState())
    val state: StateFlow<ThreadUiState> = _state

    fun loadThread(threadId: String, contactName: String) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(
                    isLoading = true,
                    contactName = contactName,
                    threadId = threadId,
                    lockState = SpiralLockManager.refresh(getApplication())
                )
                val msgs = repo.getMessagesForThread(threadId)
                val phone = withContext(Dispatchers.IO) { repo.getPhoneForThread(threadId) }
                _state.value = _state.value.copy(
                    messages = msgs,
                    recipientPhone = phone,
                    isLoading = false
                )
                if (phone.isNotBlank()) observeHeldDrafts(phone)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    private fun observeHeldDrafts(phone: String) {
        viewModelScope.launch {
            draftRepo.draftsForRecipient(phone).collect { drafts ->
                _state.value = _state.value.copy(
                    heldDrafts = drafts.filter {
                        it.state == MessageState.HELD || it.state == MessageState.READY_TO_SEND
                    }
                )
            }
        }
    }

    // ── Verdict fetching (shared by Analyze and the send gate) ───────────

    private suspend fun fetchVerdict(threadId: String, draftText: String): VerdictResult {
        val threadHistory = _state.value.messages.map { msg ->
            mapOf(
                "direction" to if (msg.isOutgoing) "sent" else "received",
                "body"      to msg.body,
                "timestamp" to msg.timestamp
            )
        }
        val apiResult = HoldOffApi.analyzeMessages(
            ctx           = getApplication(),
            messageText   = draftText,
            threadHistory = threadHistory
        )
        return VerdictResult(
            threadId        = threadId,
            verdict         = when (apiResult.optString("verdict", "HOLD")) {
                "SEND"    -> Verdict.REACH_OUT
                "REWRITE" -> Verdict.MAYBE
                else      -> Verdict.HOLD_OFF
            },
            confidence      = apiResult.optDouble("confidence", 0.75).toFloat(),
            reasoning       = apiResult.optString("feedback_text",
                apiResult.optString("reasoning", "Review before sending.")),
            patternInsights = buildList {
                apiResult.optString("attachmentPattern").takeIf { it.isNotBlank() }?.let {
                    add("Attachment pattern: $it")
                }
                apiResult.optString("safetyLevel").takeIf { it.isNotBlank() }?.let {
                    add("Safety level: $it")
                }
            },
            suggestedResponse = apiResult.optString("rewrite").takeIf { it.isNotBlank() },
            safetyLevel = apiResult.optString("safetyLevel").takeIf { it.isNotBlank() }
        )
    }

    /**
     * Analyze the loaded thread (existing behavior, unchanged): sends
     * the thread history to Sadie and surfaces the verdict for the
     * Verdict screen. Does not send or hold anything.
     */
    fun analyzeThread(threadId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isAnalyzing = true, error = null)
            try {
                val draftText = _state.value.draftMessage.takeIf { it.isNotBlank() }
                    ?: _state.value.messages.lastOrNull { it.isOutgoing }?.body
                    ?: _state.value.messages.lastOrNull()?.body
                    ?: ""
                val verdict = fetchVerdict(threadId, draftText)
                _state.value = _state.value.copy(isAnalyzing = false, verdict = verdict)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isAnalyzing = false,
                    error = "Sadie couldn't reach the server. Try again in a moment."
                )
            }
        }
    }

    // ── THE SEND GATE ────────────────────────────────────────────────────

    /**
     * The composer's Send button. Nothing leaves the phone from here
     * without a verdict:
     *
     *  - Spiral Lock active (and recipient not a trusted contact)
     *      -> BlockedByLock; nothing is even checked.
     *  - Verdict green  -> sent through SmsSender.
     *  - Verdict yellow -> PausedYellow: UI shows the verdict + rewrite;
     *    the user must explicitly confirm (confirmSendAfterPause),
     *    take the rewrite (useRewrite), or hold (holdAfterPause).
     *  - Verdict red    -> the message is persisted HELD in the
     *    encrypted draft store. It is NOT sent.
     *  - Verdict spiral -> HELD, and Spiral Lock engages app-wide.
     *  - Verdict unreachable -> the message is HELD, not sent. The
     *    product promise is verdict-first; a network failure must
     *    never silently become a send.
     */
    fun sendCurrentDraft(threadId: String) {
        val body = _state.value.draftMessage.trim()
        if (body.isBlank()) return
        val phone = _state.value.recipientPhone
        if (phone.isBlank()) {
            _state.value = _state.value.copy(
                gate = SendGateState.Failed("HoldOff doesn't know who this conversation is with yet.")
            )
            return
        }

        val ctx = getApplication<Application>()
        if (SpiralLockManager.isLocked(ctx) && !TrustedContactsStore.isTrusted(ctx, phone)) {
            _state.value = _state.value.copy(
                gate = SendGateState.BlockedByLock(SpiralLockManager.remainingMs(ctx)),
                lockState = SpiralLockManager.refresh(ctx)
            )
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(gate = SendGateState.Checking, error = null)
            try {
                val verdict = fetchVerdict(threadId, body)
                _state.value = _state.value.copy(verdict = verdict)
                when (gateLevel(verdict)) {
                    GateLevel.GREEN -> doSend(body)
                    GateLevel.YELLOW -> {
                        _state.value = _state.value.copy(gate = SendGateState.PausedYellow(verdict))
                    }
                    GateLevel.RED -> holdMessage(body, verdict, verdict.reasoning)
                    GateLevel.SPIRAL -> {
                        SpiralLockManager.engage(ctx, reason = "Sadie flagged a spiral in this conversation")
                        _state.value = _state.value.copy(lockState = SpiralLockManager.refresh(ctx))
                        holdMessage(body, verdict, "Spiral Lock is on. This message is held while you cool down.")
                    }
                }
            } catch (e: Exception) {
                // Verdict-first means failure = hold, never silent send.
                val id = draftRepo.holdDraft(phone, body)
                _state.value = _state.value.copy(
                    draftMessage = "",
                    gate = SendGateState.Held(
                        draftId = id,
                        verdict = null,
                        reason = "Sadie couldn't be reached, so this was held instead of sent. You can send it from the hold queue when you're back online."
                    )
                )
            }
        }
    }

    /** Yellow pause: the user read the verdict and still wants to send. */
    fun confirmSendAfterPause() {
        val gate = _state.value.gate
        if (gate !is SendGateState.PausedYellow) return
        val body = _state.value.draftMessage.trim()
        if (body.isBlank()) return
        viewModelScope.launch { doSend(body) }
    }

    /** Yellow pause: the user chooses the hold instead. */
    fun holdAfterPause() {
        val gate = _state.value.gate
        if (gate !is SendGateState.PausedYellow) return
        val body = _state.value.draftMessage.trim()
        if (body.isBlank()) return
        viewModelScope.launch { holdMessage(body, gate.verdict, gate.verdict.reasoning) }
    }

    /** Yellow pause: replace the draft with Sadie's rewrite (does not send). */
    fun useRewrite() {
        val gate = _state.value.gate
        if (gate !is SendGateState.PausedYellow) return
        val rewrite = gate.verdict.suggestedResponse ?: return
        _state.value = _state.value.copy(draftMessage = rewrite, gate = SendGateState.Idle)
    }

    /**
     * Verdict screen's send buttons. The verdict on screen IS the gate
     * decision for this draft, so this sends directly (still lock- and
     * role-checked) rather than re-running the analysis.
     */
    fun sendAnalyzedDraft(threadId: String) {
        val body = _state.value.draftMessage.takeIf { it.isNotBlank() }
            ?: _state.value.messages.lastOrNull { it.isOutgoing }?.body
            ?: return
        val phone = _state.value.recipientPhone
        if (phone.isBlank()) {
            _state.value = _state.value.copy(
                gate = SendGateState.Failed("HoldOff doesn't know who this conversation is with yet.")
            )
            return
        }
        val ctx = getApplication<Application>()
        if (SpiralLockManager.isLocked(ctx) && !TrustedContactsStore.isTrusted(ctx, phone)) {
            _state.value = _state.value.copy(
                gate = SendGateState.BlockedByLock(SpiralLockManager.remainingMs(ctx)),
                lockState = SpiralLockManager.refresh(ctx)
            )
            return
        }
        viewModelScope.launch { doSend(body.trim()) }
    }

    /** Sends a previously held draft — through the full gate again. */
    fun sendHeldDraft(draft: DraftEntity) {
        viewModelScope.launch { draftRepo.discard(draft) }
        _state.value = _state.value.copy(draftMessage = draft.messageBody, gate = SendGateState.Idle)
        sendCurrentDraft(_state.value.threadId)
    }

    fun discardHeldDraft(draft: DraftEntity) {
        viewModelScope.launch { draftRepo.discard(draft) }
    }

    /** Calm-sentence release from the lock banner. */
    fun releaseLockWithCalmSentence(text: String) {
        val ctx = getApplication<Application>()
        if (SpiralLockManager.isCalmSentence(text)) {
            SpiralLockManager.release(ctx, SpiralLockManager.ReleaseReason.CALM_SENTENCE)
            _state.value = _state.value.copy(
                lockState = SpiralLockManager.refresh(ctx),
                gate = if (_state.value.gate is SendGateState.BlockedByLock)
                    SendGateState.Idle else _state.value.gate
            )
        }
    }

    /** Re-reads persisted lock state (UI countdown ticking / resume). */
    fun refreshLock() {
        _state.value = _state.value.copy(lockState = SpiralLockManager.refresh(getApplication()))
    }

    // ── gate internals ───────────────────────────────────────────────────

    private enum class GateLevel { GREEN, YELLOW, RED, SPIRAL }

    private fun gateLevel(verdict: VerdictResult): GateLevel =
        when (verdict.safetyLevel?.lowercase()) {
            "green"  -> GateLevel.GREEN
            "yellow" -> GateLevel.YELLOW
            "red"    -> GateLevel.RED
            "spiral" -> GateLevel.SPIRAL
            else -> when (verdict.verdict) {
                Verdict.REACH_OUT -> GateLevel.GREEN
                Verdict.MAYBE     -> GateLevel.YELLOW
                Verdict.HOLD_OFF  -> GateLevel.RED
            }
        }

    private suspend fun holdMessage(body: String, verdict: VerdictResult?, reason: String) {
        val phone = _state.value.recipientPhone
        val id = draftRepo.holdDraft(phone, body)
        _state.value = _state.value.copy(
            draftMessage = "",
            gate = SendGateState.Held(draftId = id, verdict = verdict, reason = reason)
        )
    }

    private suspend fun doSend(body: String) {
        val ctx = getApplication<Application>()
        val phone = _state.value.recipientPhone

        if (!DefaultSmsManager.isDefaultSmsApp(ctx)) {
            _state.value = _state.value.copy(
                gate = SendGateState.Failed(
                    "HoldOff isn't your default texting app yet, so it can't send. Make HoldOff your texting app (banner above, or Settings) and try again — your draft is still here."
                )
            )
            return
        }

        val draftId = draftRepo.createOutgoingDraft(phone, body)
        when (val result = SmsSender.send(ctx, draftId, phone, body)) {
            is SmsSender.SendResult.Queued -> {
                _state.value = _state.value.copy(
                    draftMessage = "",
                    gate = SendGateState.Sent(body)
                )
                // The sent copy is already in the provider (SmsSender
                // inserts it); reload so the thread shows it.
                val msgs = repo.getMessagesForThread(_state.value.threadId)
                _state.value = _state.value.copy(messages = msgs)
            }
            is SmsSender.SendResult.Failed -> {
                _state.value = _state.value.copy(gate = SendGateState.Failed(result.reason))
            }
        }
    }

    fun updateDraft(text: String) {
        val gate = _state.value.gate
        _state.value = _state.value.copy(
            draftMessage = text,
            // Typing a new draft clears one-shot outcomes.
            gate = if (gate is SendGateState.Sent || gate is SendGateState.Failed)
                SendGateState.Idle else gate
        )
    }
}
