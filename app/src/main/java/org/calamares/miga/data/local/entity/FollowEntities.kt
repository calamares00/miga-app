package org.calamares.miga.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A YouTube channel or an Instagram profile the user follows in Favourites. [network] is a
 * SocialNetwork name; [key] identifies it there (the channel id "UC…", or the username).
 */
@Entity(tableName = "followed_sources", indices = [Index(value = ["network", "key"], unique = true)])
data class FollowedSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val network: String,
    val key: String,
    /** "@lacocinadelila", when known. */
    val handle: String?,
    val name: String,
    val avatarUrl: String?,
    /** The channel's or profile's page. */
    val url: String,
    val addedAt: Long,
    /** When the user last opened it: posts published later are "new". */
    val lastOpenedAt: Long,
    /** When its latest posts were last downloaded; null before the first time. */
    val checkedAt: Long? = null,
    /**
     * For a website made with WordPress: its public API ("https://site/wp-json/wp/v2/"), for more
     * recipes, pictures and searching; null for other websites and for channels and profiles.
     */
    val wpApi: String? = null,
    /** The WordPress category followed (a section such as "Recetas"); null for the whole site. */
    val wpCategory: Long? = null
)

/**
 * One of the latest posts of a followed source, as last downloaded: kept to show them offline and
 * to tell which are new. Replaced on every refresh.
 */
@Entity(
    tableName = "source_posts",
    foreignKeys = [ForeignKey(entity = FollowedSourceEntity::class, parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["sourceId", "postKey"], unique = true)]
)
data class SourcePostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    /** The video id or the post's shortcode. */
    val postKey: String,
    val url: String,
    val title: String,
    val description: String,
    val thumbnailUrl: String?,
    val publishedAt: Long,
    /** A PostKind name. */
    val kind: String
)

/** A video or reel saved in Favourites to cook or import later; it stays when it leaves the feed. */
@Entity(tableName = "saved_posts", indices = [Index(value = ["network", "postKey"], unique = true)])
data class SavedPostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val network: String,
    val postKey: String,
    val url: String,
    val title: String,
    val description: String,
    val thumbnailUrl: String?,
    val author: String?,
    val authorUrl: String?,
    val kind: String,
    val savedAt: Long
)
