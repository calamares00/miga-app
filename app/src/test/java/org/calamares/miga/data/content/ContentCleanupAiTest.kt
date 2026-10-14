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
}
