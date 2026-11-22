package com.example.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTextTest {
    @Test fun removesCodeBlocksAndKeepsProse() {
        val md = "Mira este código:\n```kotlin\nval x = 1\nprintln(x)\n```\nY listo."
        val out = SpeechText.clean(md)
        assertFalse(out.contains("val x"))
        assertFalse(out.contains("```"))
        assertTrue(out.contains("Mira este código:") && out.contains("Y listo."))
    }

    @Test fun unclosedFenceIsDropped() {
        assertEquals("Antes", SpeechText.clean("Antes\n```js\nconsole.log(1)"))
    }

    @Test fun stripsMarkdownSyntax() {
        val md = "# Título\n\n**negrita**, *cursiva*, ~~tachado~~ y `código`.\n\n- uno\n- dos\n\n> cita\n\n1. primero"
        val out = SpeechText.clean(md)
        listOf("#", "**", "*", "~~", "`", "> ", "- ").forEach { assertFalse("'$it' en: $out", out.contains(it)) }
        assertTrue(out.contains("Título") && out.contains("negrita") && out.contains("código") && out.contains("uno") && out.contains("1. primero"))
    }

    @Test fun linksKeepTextAndDropUrls() {
        val out = SpeechText.clean("Visita [la docs](https://example.com/a?b=1) o https://foo.bar/x ahora")
        assertTrue(out.contains("la docs"))
        assertFalse(out.contains("http"))
    }

    @Test fun tablesDontSpellPipesOrDashes() {
        val out = SpeechText.clean("| a | b |\n|---|---|\n| 1 | 2 |")
        assertFalse(out.contains("|") || out.contains("---"))
    }

    @Test fun removesEmojiAndHtml() {
        val out = SpeechText.clean("Hola 😀 <b>mundo</b> ✨")
        assertEquals("Hola mundo", out)
    }

    @Test fun longTextIsTruncatedAtAWordBoundary() {
        val out = SpeechText.clean("palabra ".repeat(1000))
        assertTrue(out.length <= SpeechText.MAX_CHARS + 1)
        assertTrue(out.endsWith("…"))
    }

    @Test fun codeOnlyMessageBecomesEmpty() {
        assertEquals("", SpeechText.clean("```\nsolo código\n```"))
    }
}
