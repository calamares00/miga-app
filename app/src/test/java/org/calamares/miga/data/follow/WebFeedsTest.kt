package org.calamares.miga.data.follow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Recipe websites: their pages, feeds (RSS and Atom) and WordPress's API (made-up data). */
class WebFeedsTest {

    @Test
    fun `a page gives the site's name, icon and feeds, comments left out`() {
        val html = """<html><head><title>Recetas de prueba | Cocina fácil</title>
            <meta property="og:site_name" content="Cocina de prueba">
            <link rel="alternate" type="application/rss+xml" title="Feed" href="https://cocina.example/feed/" />
            <link rel="alternate" type="application/rss+xml" title="Comentarios" href="https://cocina.example/comments/feed/" />
            <link rel="alternate" type="application/rss+xml" title="Recetas" href="/recetas/feed/" />
            <link rel="apple-touch-icon" href="/icon-180.png"></head></html>"""
        val site = WebFeeds.readSite(html, "https://cocina.example/recetas/")
        assertEquals("Cocina de prueba", site.name)
        assertEquals("https://cocina.example/icon-180.png", site.iconUrl)
        assertEquals(listOf("https://cocina.example/feed/", "https://cocina.example/recetas/feed/"), site.feeds)
        assertEquals("the section's own feed", "https://cocina.example/recetas/feed/", WebFeeds.chooseFeed(site.feeds, "https://cocina.example/recetas/"))
        assertEquals("https://cocina.example/feed/", WebFeeds.chooseFeed(site.feeds, "https://cocina.example/"))
        assertEquals("Recetas de prueba", WebFeeds.readSite("<title>Recetas de prueba | Cocina</title>", "https://x.example/").name)
    }

    private val rss = """<?xml version="1.0" encoding="UTF-8"?><rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/" xmlns:media="http://search.yahoo.com/mrss/"><channel>
        <item><title>Sopa de pollo &amp; jengibre</title><link>https://cocina.example/sopa-de-pollo/</link><pubDate>Tue, 10 Nov 2026 10:00:00 +0000</pubDate>
          <description><![CDATA[<p>Una sopa <b>reconfortante</b> para el frío.</p>]]></description></item>
        <item><title>Magdalenas</title><link>https://cocina.example/magdalenas/?utm=rss</link><pubDate>Wed, 07 Oct 2026 10:00:00 +0000</pubDate>
          <media:content url="https://cocina.example/m.jpg" medium="image"/><description>Esponjosas</description></item>
        <item><title>Tarta</title><link>https://cocina.example/tarta/</link><pubDate>Mon, 05 Oct 2026 10:00:00 +0000</pubDate>
          <content:encoded><![CDATA[<p><img src="https://cocina.example/t.jpg" alt=""> Tarta de queso</p>]]></content:encoded></item>
        </channel></rss>"""

    @Test
    fun `an rss feed gives the posts with their pictures, newest first`() {
        val posts = WebFeeds.readFeed(rss, "https://cocina.example/feed/")
        assertEquals(listOf("Sopa de pollo & jengibre", "Magdalenas", "Tarta"), posts.map { it.title })
        assertEquals("Una sopa reconfortante para el frío.", posts[0].description)
        assertNull(posts[0].thumbnailUrl)
        assertEquals("https://cocina.example/m.jpg", posts[1].thumbnailUrl)
        assertEquals("https://cocina.example/t.jpg", posts[2].thumbnailUrl)
        assertEquals("https://cocina.example/magdalenas", posts[1].postKey)
        assertEquals(PostKind.ARTICLE, posts[1].kind)
        assertTrue(posts[0].publishedAt > posts[1].publishedAt)
    }

    @Test
    fun `a badly written feed is still read`() {
        val broken = rss.replace("Esponjosas", "Esponjosas & ricas \u0001").replace("<title>Tarta</title>", "<title>Tarta R&D</title>")
        assertEquals(3, WebFeeds.readFeed(broken, "https://cocina.example/feed/").size)
        val doubled = rss.replace("""<media:content url="https://cocina.example/m.jpg" medium="image"/>""", """<media:content url="https://cocina.example/m.jpg" width=""1199" medium="image"/>""")
        assertEquals("https://cocina.example/m.jpg", WebFeeds.readFeed(doubled, "https://cocina.example/feed/")[1].thumbnailUrl)
        // Broken beyond repair for an XML parser: read entry by entry.
        val unclosed = rss.replace("<channel>", "<channel><bad>")
        assertEquals(listOf("Sopa de pollo & jengibre", "Magdalenas", "Tarta"), WebFeeds.readFeed(unclosed, "https://cocina.example/feed/").map { it.title })
        assertTrue(WebFeeds.readFeed("<html>not a feed</html>", "https://x.example/").isEmpty())
    }

    @Test
    fun `an atom feed works too`() {
        val atom = """<feed xmlns="http://www.w3.org/2005/Atom"><entry><title>Pan de molde</title>
            <link rel="alternate" href="https://pan.example/pan-de-molde"/><published>2026-11-01T08:00:00Z</published>
            <summary>Esponjoso</summary></entry></feed>"""
        val post = WebFeeds.readFeed(atom, "https://pan.example/atom.xml").single()
        assertEquals("https://pan.example/pan-de-molde", post.url)
        assertEquals("Esponjoso", post.description)
    }

    @Test
    fun `wordpress posts, pictures, categories and addresses`() {
        val posts = WebFeeds.readWpPosts("""[{"link":"https://cocina.example/sopa/","title":{"rendered":"Sopa &#8211; f&aacute;cil"},
            "excerpt":{"rendered":"<p>Una sopa rica [&hellip;]</p>"},"date_gmt":"2026-11-10T10:00:00","featured_media":7},
            {"link":"https://cocina.example/pan/","title":{"rendered":"Pan"},"excerpt":{"rendered":""},"date_gmt":"2026-11-01T10:00:00","featured_media":0}]""")!!
        assertEquals("Sopa – fácil", posts[0].title)
        assertEquals("Una sopa rica", posts[0].excerpt)
        val media = WebFeeds.readWpMedia("""[{"id":7,"source_url":"https://cocina.example/big.jpg","media_details":{"sizes":{"medium_large":{"source_url":"https://cocina.example/768.jpg"}}}}]""")
        val feed = WebFeeds.toFeedPosts(posts, media)
        assertEquals("https://cocina.example/768.jpg", feed[0].thumbnailUrl)
        assertNull(feed[1].thumbnailUrl)
        assertNull("not WordPress", WebFeeds.readWpPosts("<html>404</html>"))
        assertEquals(12L, WebFeeds.readWpCategory("""[{"id":9,"count":0},{"id":12,"count":40}]"""))
        assertNull(WebFeeds.readWpCategory("""[{"id":9,"count":0}]"""))
        assertEquals("https://cocina.example/wp-json/wp/v2/", WebFeeds.wpApiOf("https://cocina.example/recetas/"))
        assertEquals("https://cocina.example/feed/?paged=3", WebFeeds.pagedFeedUrl("https://cocina.example/feed/", 3))
        assertTrue(WebFeeds.wpPostsUrl("https://cocina.example/wp-json/wp/v2/", 12, 2, "lentejas rojas").contains("page=2&_fields=link,title,excerpt,date_gmt,featured_media&categories=12&search=lentejas+rojas"))
        assertTrue(WebFeeds.wpPostsBySlugUrl("https://c.example/wp-json/wp/v2/", listOf("https://c.example/sopa-de-pollo/", "https://c.example/pan")).endsWith("slug=sopa-de-pollo,pan"))
    }

    @Test
    fun `any other page is a website`() {
        assertNull(SocialFeeds.parse("https://cocinaconcoqui.com/recetas/"))
        assertTrue(SocialLink.Website("https://cocinaconcoqui.com/recetas/").isSource)
    }
}
