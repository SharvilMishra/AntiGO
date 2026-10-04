package com.sharvil.antigo.ui.viewmodel

import com.sharvil.antigo.domain.model.Conversation
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationFilterTest {
    @Test fun filtersByTitleWithoutCaseSensitivity() {
        val rows = listOf(Conversation("1", "Example"), Conversation("2", "Other"))
        assertEquals(listOf(rows.first()), filterConversations(rows, "EXAM"))
    }
}
