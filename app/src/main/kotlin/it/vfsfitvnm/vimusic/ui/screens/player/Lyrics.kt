package it.vfsfitvnm.vimusic.ui.screens.player

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.LocalPlayerServiceBinder
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.models.Lyrics
import it.vfsfitvnm.vimusic.preferences.PlayerPreferences
import it.vfsfitvnm.vimusic.query
import it.vfsfitvnm.vimusic.service.LOCAL_KEY_PREFIX
import it.vfsfitvnm.vimusic.transaction
import it.vfsfitvnm.vimusic.ui.components.LocalMenuState
import it.vfsfitvnm.vimusic.ui.components.themed.CircularProgressIndicator
import it.vfsfitvnm.vimusic.ui.components.themed.DefaultDialog
import it.vfsfitvnm.vimusic.ui.components.themed.Menu
import it.vfsfitvnm.vimusic.ui.components.themed.MenuEntry
import it.vfsfitvnm.vimusic.ui.components.themed.TextField
import it.vfsfitvnm.vimusic.ui.components.themed.TextFieldDialog
import it.vfsfitvnm.vimusic.ui.components.themed.TextPlaceholder
import it.vfsfitvnm.vimusic.ui.components.themed.ValueSelectorDialogBody
import it.vfsfitvnm.vimusic.ui.modifiers.verticalFadingEdge
import it.vfsfitvnm.vimusic.utils.SynchronizedLyrics
import it.vfsfitvnm.vimusic.utils.SynchronizedLyricsState
import it.vfsfitvnm.vimusic.utils.center
import it.vfsfitvnm.vimusic.utils.color
import it.vfsfitvnm.vimusic.utils.isInPip
import it.vfsfitvnm.vimusic.utils.medium
import it.vfsfitvnm.vimusic.utils.semiBold
import it.vfsfitvnm.vimusic.utils.toast
import it.vfsfitvnm.core.ui.LocalAppearance
import it.vfsfitvnm.core.ui.onOverlay
import it.vfsfitvnm.core.ui.onOverlayShimmer
import it.vfsfitvnm.core.ui.overlay
import it.vfsfitvnm.providers.innertube.Innertube
import it.vfsfitvnm.providers.innertube.models.bodies.NextBody
import it.vfsfitvnm.providers.innertube.requests.lyrics
import it.vfsfitvnm.providers.kugou.KuGou
import it.vfsfitvnm.providers.lrclib.LrcLib
import it.vfsfitvnm.providers.lrclib.LrcParser
import it.vfsfitvnm.providers.lrclib.models.Track
import it.vfsfitvnm.providers.lrclib.toLrcFile
import com.valentinilk.shimmer.shimmer
import it.vfsfitvnm.core.ui.utils.dp
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import it.vfsfitvnm.providers.lyricsplus.LyricsPlus
import it.vfsfitvnm.providers.lyricsplus.LyricsPlusSyncManager
import kotlinx.coroutines.isActive

private const val UPDATE_DELAY = 50L
private val wordSyncCache = mutableMapOf<String, LyricsPlusSyncManager>()
@Composable
fun Lyrics(
    mediaId: String,
    isDisplayed: Boolean,
    onDismiss: () -> Unit,
    mediaMetadataProvider: () -> MediaMetadata,
    durationProvider: () -> Long,
    ensureSongInserted: () -> Unit,
    modifier: Modifier = Modifier,
    onMenuLaunch: () -> Unit = { },
    onOpenDialog: (() -> Unit)? = null,
    shouldShowSynchronizedLyrics: Boolean = PlayerPreferences.isShowingSynchronizedLyrics,
    setShouldShowSynchronizedLyrics: (Boolean) -> Unit = {
        PlayerPreferences.isShowingSynchronizedLyrics = it
    },
    shouldKeepScreenAwake: Boolean = PlayerPreferences.lyricsKeepScreenAwake,
    shouldUpdateLyrics: Boolean = true,
    showControls: Boolean = true
) = AnimatedVisibility(
    visible = isDisplayed,
    enter = fadeIn(),
    exit = fadeOut()
) {
    val currentEnsureSongInserted by rememberUpdatedState(ensureSongInserted)
    val currentMediaMetadataProvider by rememberUpdatedState(mediaMetadataProvider)
    val currentDurationProvider by rememberUpdatedState(durationProvider)

    val (colorPalette, typography) = LocalAppearance.current
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val binder = LocalPlayerServiceBinder.current
    val density = LocalDensity.current
    val view = LocalView.current

    val pip = isInPip()

    var lyrics by remember { mutableStateOf<Lyrics?>(null) }

    val showSynchronizedLyrics = remember(shouldShowSynchronizedLyrics, lyrics) {
        shouldShowSynchronizedLyrics && lyrics?.synced?.isBlank() != true
    }

    var editing by remember(mediaId, shouldShowSynchronizedLyrics) { mutableStateOf(false) }
    var picking by remember(mediaId, shouldShowSynchronizedLyrics) { mutableStateOf(false) }
    var error by remember(mediaId, shouldShowSynchronizedLyrics) { mutableStateOf(false) }

    val text = remember(lyrics, showSynchronizedLyrics) {
        if (showSynchronizedLyrics) lyrics?.synced else lyrics?.fixed
    }
    var invalidLrc by remember(text) { mutableStateOf(false) }

    // Use rememberSaveable to persist wordSynced states across recompositions
    var wordSyncedManager by rememberSaveable(mediaId, shouldShowSynchronizedLyrics) {
        mutableStateOf<LyricsPlusSyncManager?>(null)
    }
    var wordSyncedAvailable by rememberSaveable(mediaId, shouldShowSynchronizedLyrics) {
        mutableStateOf(false)
    }

    DisposableEffect(shouldKeepScreenAwake) {
        view.keepScreenOn = shouldKeepScreenAwake

        onDispose {
            view.keepScreenOn = false
        }
    }

    LaunchedEffect(mediaId, shouldShowSynchronizedLyrics, binder?.player?.isPlaying) {
        runCatching {
            withContext(Dispatchers.IO) {
                Database.instance
                    .lyrics(mediaId)
                    .distinctUntilChanged()
                    .cancellable()
                    .collect { currentLyrics ->
                        if (
                            !shouldUpdateLyrics ||
                            (currentLyrics?.fixed != null && currentLyrics.synced != null)
                        ) {
                            lyrics = currentLyrics

                            // Check cache first for word-level lyrics
                            if (wordSyncCache.containsKey(mediaId)) {
                                wordSyncedManager = wordSyncCache[mediaId]
                                wordSyncedAvailable = true
                            } else if (wordSyncedManager == null) {
                                // Only fetch if not in cache and not already set
                                val mediaMetadata = currentMediaMetadataProvider()
                                val duration = withContext(Dispatchers.Main) {
                                    currentDurationProvider()
                                }
                                val album = mediaMetadata.albumTitle?.toString()
                                val artist = mediaMetadata.artist?.toString().orEmpty()
                                val title = mediaMetadata.title?.toString().orEmpty().let {
                                    if (mediaId.startsWith(LOCAL_KEY_PREFIX)) {
                                        it.substringBeforeLast('.').trim()
                                    } else it
                                }

                                val wordLevelLyrics = LyricsPlus.fetchLyrics(
                                    title = title,
                                    artist = artist
                                )

                                if (!wordLevelLyrics.isNullOrEmpty()) {
                                    val manager = LyricsPlusSyncManager(
                                        lyrics = wordLevelLyrics,
                                        positionProvider = {
                                            binder?.player?.currentPosition ?: 0L
                                        }
                                    )
                                    // Cache the manager
                                    wordSyncCache[mediaId] = manager
                                    wordSyncedManager = manager
                                    wordSyncedAvailable = true
                                } else {
                                    wordSyncedAvailable = false
                                }
                            }
                        } else {
                            val mediaMetadata = currentMediaMetadataProvider()
                            var duration = withContext(Dispatchers.Main) {
                                currentDurationProvider()
                            }

                            while (duration == C.TIME_UNSET) {
                                delay(100)
                                duration = withContext(Dispatchers.Main) {
                                    currentDurationProvider()
                                }
                            }

                            val album = mediaMetadata.albumTitle?.toString()
                            val artist = mediaMetadata.artist?.toString().orEmpty()
                            val title = mediaMetadata.title?.toString().orEmpty().let {
                                if (mediaId.startsWith(LOCAL_KEY_PREFIX)) {
                                    it.substringBeforeLast('.').trim()
                                } else it
                            }

                            lyrics = null
                            error = false

                            // DON'T reset wordSyncedManager here - check cache instead
                            if (wordSyncCache.containsKey(mediaId)) {
                                wordSyncedManager = wordSyncCache[mediaId]
                                wordSyncedAvailable = true
                            } else {
                                wordSyncedManager = null
                                wordSyncedAvailable = false

                                // ✅ Try LyricsPlus word-level first
                                val wordLevelLyrics = LyricsPlus.fetchLyrics(
                                    title = title,
                                    artist = artist
                                )

                                if (!wordLevelLyrics.isNullOrEmpty()) {
                                    val manager = LyricsPlusSyncManager(
                                        lyrics = wordLevelLyrics,
                                        positionProvider = {
                                            binder?.player?.currentPosition ?: 0L
                                        }
                                    )
                                    // Cache the manager
                                    wordSyncCache[mediaId] = manager
                                    wordSyncedManager = manager
                                    wordSyncedAvailable = true
                                } else {
                                    wordSyncedAvailable = false
                                }
                            }

                            // ✅ Fallback to line-level sources (keep existing logic)
                            val fixed = currentLyrics?.fixed ?: Innertube
                                .lyrics(NextBody(videoId = mediaId))
                                ?.getOrNull()
                            ?: LrcLib.bestLyrics(
                                artist = artist,
                                title = title,
                                duration = duration.milliseconds,
                                album = album,
                                synced = false
                            )?.map { it?.text }?.getOrNull()

                            val synced = currentLyrics?.synced ?: LrcLib.bestLyrics(
                                artist = artist,
                                title = title,
                                duration = duration.milliseconds,
                                album = album
                            )?.map { it?.text }?.getOrNull()
                            ?: LrcLib.bestLyrics(
                                artist = artist,
                                title = title.split("(")[0].trim(),
                                duration = duration.milliseconds,
                                album = album
                            )?.map { it?.text }?.getOrNull()
                            ?: KuGou.lyrics(
                                artist = artist,
                                title = title,
                                duration = duration / 1000
                            )?.map { it?.value }?.getOrNull()

                            Lyrics(
                                songId = mediaId,
                                fixed = fixed.orEmpty(),
                                synced = synced.orEmpty()
                            ).also {
                                ensureActive()
                                transaction {
                                    runCatching {
                                        currentEnsureSongInserted()
                                        Database.instance.upsert(it)
                                    }
                                }
                            }
                        }

                        error = if (shouldShowSynchronizedLyrics) {
                            !wordSyncedAvailable && lyrics?.synced?.isBlank() == true
                        } else {
                            lyrics?.fixed?.isBlank() == true
                        }
                    }
            }
        }.exceptionOrNull()?.let {
            if (it is CancellationException) throw it
            else it.printStackTrace()
        }
    }

    if (editing) TextFieldDialog(
        hintText = stringResource(R.string.enter_lyrics),
        initialTextInput = (if (shouldShowSynchronizedLyrics) lyrics?.synced else lyrics?.fixed)
            .orEmpty(),
        singleLine = false,
        maxLines = 10,
        isTextInputValid = { true },
        onDismiss = { editing = false },
        onAccept = {
            transaction {
                runCatching {
                    currentEnsureSongInserted()

                    Database.instance.upsert(
                        if (shouldShowSynchronizedLyrics) Lyrics(
                            songId = mediaId,
                            fixed = lyrics?.fixed,
                            synced = it
                        ) else Lyrics(
                            songId = mediaId,
                            fixed = it,
                            synced = lyrics?.synced
                        )
                    )
                }
            }
        }
    )

    if (picking && shouldShowSynchronizedLyrics) {
        var query by rememberSaveable {
            mutableStateOf(
                currentMediaMetadataProvider().title?.toString().orEmpty().let {
                    if (mediaId.startsWith(LOCAL_KEY_PREFIX)) it
                        .substringBeforeLast('.')
                        .trim()
                    else it
                }
            )
        }

        LrcLibSearchDialog(
            query = query,
            setQuery = { query = it },
            onDismiss = { picking = false },
            onPick = {
                runCatching {
                    transaction {
                        Database.instance.upsert(
                            Lyrics(
                                songId = mediaId,
                                fixed = lyrics?.fixed,
                                synced = it.syncedLyrics
                            )
                        )
                    }
                }
            }
        )
    }

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            }
            .fillMaxSize()
            .background(colorPalette.overlay)
    ) {
        val animatedHeight by animateDpAsState(
            targetValue = this.maxHeight, // `this` needed, thanks Android Lint!
            label = ""
        )

        AnimatedVisibility(
            visible = error,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            BasicText(
                text = stringResource(
                    if (shouldShowSynchronizedLyrics) R.string.synchronized_lyrics_not_available
                    else R.string.lyrics_not_available
                ),
                style = typography.xs.center.medium.color(colorPalette.onOverlay),
                modifier = Modifier
                    .background(Color.Black.copy(0.4f))
                    .padding(all = 8.dp)
                    .fillMaxWidth(),
                maxLines = if (pip) 1 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis
            )
        }

        AnimatedVisibility(
            visible = !text.isNullOrBlank() && !error && invalidLrc && shouldShowSynchronizedLyrics && !wordSyncedAvailable,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            BasicText(
                text = stringResource(R.string.invalid_synchronized_lyrics),
                style = typography.xs.center.medium.color(colorPalette.onOverlay),
                modifier = Modifier
                    .background(Color.Black.copy(0.4f))
                    .padding(all = 8.dp)
                    .fillMaxWidth(),
                maxLines = if (pip) 1 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis
            )
        }

        val lyricsState = rememberSaveable(text) {
            val file = lyrics?.synced?.takeIf { it.isNotBlank() }?.let {
                LrcParser.parse(it)?.toLrcFile()
            }

            SynchronizedLyricsState(
                sentences = file?.lines,
                offset = file?.offset?.inWholeMilliseconds ?: 0L
            )
        }

        val synchronizedLyrics = remember(lyricsState) {
            invalidLrc = lyricsState.sentences == null
            lyricsState.sentences?.let {
                SynchronizedLyrics(it.toImmutableMap()) {
                    binder?.player?.let { player ->
                        player.currentPosition + UPDATE_DELAY + lyricsState.offset -
                            (lyrics?.startTime ?: 0L)
                    } ?: 0L
                }
            }
        }

        AnimatedContent(
            targetState = showSynchronizedLyrics,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = ""
        ) { synchronized ->
            when {
                // ✅ 1. Word-level (LyricsPlus)
                synchronized && wordSyncedAvailable && wordSyncedManager != null -> {
                    LaunchedEffect(Unit) {
                        while (isActive) {
                            wordSyncedManager?.updatePosition()
                            delay(UPDATE_DELAY)
                        }
                    }

                    WordSyncedLyrics(manager = wordSyncedManager!!)
                }

                // ✅ 2. Fallback: line-level LRC
                synchronized -> {
                    val lazyListState = rememberLazyListState()

                    LaunchedEffect(synchronizedLyrics, density, animatedHeight) {
                        val currentSynchronizedLyrics = synchronizedLyrics ?: return@LaunchedEffect
                        val centerOffset = with(density) { (-animatedHeight / 3).roundToPx() }

                        lazyListState.animateScrollToItem(
                            index = currentSynchronizedLyrics.index + 1,
                            scrollOffset = centerOffset
                        )

                        while (true) {
                            delay(UPDATE_DELAY)
                            if (!currentSynchronizedLyrics.update()) continue

                            lazyListState.animateScrollToItem(
                                index = currentSynchronizedLyrics.index + 1,
                                scrollOffset = centerOffset
                            )
                        }
                    }

                    if (synchronizedLyrics != null) LazyColumn(
                        state = lazyListState,
                        userScrollEnabled = false,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .verticalFadingEdge()
                            .fillMaxWidth()
                    ) {
                        item(key = "header", contentType = 0) {
                            Spacer(modifier = Modifier.height(maxHeight))
                        }

                        itemsIndexed(
                            items = synchronizedLyrics.sentences.values.toImmutableList()
                        ) { index, sentence ->
                            val color by animateColorAsState(
                                if (index == synchronizedLyrics.index) Color.White
                                else colorPalette.textDisabled
                            )

                            if (sentence.isBlank()) {
                                Image(
                                    painter = painterResource(R.drawable.musical_notes),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(color),
                                    modifier = Modifier
                                        .padding(vertical = 4.dp, horizontal = 32.dp)
                                        .size(typography.xs.fontSize.dp)
                                )
                            } else {
                                BasicText(
                                    text = sentence,
                                    style = typography.xs.center.medium.color(color),
                                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 32.dp)
                                )
                            }
                        }

                        item(key = "footer", contentType = 0) {
                            Spacer(modifier = Modifier.height(maxHeight))
                        }
                    }
                }

                // ✅ 3. Unsynchronized fallback
                else -> {
                    BasicText(
                        text = lyrics?.fixed.orEmpty(),
                        style = typography.xs.center.medium.color(colorPalette.onOverlay),
                        modifier = Modifier
                            .verticalFadingEdge()
                            .verticalScroll(rememberScrollState())
                            .fillMaxWidth()
                            .padding(vertical = maxHeight / 4, horizontal = 32.dp)
                    )
                }
            }
        }

        if (text == null && !error && !wordSyncedAvailable) Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.shimmer()
        ) {
            repeat(4) {
                TextPlaceholder(
                    color = colorPalette.onOverlayShimmer,
                    modifier = Modifier.alpha(1f - it * 0.2f)
                )
            }
        }

        if (showControls) {
            if (onOpenDialog != null) Image(
                painter = painterResource(R.drawable.expand),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colorPalette.onOverlay),
                modifier = Modifier
                    .padding(all = 4.dp)
                    .clickable(
                        indication = ripple(bounded = false),
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {
                            onOpenDialog()
                        }
                    )
                    .padding(all = 8.dp)
                    .size(20.dp)
                    .align(Alignment.BottomStart)
            )

            Image(
                painter = painterResource(R.drawable.ellipsis_horizontal),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colorPalette.onOverlay),
                modifier = Modifier
                    .padding(all = 4.dp)
                    .clickable(
                        indication = ripple(bounded = false),
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {
                            onMenuLaunch()
                            menuState.display {
                                Menu {
                                    MenuEntry(
                                        icon = R.drawable.time,
                                        text = stringResource(
                                            if (shouldShowSynchronizedLyrics) R.string.show_unsynchronized_lyrics
                                            else R.string.show_synchronized_lyrics
                                        ),
                                        onClick = {
                                            menuState.hide()
                                            setShouldShowSynchronizedLyrics(!shouldShowSynchronizedLyrics)
                                        }
                                    )

                                    MenuEntry(
                                        icon = R.drawable.pencil,
                                        text = stringResource(R.string.edit_lyrics),
                                        onClick = {
                                            menuState.hide()
                                            editing = true
                                        }
                                    )

                                    MenuEntry(
                                        icon = R.drawable.search,
                                        text = stringResource(R.string.search_lyrics_online),
                                        onClick = {
                                            menuState.hide()
                                            val mediaMetadata = currentMediaMetadataProvider()

                                            try {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_WEB_SEARCH).apply {
                                                        putExtra(
                                                            SearchManager.QUERY,
                                                            "${mediaMetadata.title} ${mediaMetadata.artist} lyrics"
                                                        )
                                                    }
                                                )
                                            } catch (_: ActivityNotFoundException) {
                                                context.toast(context.getString(R.string.no_browser_installed))
                                            }
                                        }
                                    )

                                    MenuEntry(
                                        icon = R.drawable.sync,
                                        text = stringResource(R.string.refetch_lyrics),
                                        enabled = lyrics != null,
                                        onClick = {
                                            menuState.hide()

                                            transaction {
                                                runCatching {
                                                    currentEnsureSongInserted()

                                                    Database.instance.upsert(
                                                        if (shouldShowSynchronizedLyrics) Lyrics(
                                                            songId = mediaId,
                                                            fixed = lyrics?.fixed,
                                                            synced = null
                                                        ) else Lyrics(
                                                            songId = mediaId,
                                                            fixed = null,
                                                            synced = lyrics?.synced
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    )

                                    if (shouldShowSynchronizedLyrics) {
                                        MenuEntry(
                                            icon = R.drawable.download,
                                            text = stringResource(R.string.pick_from_lrclib),
                                            onClick = {
                                                menuState.hide()
                                                picking = true
                                            }
                                        )
                                        MenuEntry(
                                            icon = R.drawable.play_skip_forward,
                                            text = stringResource(R.string.set_lyrics_start_offset),
                                            secondaryText = stringResource(
                                                R.string.set_lyrics_start_offset_description
                                            ),
                                            onClick = {
                                                menuState.hide()
                                                lyrics?.let {
                                                    val startTime = binder?.player?.currentPosition
                                                    query {
                                                        Database.instance.upsert(it.copy(startTime = startTime))
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    )
                    .padding(all = 8.dp)
                    .size(20.dp)
                    .align(Alignment.BottomEnd)
            )
        }
    }
}

@Composable
fun LrcLibSearchDialog(
    query: String,
    setQuery: (String) -> Unit,
    onDismiss: () -> Unit,
    onPick: (Track) -> Unit,
    modifier: Modifier = Modifier
) = DefaultDialog(
    onDismiss = onDismiss,
    horizontalPadding = 0.dp,
    modifier = modifier
) {
    val (_, typography) = LocalAppearance.current

    val tracks = remember { mutableStateListOf<Track>() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        loading = true
        error = false

        delay(1000)

        LrcLib.lyrics(
            query = query,
            synced = true
        )?.onSuccess { newTracks ->
            tracks.clear()
            tracks.addAll(newTracks.filter { !it.syncedLyrics.isNullOrBlank() })
            loading = false
            error = false
        }?.onFailure {
            loading = false
            error = true
            it.printStackTrace()
        } ?: run { loading = false }
    }

    TextField(
        value = query,
        onValueChange = setQuery,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        maxLines = 1,
        singleLine = true
    )
    Spacer(modifier = Modifier.height(8.dp))

    when {
        loading -> CircularProgressIndicator(
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        error || tracks.isEmpty() -> BasicText(
            text = stringResource(R.string.no_lyrics_found),
            style = typography.s.semiBold.center,
            modifier = Modifier
                .padding(all = 24.dp)
                .align(Alignment.CenterHorizontally)
        )

        else -> ValueSelectorDialogBody(
            onDismiss = onDismiss,
            title = stringResource(R.string.choose_lyric_track),
            selectedValue = null,
            values = tracks.toImmutableList(),
            onValueSelect = {
                transaction {
                    onPick(it)
                    onDismiss()
                }
            },
            valueText = {
                "${it.artistName} - ${it.trackName} (${
                    it.duration.seconds.toComponents { minutes, seconds, _ ->
                        "$minutes:${seconds.toString().padStart(2, '0')}"
                    }
                })"
            }
        )
    }
}
