package org.calamares.miga.data.content

import org.calamares.miga.data.model.ContentItem
import org.calamares.miga.data.model.ContentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentCleanupAiTest {

    private val dulces = ContentItem(ContentKind.CATEGORY, 1, "Dulces caseros", 3)
    private val postres = ContentItem(ContentKind.CATEGORY, 2, "Postres", 9) // comes with the app
    private val vegi = ContentItem(ContentKind.TAG, 3, "vegi", 2)
    private val vegetariano = ContentItem(ContentKind.TAG, 4, "vegetariano", 5)
    private val tipo = ContentItem(ContentKind.TAG, 5, "rapdio", 1)
    private val content = mapOf(ContentKind.CATEGORY to listOf(dulces, postres), ContentKind.TAG to listOf(vegi, vegetariano, tipo))

    @Test
    fun `answers become unticked proposals on real entries`() {
        val dto = AiCleanupDto(
            merges = listOf(
                AiMergeDto("CATEGORY", listOf("Dulces caseros", "postres"), into = "Postres"),
                AiMergeDto("CATEGORY", listOf("Inventada", "Postres"), into = "Inventada")
            ),
            renames = listOf(
                AiRenameDto("TAG", "rapdio", "rápido"),
                AiRenameDto("TAG", "vegi", "Vegetariano"),
                AiRenameDto("CATEGORY", "Postres", "Dulces")
            )
        )
        val proposals = dto.toProposals(content, alreadyProposed = emptyList())

        val merge = proposals.first()
        assertEquals(CleanupAction.MERGE, merge.action)
        assertEquals(postres, merge.target)
        assertEquals(listOf(postres, dulces), merge.items)
        // Renaming into an existing tag merges both; a built-in entry is never renamed.
        assertEquals(listOf(CleanupAction.MERGE, CleanupAction.RENAME, CleanupAction.MERGE), proposals.map { it.action })
        assertEquals(listOf(vegetariano, vegi), proposals[2].items)
        assertEquals("rápido", proposals[1].newName)
        assertTrue(proposals.all { it.fromAi && !it.recommended })
    }

    @Test
    fun `entries other proposals already handle are left alone`() {
        val existing = CleanupProposal(ContentKind.TAG, CleanupAction.DELETE, listOf(tipo))
        val dto = AiCleanupDto(renames = listOf(AiRenameDto("TAG", "rapdio", "rápido")))
        assertTrue(dto.toProposals(content, listOf(existing)).isEmpty())
    }

    @Test
    fun `the work goes in small requests, and one cut off is retried in halves`() = kotlinx.coroutines.runBlocking {
        val tags = (1..130).map { ContentItem(ContentKind.TAG, 100L + it, "etiqueta $it", 1) }
        val lists = mapOf(ContentKind.TAG to tags)
        assertEquals(listOf(60, 60, 10), cleanupBatches(lists, emptyList()).map { it.size })

        val sizes = mutableListOf<Int>()
        // A model that cuts off any answer about more than 40 entries.
        val result = suggestContentCleanup(lists, "es", emptyList()) { request ->
            val count = Regex("etiqueta \\d+").findAll(request.prompt).count()
            sizes += count
            if (count > 40) org.calamares.miga.data.ai.AiText.Error("cut off")
            else org.calamares.miga.data.ai.AiText.Success("""{"merges": [], "renames": []}""")
        }
        assertTrue(result is AiCleanupResult.Success)
        assertTrue(!(result as AiCleanupResult.Success).incomplete)
        assertEquals(listOf(60, 30, 30, 60, 30, 30, 10), sizes)
    }

    @Test
    fun `nothing of the user's means no request`() = kotlinx.coroutines.runBlocking {
        val result = suggestContentCleanup(mapOf(ContentKind.CATEGORY to listOf(postres)), "es", emptyList()) {
            error("no request expected")
        }
        assertEquals(AiCleanupResult.Success(emptyList()), result)
    }

    @Test
    fun `when every request fails the error says why`() = kotlinx.coroutines.runBlocking {
        val result = suggestContentCleanup(content, "es", emptyList()) { org.calamares.miga.data.ai.AiText.Error("sin conexión") }
        assertEquals(AiCleanupResult.Error("sin conexión"), result)
    }
}
