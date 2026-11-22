package com.example.ui

import com.example.connectors.github.RepoInfo
import com.example.ui.chat.Mentions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MentionsTest {
    private fun repo(name: String) = RepoInfo(name, null, false, "main", null, null)

    @Test fun atTriggersOnWordStart_only() {
        assertEquals("oc", Mentions.detect("revisa @oc")!!.query)
        assertEquals(7, Mentions.detect("revisa @oc")!!.start)
        assertNull(Mentions.detect("escríbeme a ana@correo.com"))
        assertNull(Mentions.detect("hola "))
        assertNull(Mentions.detect(""))
    }

    @Test fun slashTriggersOnlyAtMessageStart() {
        assertEquals("oc", Mentions.detect("/oc")!!.query)
        assertNull(Mentions.detect("mira la ruta /sdcard"))
    }

    @Test fun queryWithInvalidCharsDoesNotTrigger() {
        assertNull(Mentions.detect("@ñandú"))
        assertNull(Mentions.detect("@a,b"))
    }

    @Test fun insertReplacesTheToken() {
        val text = "revisa @oc"
        assertEquals("revisa @octo/cat ", Mentions.insert(text, Mentions.detect(text)!!, "octo/cat"))
    }

    @Test fun filterRanksNameMatchesFirst() {
        val repos = listOf(repo("zed/cat-tools"), repo("octo/cat"), repo("octo/dog"))
        assertEquals(listOf("octo/cat", "zed/cat-tools"), Mentions.filter(repos, "cat").map { it.fullName })
        assertEquals(3, Mentions.filter(repos, "").size)
    }
}
