package com.holdoff.app.data.repository

import android.content.Context
import com.holdoff.app.data.HoldOffDatabase
import com.holdoff.app.data.entity.DraftEntity
import com.holdoff.app.data.entity.MessageState
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * DraftRepository — wires the Room/SQLCipher draft store into the app.
 *
 * The audit found HoldOffDatabase/DraftDao fully written but called
 * from nowhere. This repository is the single door into it:
 *  - The verdict gate persists RED verdicts here as HELD.
 *  - A yellow pause moves a draft to READY_TO_SEND on user confirm.
 *  - [com.holdoff.app.data.receiver.SmsStatusReceiver] settles SENT
 *    when Android reports the send result.
 *  - Threads read their held drafts from here so a held message is
 *    visible, sendable later, and discardable — never silently lost.
 */
class DraftRepository(context: Context) {

    private val dao = HoldOffDatabase.getInstance(context.applicationContext).draftDao()

    /** Persists a message the gate has held. Returns the draft id. */
    suspend fun holdDraft(recipientPhone: String, body: String): Long =
        dao.insertDraft(
            DraftEntity(
                recipientPhone = recipientPhone,
                messageBody = body,
                state = MessageState.HELD
            )
        )

    /** Creates a draft already in READY_TO_SEND (gate cleared it; SmsSender is being called). */
    suspend fun createOutgoingDraft(recipientPhone: String, body: String): Long =
        dao.insertDraft(
            DraftEntity(
                recipientPhone = recipientPhone,
                messageBody = body,
                state = MessageState.READY_TO_SEND
            )
        )

    /** User confirmed after a yellow pause: draft is ready to send. */
    suspend fun markReadyToSend(draft: DraftEntity) =
        dao.updateDraft(draft.copy(state = MessageState.READY_TO_SEND, updatedAt = Instant.now()))

    /** Android reported the send succeeded. */
    suspend fun markSent(draftId: Long) {
        val draft = dao.getDraftById(draftId) ?: return
        dao.updateDraft(
            draft.copy(
                state = MessageState.SENT,
                sentAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )
    }

    suspend fun discard(draft: DraftEntity) =
        dao.updateDraft(draft.copy(state = MessageState.DISCARDED, updatedAt = Instant.now()))

    suspend fun getById(draftId: Long): DraftEntity? = dao.getDraftById(draftId)

    /** Held / ready drafts for one conversation, newest first. */
    fun draftsForRecipient(phone: String): Flow<List<DraftEntity>> =
        dao.getDraftsByRecipient(phone)

    /** All active (HELD or READY_TO_SEND) drafts — the hold queue. */
    fun activeDrafts(): Flow<List<DraftEntity>> = dao.getActiveDrafts()
}
