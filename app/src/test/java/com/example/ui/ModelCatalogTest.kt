package com.example.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {

    private val all = ModelCatalog.openRouter + ModelCatalog.groq

    @Test fun displayNames_neverExposeTechnicalIds() {
        all.forEach { m ->
            assertFalse("'${m.name}' contiene '/'", m.name.contains('/'))
            assertFalse("'${m.name}' contiene ':free'", m.name.contains(":free"))
            assertFalse("'${m.name}' parece un ID", m.name == m.id || m.name == m.name.lowercase())
        }
    }

    @Test fun ids_areUnique_andNamesToo() {
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertEquals(ModelCatalog.openRouter.size, ModelCatalog.openRouter.map { it.name }.toSet().size)
    }

    @Test fun defaultModel_isInTheOpenRouterList() {
        assertTrue(ModelCatalog.openRouter.any { it.id == ModelCatalog.DEFAULT_OPENROUTER })
    }

    @Test fun retiredIds_areNotSelectable() {
        assertTrue(ModelCatalog.retiredIds.none { id -> ModelCatalog.openRouter.any { it.id == id } })
    }

    @Test fun nonChatModels_areNotInTheList() {
        val bad = listOf("embed", "rerank", "recraft", "heygen", "fish-audio", "decider", "span-01")
        ModelCatalog.openRouter.forEach { m -> bad.forEach { assertFalse("${m.id} no sirve para chat", m.id.contains(it)) } }
    }

    @Test fun resolve_findsKnownModels_andSynthesizesCustomOnes() {
        assertEquals("ERNIE 5.5 Flash", ModelCatalog.resolve("OpenRouter", "baidu/ernie-5.5-flash-preview:free").name)
        val custom = ModelCatalog.resolve("OpenRouter", "nvidia/nemotron-9-test-12b:free")
        assertEquals("Nemotron 9 Test 12B", custom.name)
        assertEquals(Brand.NVIDIA, custom.brand)
        assertFalse(custom.name.contains('/'))
    }

    @Test fun prettyName_handlesSizesAndSuffixes() {
        assertEquals("Gemma 4 31B It", ModelCatalog.prettyName("google/gemma-4-31b-it:free"))
        assertEquals("Llama 3.3 70B Versatile", ModelCatalog.prettyName("llama-3.3-70b-versatile"))
    }

    @Test fun brandOfId_coversKnownProviders() {
        assertEquals(Brand.GOOGLE, ModelCatalog.brandOfId("google/gemma-4-26b-a4b-it:free"))
        assertEquals(Brand.AUTO, ModelCatalog.brandOfId("openrouter/free"))
        assertEquals(Brand.META, ModelCatalog.brandOfId("llama-3.3-70b-versatile"))
        assertEquals(Brand.GENERIC, ModelCatalog.brandOfId("algo-desconocido"))
    }

    @Test fun resolveStored_understandsCurrentAndLegacyFormats() {
        assertEquals("ERNIE 5.5 Flash", ModelCatalog.resolveStored("OpenRouter · baidu/ernie-5.5-flash-preview:free")!!.name)
        assertNotNull(ModelCatalog.resolveStored("OpenRouter - cohere/north-mini-code:free")) // formato antiguo
        assertEquals("Llama 3.3 70B", ModelCatalog.resolveStored("Groq · llama-3.3-70b-versatile")!!.name)
    }

    @Test fun resolveStored_ignoresFreeTextLabels() {
        assertNull(ModelCatalog.resolveStored("Pensando"))
        assertNull(ModelCatalog.resolveStored("Asistente"))
        assertNull(ModelCatalog.resolveStored(null))
        assertNull(ModelCatalog.resolveStored(""))
    }
}
