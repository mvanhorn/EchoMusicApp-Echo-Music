package echo.music.iad1tya.lyrics

import echo.music.iad1tya.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import echo.music.iad1tya.models.MediaMetadata
import echo.music.iad1tya.utils.NetworkConnectivityObserver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LyricsHelperTest {

  private lateinit var networkConnectivity: NetworkConnectivityObserver
  private lateinit var provider: LyricsProvider
  private lateinit var helper: LyricsHelper

  @Before
  fun setUp() {
    networkConnectivity = mockk()
    every { networkConnectivity.isCurrentlyConnected() } returns true

    provider = mockk()
    every { provider.name } returns PROVIDER_NAME
    every { provider.isEnabled(any()) } returns true
    stubSearchLyrics { id, title, artist, duration, album ->
      lyricsFor(id, title, artist, duration, album)
    }
    stubAutomaticLyrics { id, _, _, _, _ -> Result.success("auto|$id") }

    mockkObject(LyricsProviderRegistry)
    every { LyricsProviderRegistry.getDefaultProviderOrder() } returns listOf(PROVIDER_NAME)
    every { LyricsProviderRegistry.getProviderByName(PROVIDER_NAME) } returns provider
    assertEquals(listOf(PROVIDER_NAME), LyricsProviderRegistry.getDefaultProviderOrder())
    assertSame(provider, LyricsProviderRegistry.getProviderByName(PROVIDER_NAME))

    helper = LyricsHelper(RuntimeEnvironment.getApplication(), networkConnectivity)
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun differentMediaIdsWithSameTitleArtistReturnOwnLyrics_spanishThenEnglish() = runBlocking {
    val spanish = search(SPANISH_MEDIA_ID)
    val english = search(ENGLISH_MEDIA_ID)

    assertEquals(listOf(LyricsResult(PROVIDER_NAME, SPANISH_LYRICS)), spanish)
    assertEquals(listOf(LyricsResult(PROVIDER_NAME, ENGLISH_LYRICS)), english)

    assertEquals(spanish, search(SPANISH_MEDIA_ID))
    assertEquals(english, search(ENGLISH_MEDIA_ID))
    verifySearchCount(SPANISH_MEDIA_ID, 1)
    verifySearchCount(ENGLISH_MEDIA_ID, 1)
  }

  @Test
  fun differentMediaIdsWithSameTitleArtistReturnOwnLyrics_englishThenSpanish() = runBlocking {
    val english = search(ENGLISH_MEDIA_ID)
    val spanish = search(SPANISH_MEDIA_ID)

    assertEquals(listOf(LyricsResult(PROVIDER_NAME, ENGLISH_LYRICS)), english)
    assertEquals(listOf(LyricsResult(PROVIDER_NAME, SPANISH_LYRICS)), spanish)

    assertEquals(english, search(ENGLISH_MEDIA_ID))
    assertEquals(spanish, search(SPANISH_MEDIA_ID))
    verifySearchCount(ENGLISH_MEDIA_ID, 1)
    verifySearchCount(SPANISH_MEDIA_ID, 1)
  }

  @Test
  fun editedTitleMissesCacheAndIdenticalRepeatHits() = runBlocking {
    val original = search(mediaId = "id-1", title = "Original Title")
    val edited = search(mediaId = "id-1", title = "Edited Title")

    assertEquals(listOf(LyricsResult(PROVIDER_NAME, lyricsFor("id-1", "Original Title"))), original)
    assertEquals(listOf(LyricsResult(PROVIDER_NAME, lyricsFor("id-1", "Edited Title"))), edited)
    assertEquals(original, search(mediaId = "id-1", title = "Original Title"))
    assertEquals(edited, search(mediaId = "id-1", title = "Edited Title"))
    coVerify(exactly = 1) {
      provider.getAllLyrics("id-1", "Original Title", SHARED_ARTIST, DURATION, ALBUM, any())
    }
    coVerify(exactly = 1) {
      provider.getAllLyrics("id-1", "Edited Title", SHARED_ARTIST, DURATION, ALBUM, any())
    }
  }

  @Test
  fun editedArtistMissesCacheAndIdenticalRepeatHits() = runBlocking {
    val original = search(mediaId = "id-1", artist = "Original Artist")
    val edited = search(mediaId = "id-1", artist = "Edited Artist")

    assertEquals(
      listOf(LyricsResult(PROVIDER_NAME, lyricsFor("id-1", artist = "Original Artist"))),
      original,
    )
    assertEquals(
      listOf(LyricsResult(PROVIDER_NAME, lyricsFor("id-1", artist = "Edited Artist"))),
      edited,
    )
    assertEquals(original, search(mediaId = "id-1", artist = "Original Artist"))
    assertEquals(edited, search(mediaId = "id-1", artist = "Edited Artist"))
    coVerify(exactly = 1) {
      provider.getAllLyrics("id-1", SHARED_TITLE, "Original Artist", DURATION, ALBUM, any())
    }
    coVerify(exactly = 1) {
      provider.getAllLyrics("id-1", SHARED_TITLE, "Edited Artist", DURATION, ALBUM, any())
    }
  }

  @Test
  fun changedDurationMissesCacheAndIdenticalRepeatHits() = runBlocking {
    val original = search(mediaId = "id-1", duration = 180)
    val edited = search(mediaId = "id-1", duration = 240)

    assertEquals(listOf(LyricsResult(PROVIDER_NAME, lyricsFor("id-1", duration = 180))), original)
    assertEquals(listOf(LyricsResult(PROVIDER_NAME, lyricsFor("id-1", duration = 240))), edited)
    assertEquals(original, search(mediaId = "id-1", duration = 180))
    assertEquals(edited, search(mediaId = "id-1", duration = 240))
    coVerify(exactly = 1) {
      provider.getAllLyrics("id-1", SHARED_TITLE, SHARED_ARTIST, 180, ALBUM, any())
    }
    coVerify(exactly = 1) {
      provider.getAllLyrics("id-1", SHARED_TITLE, SHARED_ARTIST, 240, ALBUM, any())
    }
  }

  @Test
  fun changedAlbumMissesCacheAndIdenticalRepeatHits() = runBlocking {
    val original = search(mediaId = "id-1", album = "Album One")
    val edited = search(mediaId = "id-1", album = "Album Two")

    assertEquals(
      listOf(LyricsResult(PROVIDER_NAME, lyricsFor("id-1", album = "Album One"))),
      original,
    )
    assertEquals(
      listOf(LyricsResult(PROVIDER_NAME, lyricsFor("id-1", album = "Album Two"))),
      edited,
    )
    assertEquals(original, search(mediaId = "id-1", album = "Album One"))
    assertEquals(edited, search(mediaId = "id-1", album = "Album Two"))
    coVerify(exactly = 1) {
      provider.getAllLyrics("id-1", SHARED_TITLE, SHARED_ARTIST, DURATION, "Album One", any())
    }
    coVerify(exactly = 1) {
      provider.getAllLyrics("id-1", SHARED_TITLE, SHARED_ARTIST, DURATION, "Album Two", any())
    }
  }

  @Test
  fun nullAlbumVersusEmptyAlbumMissCache() = runBlocking {
    val missing = search(mediaId = "id-1", album = null)
    val empty = search(mediaId = "id-1", album = "")

    assertEquals(listOf(LyricsResult(PROVIDER_NAME, lyricsFor("id-1", album = null))), missing)
    assertEquals(listOf(LyricsResult(PROVIDER_NAME, lyricsFor("id-1", album = ""))), empty)
    assertEquals(missing, search(mediaId = "id-1", album = null))
    assertEquals(empty, search(mediaId = "id-1", album = ""))
    coVerify(exactly = 1) {
      provider.getAllLyrics("id-1", SHARED_TITLE, SHARED_ARTIST, DURATION, isNull(), any())
    }
    coVerify(exactly = 1) {
      provider.getAllLyrics("id-1", SHARED_TITLE, SHARED_ARTIST, DURATION, "", any())
    }
  }

  @Test
  fun spaceStrippedArtistTitlePairsStayDistinct() = runBlocking {
    val spaced = search(mediaId = "id-space", title = "Blinding Lights", artist = "The Weeknd")
    val concatenated = search(mediaId = "id-concat", title = "BlindingLights", artist = "TheWeeknd")

    assertEquals(
      listOf(LyricsResult(PROVIDER_NAME, lyricsFor("id-space", "Blinding Lights", "The Weeknd"))),
      spaced,
    )
    assertEquals(
      listOf(LyricsResult(PROVIDER_NAME, lyricsFor("id-concat", "BlindingLights", "TheWeeknd"))),
      concatenated,
    )
    assertEquals(
      spaced,
      search(mediaId = "id-space", title = "Blinding Lights", artist = "The Weeknd"),
    )
    assertEquals(
      concatenated,
      search(mediaId = "id-concat", title = "BlindingLights", artist = "TheWeeknd"),
    )
    coVerify(exactly = 1) {
      provider.getAllLyrics("id-space", "Blinding Lights", "The Weeknd", DURATION, ALBUM, any())
    }
    coVerify(exactly = 1) {
      provider.getAllLyrics("id-concat", "BlindingLights", "TheWeeknd", DURATION, ALBUM, any())
    }
  }

  @Test
  fun hyphenConcatenatedArtistTitlePairsStayDistinct() = runBlocking {
    val titleHyphen = search(mediaId = "id-title", title = "B-C", artist = "A")
    val artistHyphen = search(mediaId = "id-artist", title = "C", artist = "A-B")

    assertEquals(
      listOf(LyricsResult(PROVIDER_NAME, lyricsFor("id-title", "B-C", "A"))),
      titleHyphen,
    )
    assertEquals(
      listOf(LyricsResult(PROVIDER_NAME, lyricsFor("id-artist", "C", "A-B"))),
      artistHyphen,
    )
    assertEquals(titleHyphen, search(mediaId = "id-title", title = "B-C", artist = "A"))
    assertEquals(artistHyphen, search(mediaId = "id-artist", title = "C", artist = "A-B"))
    coVerify(exactly = 1) { provider.getAllLyrics("id-title", "B-C", "A", DURATION, ALBUM, any()) }
    coVerify(exactly = 1) { provider.getAllLyrics("id-artist", "C", "A-B", DURATION, ALBUM, any()) }
  }

  @Test
  fun threeEntryLruEvictsLeastRecentlyUsedSearch() = runBlocking {
    search(mediaId = "id-a")
    search(mediaId = "id-b")
    search(mediaId = "id-c")
    verifySearchCount("id-a", 1)
    verifySearchCount("id-b", 1)
    verifySearchCount("id-c", 1)

    search(mediaId = "id-d")
    verifySearchCount("id-d", 1)
    search(mediaId = "id-b")
    verifySearchCount("id-b", 1)
    search(mediaId = "id-c")
    verifySearchCount("id-c", 1)

    search(mediaId = "id-a")
    verifySearchCount("id-a", 2)
  }

  @Test
  fun cachedSearchResultsRemainAvailableOfflineForExactRequest() = runBlocking {
    val online = search(SPANISH_MEDIA_ID)
    every { networkConnectivity.isCurrentlyConnected() } returns false

    val offlineRepeat = search(SPANISH_MEDIA_ID)
    assertEquals(online, offlineRepeat)
    assertEquals(listOf(LyricsResult(PROVIDER_NAME, SPANISH_LYRICS)), offlineRepeat)
    verifySearchCount(SPANISH_MEDIA_ID, 1)
  }

  @Test
  fun offlineSearchForAnotherRecordingYieldsNoCallback() = runBlocking {
    search(SPANISH_MEDIA_ID)
    every { networkConnectivity.isCurrentlyConnected() } returns false

    val offlineOther = search(ENGLISH_MEDIA_ID)
    assertTrue(offlineOther.isEmpty())
    verifySearchCount(ENGLISH_MEDIA_ID, 0)
    verifySearchCount(SPANISH_MEDIA_ID, 1)
  }

  @Test
  fun getLyricsDoesNotConsumeSearchCandidatesCache() = runBlocking {
    val searched = search(SPANISH_MEDIA_ID)
    assertEquals(listOf(LyricsResult(PROVIDER_NAME, SPANISH_LYRICS)), searched)

    val automatic = helper.getLyrics(mediaMetadata(SPANISH_MEDIA_ID))
    assertEquals("auto|$SPANISH_MEDIA_ID", automatic.lyrics)
    assertEquals(PROVIDER_NAME, automatic.providerName)

    assertEquals(searched, search(SPANISH_MEDIA_ID))
    verifySearchCount(SPANISH_MEDIA_ID, 1)
    coVerify(exactly = 1) {
      provider.getLyrics(SPANISH_MEDIA_ID, SHARED_TITLE, SHARED_ARTIST, DURATION, ALBUM)
    }
  }

  @Test
  fun getLyricsProviderFailureDoesNotEmitCachedSearchLyrics() = runBlocking {
    search(SPANISH_MEDIA_ID)
    stubAutomaticLyrics { _, _, _, _, _ -> Result.failure(IllegalStateException("no lyrics")) }

    val automatic = helper.getLyrics(mediaMetadata(SPANISH_MEDIA_ID))
    assertEquals(LYRICS_NOT_FOUND, automatic.lyrics)
    assertEquals("Unknown", automatic.providerName)
    verifySearchCount(SPANISH_MEDIA_ID, 1)
  }

  @Test
  fun providerFailureDoesNotEmitAnotherRecordingsCachedLyrics() = runBlocking {
    val spanish = search(SPANISH_MEDIA_ID)
    stubSearchLyrics { id, title, artist, duration, album ->
      if (id == ENGLISH_MEDIA_ID) {
        throw IllegalStateException("provider failed")
      }
      lyricsFor(id, title, artist, duration, album)
    }

    val failed = search(ENGLISH_MEDIA_ID)
    assertTrue(failed.isEmpty())
    assertEquals(spanish, search(SPANISH_MEDIA_ID))
    verifySearchCount(SPANISH_MEDIA_ID, 1)
    verifySearchCount(ENGLISH_MEDIA_ID, 1)
  }

  private suspend fun search(
    mediaId: String,
    title: String = SHARED_TITLE,
    artist: String = SHARED_ARTIST,
    duration: Int = DURATION,
    album: String? = ALBUM,
  ): List<LyricsResult> {
    val results = mutableListOf<LyricsResult>()
    helper.getAllLyrics(mediaId, title, artist, duration, album) { results += it }
    return results.toList()
  }

  private fun verifySearchCount(mediaId: String, exactly: Int) {
    coVerify(exactly = exactly) {
      provider.getAllLyrics(mediaId, any(), any(), any(), matchNullable { true }, any())
    }
  }

  private fun stubSearchLyrics(
    lyrics: (id: String, title: String, artist: String, duration: Int, album: String?) -> String
  ) {
    coEvery {
      provider.getAllLyrics(any(), any(), any(), any(), matchNullable { true }, any())
    } coAnswers
      {
        val id = invocation.args[0] as String
        val title = invocation.args[1] as String
        val artist = invocation.args[2] as String
        val duration = invocation.args[3] as Int
        val album = invocation.args[4] as String?
        @Suppress("UNCHECKED_CAST") val callback = invocation.args[5] as (String) -> Unit
        callback(lyrics(id, title, artist, duration, album))
      }
  }

  private fun stubAutomaticLyrics(
    result:
      (id: String, title: String, artist: String, duration: Int, album: String?) -> Result<String>
  ) {
    coEvery { provider.getLyrics(any(), any(), any(), any(), matchNullable { true }) } coAnswers
      {
        result(
          invocation.args[0] as String,
          invocation.args[1] as String,
          invocation.args[2] as String,
          invocation.args[3] as Int,
          invocation.args[4] as String?,
        )
      }
  }

  private fun mediaMetadata(mediaId: String): MediaMetadata =
    MediaMetadata(
      id = mediaId,
      title = SHARED_TITLE,
      artists = listOf(MediaMetadata.Artist(id = null, name = SHARED_ARTIST)),
      duration = DURATION,
      album = MediaMetadata.Album(id = "album-id", title = ALBUM),
    )

  private companion object {
    const val PROVIDER_NAME = "TestProvider"
    const val SHARED_TITLE = "Despacito"
    const val SHARED_ARTIST = "Luis Fonsi"
    const val DURATION = 228
    const val ALBUM = "Vida"
    const val SPANISH_MEDIA_ID = "spanish-recording-id"
    const val ENGLISH_MEDIA_ID = "english-recording-id"
    const val SPANISH_LYRICS = "¡Sí, sabes que ya llevo un rato mirándote!"
    const val ENGLISH_LYRICS = "Yes, you know that I've been looking at you for a while!"

    fun lyricsFor(
      mediaId: String,
      title: String = SHARED_TITLE,
      artist: String = SHARED_ARTIST,
      duration: Int = DURATION,
      album: String? = ALBUM,
    ): String =
      when (mediaId) {
        SPANISH_MEDIA_ID -> SPANISH_LYRICS
        ENGLISH_MEDIA_ID -> ENGLISH_LYRICS
        else -> "lyrics|$mediaId|$title|$artist|$duration|${album ?: "null"}"
      }
  }
}
