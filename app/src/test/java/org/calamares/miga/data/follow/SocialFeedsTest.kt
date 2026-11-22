package org.calamares.miga.data.follow

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Links, channel pages, feeds and profile pages of YouTube and Instagram (made-up data). */
class SocialFeedsTest {

    @Test
    fun `what each link is`() {
        assertEquals(SocialLink.YouTubeChannel("https://www.youtube.com/@cocinadeprueba"), SocialFeeds.parse("https://youtube.com/@cocinadeprueba/videos?si=x"))
        assertEquals(
            SocialLink.YouTubeChannel("https://www.youtube.com/channel/UCtfcIH9J6eC3p-4WwXVAATQ"),
            SocialFeeds.parse("https://m.youtube.com/channel/UCtfcIH9J6eC3p-4WwXVAATQ/featured")
        )
        assertEquals(SocialLink.YouTubeVideo("https://www.youtube.com/watch?v=AbC-123_xyZ", "AbC-123_xyZ"), SocialFeeds.parse("https://youtu.be/AbC-123_xyZ"))
        assertEquals(SocialLink.InstagramProfile("https://www.instagram.com/cocina.de_prueba/", "cocina.de_prueba"), SocialFeeds.parse("https://www.instagram.com/cocina.de_prueba?igsh=abc"))
        assertEquals("DffETULIUTE", (SocialFeeds.parse("https://www.instagram.com/reel/DffETULIUTE/?x=1") as SocialLink.InstagramPost).shortcode)
        assertNull(SocialFeeds.parse("https://www.instagram.com/explore/"))
        assertNull(SocialFeeds.parse("https://www.youtube.com/results?search_query=x"))
        assertNull(SocialFeeds.parse("https://www.example.com/@x"))
        assertTrue(SocialFeeds.parse("https://www.instagram.com/x/")!!.isSource)
    }

    @Test
    fun `a channel's page gives its id, handle, name and picture`() {
        val html = """<html><head><meta property="og:title" content="La cocina de prueba"><meta property="og:image" content="https://yt3.example/a.jpg">
            <link rel="canonical" href="https://www.youtube.com/channel/UCtfcIH9J6eC3p-4WwXVAATQ"></head>
            <script>var ytInitialData = {"metadata":{"channelMetadataRenderer":{"externalId":"UCtfcIH9J6eC3p-4WwXVAATQ","vanityChannelUrl":"http://www.youtube.com/@cocinadeprueba"}}};</script>"""
        val info = SocialFeeds.readYouTubeChannel(html)!!
        assertEquals("UCtfcIH9J6eC3p-4WwXVAATQ", info.key)
        assertEquals("@cocinadeprueba", info.handle)
        assertEquals("La cocina de prueba", info.name)
        assertEquals("https://www.youtube.com/@cocinadeprueba", info.url)
        assertNull(SocialFeeds.readYouTubeChannel("<html>consent</html>"))
    }

    @Test
    fun `a channel's feed gives its videos, newest first, with Shorts marked`() {
        fun entry(id: String, date: String, title: String, link: String) = """<entry><yt:videoId>$id</yt:videoId><title>$title</title>
            <link rel="alternate" href="$link"/><published>$date</published>
            <media:group><media:title>$title</media:title><media:thumbnail url="https://i1.ytimg.com/vi/$id/hqdefault.jpg" width="480" height="360"/>
            <media:description>INGREDIENTES: 2 huevos &amp; sal</media:description></media:group></entry>"""
        val xml = """<?xml version="1.0" encoding="UTF-8"?><feed xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns:media="http://search.yahoo.com/mrss/" xmlns="http://www.w3.org/2005/Atom">
            <title>La cocina de prueba</title>
            ${entry("AAAAAAAAAAA", "2026-11-19T10:00:00+00:00", "Tortilla", "https://www.youtube.com/watch?v=AAAAAAAAAAA")}
            ${entry("BBBBBBBBBBB", "2026-11-20T10:00:00+00:00", "Flan en 30 segundos", "https://www.youtube.com/shorts/BBBBBBBBBBB")}
            </feed>"""
        val posts = SocialFeeds.readYouTubeFeed(xml)
        assertEquals(listOf("BBBBBBBBBBB", "AAAAAAAAAAA"), posts.map { it.postKey })
        assertEquals(PostKind.SHORT, posts[0].kind)
        assertEquals(PostKind.VIDEO, posts[1].kind)
        assertEquals("https://www.youtube.com/watch?v=AAAAAAAAAAA", posts[1].url)
        assertEquals("INGREDIENTES: 2 huevos & sal", posts[1].description)
        assertEquals("https://i1.ytimg.com/vi/AAAAAAAAAAA/hqdefault.jpg", posts[1].thumbnailUrl)
        assertTrue(SocialFeeds.readYouTubeFeed("not xml").isEmpty())
    }

    @Test
    fun `a profile's embed page gives the profile and its latest posts`() {
        fun media(code: String, video: Boolean, at: Long, caption: String) =
            """{"shortcode_media":{"shortcode":"$code","__typename":"${if (video) "GraphVideo" else "GraphImage"}","taken_at_timestamp":$at,
            "display_url":"https://cdn.example/$code.jpg","edge_media_to_caption":{"edges":[{"node":{"text":${Json.encodeToString(caption)}}}]}}}"""
        val context = """{"context":{"username":"cocinadeprueba","full_name":"Cocina de prueba","profile_pic_url":"https://cdn.example/me.jpg",
            "graphql_media":[${media("Old1", false, 1000, "Pesto en mortero\nCon albahaca")},${media("New2", true, 2000, "🍝 Carbonara de verdad 🍝\n\nINGREDIENTES…\n#pasta")}]}}"""
        val html = """<script>s.handle({"contextJSON":${Json.encodeToString(context)}});</script>"""
        val (info, posts) = SocialFeeds.readInstagramProfile(html)!!
        assertEquals("cocinadeprueba", info.key)
        assertEquals("@cocinadeprueba", info.handle)
        assertEquals("Cocina de prueba", info.name)
        assertEquals(listOf("New2", "Old1"), posts.map { it.postKey })
        assertEquals(PostKind.REEL, posts[0].kind)
        assertEquals("https://www.instagram.com/reel/New2/", posts[0].url)
        assertEquals("https://www.instagram.com/p/Old1/", posts[1].url)
        assertEquals("🍝 Carbonara de verdad 🍝", posts[0].title)
        assertEquals("INGREDIENTES…", posts[0].description)
        assertEquals(2_000_000L, posts[0].publishedAt)
        assertNull(SocialFeeds.readInstagramProfile("<html>login</html>"))
        assertEquals("Sorbete de limón" to "Para Navidad", SocialFeeds.titleAndText("#publi #ad Sorbete de limón\nPara Navidad\n#postres"))
    }
}
