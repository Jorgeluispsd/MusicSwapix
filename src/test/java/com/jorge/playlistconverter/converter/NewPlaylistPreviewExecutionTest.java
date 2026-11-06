package com.jorge.playlistconverter.converter;

import com.jorge.playlistconverter.converter.execution.ExistingPlaylistExecutor;
import com.jorge.playlistconverter.converter.execution.NewPlaylistExecutor;
import com.jorge.playlistconverter.converter.history.PlaylistDestinationRegistry;
import com.jorge.playlistconverter.converter.history.SyncRunTracker;
import com.jorge.playlistconverter.converter.history.TrackSyncRecorder;
import com.jorge.playlistconverter.converter.preview.PlaylistPreview;
import com.jorge.playlistconverter.enums.PlaylistVisibility;
import com.jorge.playlistconverter.enums.SyncRunStatus;
import com.jorge.playlistconverter.errors.conversion.NoTracksToInsertException;
import com.jorge.playlistconverter.errors.persistence.SyncStateStorageException;
import com.jorge.playlistconverter.errors.youtube.YoutubePlaylistCreationException;
import com.jorge.playlistconverter.errors.youtube.YoutubeTrackInsertionException;
import com.jorge.playlistconverter.matcher.TrackMatcher;
import com.jorge.playlistconverter.model.MatchResult;
import com.jorge.playlistconverter.model.Song;
import com.jorge.playlistconverter.service.MusicService;
import com.jorge.playlistconverter.state.SyncStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NewPlaylistPreviewExecutionTest {

    private MusicService source;
    private MusicService destination;
    private TrackMatcher matcher;
    private PlaylistConverter converter;
    private PlaylistDestinationRegistry destinationRegistry;
    private SyncStateStore store;

    @BeforeEach
    void setUp() {
        source = mock(MusicService.class);
        destination = mock(MusicService.class);
        matcher = mock(TrackMatcher.class);
        destinationRegistry = mock(PlaylistDestinationRegistry.class);

        store = mock(SyncStateStore.class);
        SyncRunTracker runTracker = new SyncRunTracker(store);
        TrackSyncRecorder trackSyncRecorder = new TrackSyncRecorder(store);

        PlaylistAnalyzer analyzer = new PlaylistAnalyzer(source, destination, matcher);

        PlaylistConversionValidator validator =
                new PlaylistConversionValidator();

        NewPlaylistExecutor newPlaylistExecutor =
                new NewPlaylistExecutor(destination, validator, destinationRegistry,
                        runTracker, trackSyncRecorder);

        ExistingPlaylistExecutor existingPlaylistExecutor =
                new ExistingPlaylistExecutor(destination, validator, destinationRegistry,
                        runTracker, trackSyncRecorder);

        converter = new PlaylistConverter(analyzer, destination,
                newPlaylistExecutor, existingPlaylistExecutor, validator);

        when(destinationRegistry.registerDestination(anyString(),
                anyString())).thenReturn(10L);

        when(store.startSyncRun(anyLong())).thenReturn(20L);
    }

    @ParameterizedTest
    @EnumSource(PlaylistVisibility.class)
    void shouldCreatePlaylistWithSelectedVisibilityWithoutRepeatingAnalysis(
            PlaylistVisibility visibility
    ) {
        PlaylistPreview preview = preview(
                match("source-A", "video-A"),
                match("source-B", "video-B"));

        when(destination.createPlaylist("Meu Rock", visibility))
                .thenReturn("new-playlist");

        ConversionResult result = converter.executeNewPlaylistPreview(
                preview, "  Meu Rock  ", visibility);

        assertEquals("source-playlist", result.sourcePlaylistId());
        assertEquals("new-playlist", result.destinationPlaylistId());
        assertEquals(preview.matches(), result.matches());
        assertEquals(2, result.insertedCount());
        assertEquals(0, result.skippedCount());

        var order = inOrder(destination, destinationRegistry, store);

        order.verify(destination).createPlaylist("Meu Rock", visibility);

        order.verify(destinationRegistry).registerDestination("source-playlist",
                "new-playlist");

        order.verify(store).startSyncRun(10L);

        order.verify(destination).addTracks(eq("new-playlist"),
                eq(List.of("video-A", "video-B")), any());

        order.verify(store).finishSyncRun(20L, SyncRunStatus.SUCCESS);

        verifyNoMoreInteractions(destination, destinationRegistry, store);
        verifyNoInteractions(source, matcher);
    }

    @Test
    void shouldUsePrivateVisibilityByDefault() {
        when(destination.createPlaylist(
                "Meu Rock", PlaylistVisibility.PRIVATE))
                .thenReturn("new-playlist");

        converter.executeNewPlaylistPreview(
                preview(match("source-A", "video-A")),
                "Meu Rock"
        );

        verify(destination).createPlaylist(
                "Meu Rock", PlaylistVisibility.PRIVATE);
        verify(destination).addTracks(eq("new-playlist"),
                eq(List.of("video-A")), any());

        verifyNoMoreInteractions(destination);
        verifyNoInteractions(source, matcher);
    }

    @Test
    void shouldSkipRepeatedIdsAndPreserveUnmatchedSongs() {
        MatchResult missing = new MatchResult(
                new Song("source-D", "Song D", "Artist D", 0),
                null, null, 0.0);

        PlaylistPreview preview = preview(
                match("source-A", "video-A"),
                match("source-B", "video-B"),
                match("source-C", "video-A"),
                missing);

        when(destination.createPlaylist(
                "Meu Rock", PlaylistVisibility.PRIVATE))
                .thenReturn("new-playlist");

        ConversionResult result = converter.executeNewPlaylistPreview(
                preview, "Meu Rock");

        assertEquals(2, result.insertedCount());
        assertEquals(1, result.skippedCount());
        assertEquals(preview.matches(), result.matches());
        assertEquals(missing, result.matches().get(3));

        verify(destination).addTracks(eq("new-playlist"),
                eq(List.of("video-A", "video-B")), any());

        verifyNoInteractions(source, matcher);
    }

    @Test
    void shouldRejectNullPreviewOrVisibilityBeforeWriting() {
        PlaylistPreview preview =
                preview(match("source-A", "video-A"));

        assertThrows(
                NullPointerException.class,
                () -> converter.executeNewPlaylistPreview(
                        null, "Meu Rock", PlaylistVisibility.PRIVATE));

        assertThrows(
                NullPointerException.class,
                () -> converter.executeNewPlaylistPreview(
                        preview, "Meu Rock", null));

        verifyNoInteractions(source, destination, matcher);
    }

    @Test
    void shouldRejectInvalidSourceIdOrNameBeforeWriting() {
        for (String invalid : new String[]{null, "", "   "}) {
            PlaylistPreview invalidSource = new PlaylistPreview(
                    invalid, List.of(match("source-A", "video-A")));

            assertThrows(
                    IllegalArgumentException.class,
                    () -> converter.executeNewPlaylistPreview(
                            invalidSource, "Meu Rock"));

            assertThrows(
                    IllegalArgumentException.class,
                    () -> converter.executeNewPlaylistPreview(
                            preview(match("source-A", "video-A")),
                            invalid));
        }

        verifyNoInteractions(source, destination, matcher);
    }

    @Test
    void shouldRejectPreviewPreparedForExistingDestination() {
        List<MatchResult> matches =
                List.of(match("source-A", "video-A"));

        PlaylistPreview withDestinationId = new PlaylistPreview(
                "source-playlist", "existing-playlist",
                matches, Set.of());

        PlaylistPreview withExistingIds = new PlaylistPreview(
                "source-playlist", null,
                matches, Set.of("existing-video"));

        for (PlaylistPreview preview :
                List.of(withDestinationId, withExistingIds)) {

            assertThrows(IllegalArgumentException.class,
                    () -> converter.executeNewPlaylistPreview(
                            preview, "Meu Rock"));
        }

        verifyNoInteractions(source, destination, matcher);
    }

    @Test
    void shouldRejectPreviewWithoutMatchedTracksBeforeCreatingPlaylist() {
        MatchResult missing = new MatchResult(
                new Song("source-A", "Song A", "Artist A", 0),
                null, null, 0.0);

        for (PlaylistPreview preview :
                List.of(preview(), preview(missing))) {

            assertThrows(NoTracksToInsertException.class,
                    () -> converter.executeNewPlaylistPreview(
                            preview, "Meu Rock"));
        }

        verifyNoInteractions(source, destination, matcher);
    }

    @Test
    void shouldPreserveCreationFailureWithoutInsertingTracks() {
        YoutubePlaylistCreationException original =
                new YoutubePlaylistCreationException(
                        "Falha de criação simulada.");

        when(destination.createPlaylist(
                "Meu Rock", PlaylistVisibility.PRIVATE))
                .thenThrow(original);

        YoutubePlaylistCreationException result = assertThrows(
                YoutubePlaylistCreationException.class,
                () -> converter.executeNewPlaylistPreview(
                        preview(match("source-A", "video-A")),
                        "Meu Rock"));

        assertSame(original, result);

        verify(destination, times(1)).createPlaylist(
                "Meu Rock", PlaylistVisibility.PRIVATE);

        verifyNoMoreInteractions(destination);
        verifyNoInteractions(source, matcher);
    }

    @Test
    void shouldPreserveInsertionFailureWithoutCreatingAnotherPlaylist() {
        when(destination.createPlaylist(
                "Meu Rock", PlaylistVisibility.PRIVATE))
                .thenReturn("new-playlist");

        YoutubeTrackInsertionException original =
                new YoutubeTrackInsertionException(
                        "Falha de inserção simulada.");

        doThrow(original).when(destination).addTracks(eq("new-playlist"),
                eq(List.of("video-A")), any());

        YoutubeTrackInsertionException result = assertThrows(
                YoutubeTrackInsertionException.class,
                () -> converter.executeNewPlaylistPreview(
                        preview(match("source-A", "video-A")),
                        "Meu Rock"));

        assertSame(original, result);

        verify(destination, times(1)).createPlaylist(
                "Meu Rock", PlaylistVisibility.PRIVATE);

        verify(destinationRegistry).registerDestination("source-playlist",
                "new-playlist");

        verify(destination, times(1)).addTracks(eq("new-playlist"),
                eq(List.of("video-A")), any());

        verifyNoMoreInteractions(destination);
        verifyNoInteractions(source, matcher);
        verify(store).startSyncRun(10L);
        verify(store).finishSyncRun(20L, SyncRunStatus.FAILED);
    }

    @Test
    void shouldStopBeforeInsertionWhenDestinationRegistrationFails() {
        when(destination.createPlaylist("Meu Rock",
                PlaylistVisibility.PRIVATE)).thenReturn("new-playlist");

        SyncStateStorageException original =
                new SyncStateStorageException(
                        "Falha simulada no registro.",
                        new SQLException("Falha simulada"));

        when(destinationRegistry.registerDestination(
                "source-playlist",
                "new-playlist")).thenThrow(original);

        SyncStateStorageException result = assertThrows(
                SyncStateStorageException.class,
                () -> converter.executeNewPlaylistPreview(
                        preview(match("source-A", "video-A")),
                        "Meu Rock"));

        assertSame(original, result.getCause());
        assertTrue(result.getMessage().contains("new-playlist"));

        var order = inOrder(destination, destinationRegistry);

        order.verify(destination).createPlaylist("Meu Rock",
                PlaylistVisibility.PRIVATE);

        order.verify(destinationRegistry).registerDestination(
                "source-playlist",
                "new-playlist");

        verifyNoMoreInteractions(destination, destinationRegistry);
        verifyNoInteractions(source, matcher);
    }

    @Test
    void shouldNotInsertTracksWhenStartingRunFails() {
        when(destination.createPlaylist("Meu Rock",
                PlaylistVisibility.PRIVATE)).thenReturn("new-playlist");

        SyncStateStorageException original =
                new SyncStateStorageException(
                        "Falha ao iniciar a execução.",
                        new SQLException("Falha simulada"));

        when(store.startSyncRun(10L)).thenThrow(original);

        SyncStateStorageException result = assertThrows(
                SyncStateStorageException.class,
                () -> converter.executeNewPlaylistPreview(
                        preview(match("source-A", "video-A")),
                        "Meu Rock"));

        assertSame(original, result);

        var order = inOrder(destination, destinationRegistry, store);

        order.verify(destination).createPlaylist(
                "Meu Rock",
                PlaylistVisibility.PRIVATE);

        order.verify(destinationRegistry).registerDestination(
                "source-playlist",
                "new-playlist");

        order.verify(store).startSyncRun(10L);

        verifyNoMoreInteractions(destination, destinationRegistry, store);
        verifyNoInteractions(source, matcher);
    }

    @Test
    void shouldRecordConfirmedTracksBeforeCompletingRun() {
        MatchResult firstMatch = match("source-A", "video-A");
        MatchResult secondMatch = match("source-B", "video-B");

        PlaylistPreview preview = preview(firstMatch, secondMatch);

        when(destination.createPlaylist("Meu Rock", PlaylistVisibility.PRIVATE
        )).thenReturn("new-playlist");

        doAnswer(invocation -> {
            Consumer<String> listener = invocation.getArgument(2);

            listener.accept("video-A");
            listener.accept("video-B");

            return null;
        }).when(destination).addTracks(
                eq("new-playlist"),
                eq(List.of("video-A", "video-B")),
                any());

        ConversionResult result =
                converter.executeNewPlaylistPreview(preview, "Meu Rock");

        assertEquals(2, result.insertedCount());

        var order = inOrder(store);

        order.verify(store).startSyncRun(10L);

        order.verify(store).markSynced(10L, firstMatch.sourceSong(),
                "video-A", "success", firstMatch.confidence());

        order.verify(store).markSynced(10L, secondMatch.sourceSong(),
                "video-B", "success", secondMatch.confidence());

        order.verify(store).finishSyncRun(20L, SyncRunStatus.SUCCESS);

        verifyNoMoreInteractions(store);
    }

    @Test
    void shouldKeepFirstTrackRecordedWhenSecondInsertionFails() {
        MatchResult firstMatch = match("source-A", "video-A");
        MatchResult secondMatch = match("source-B", "video-B");

        PlaylistPreview preview = preview(firstMatch, secondMatch);

        when(destination.createPlaylist("Meu Rock", PlaylistVisibility.PRIVATE
        )).thenReturn("new-playlist");

        YoutubeTrackInsertionException original =
                new YoutubeTrackInsertionException(
                        "Falha simulada na inserção de B.");

        doAnswer(invocation -> {
            Consumer<String> listener = invocation.getArgument(2);

            listener.accept("video-A");

            throw original;
        }).when(destination).addTracks(
                eq("new-playlist"),
                eq(List.of("video-A", "video-B")),
                any());

        YoutubeTrackInsertionException result = assertThrows(
                YoutubeTrackInsertionException.class,
                () -> converter.executeNewPlaylistPreview(preview, "Meu Rock"));

        assertSame(original, result);

        var order = inOrder(store);

        order.verify(store).startSyncRun(10L);

        order.verify(store).markSynced(10L, firstMatch.sourceSong(), "video-A",
                "success", firstMatch.confidence());

        order.verify(store).finishSyncRun(20L, SyncRunStatus.FAILED);

        verifyNoMoreInteractions(store);

        verify(destination, times(1)).addTracks(eq("new-playlist"),
                eq(List.of("video-A", "video-B")), any());
    }

    @Test
    void shouldFailRunWhenRecordingConfirmedTrackFails() {
        MatchResult firstMatch = match("source-A", "video-A");
        PlaylistPreview preview = preview(firstMatch);

        when(destination.createPlaylist("Meu Rock", PlaylistVisibility.PRIVATE
        )).thenReturn("new-playlist");

        SyncStateStorageException original =
                new SyncStateStorageException(
                        "Falha ao registrar a música confirmada.",
                        new SQLException("Falha simulada"));

        doThrow(original).when(store).markSynced(
                10L,
                firstMatch.sourceSong(),
                "video-A",
                "success",
                firstMatch.confidence());

        doAnswer(invocation -> {
            Consumer<String> listener = invocation.getArgument(2);

            listener.accept("video-A");

            return null;
        }).when(destination).addTracks(
                eq("new-playlist"),
                eq(List.of("video-A")),
                any());

        SyncStateStorageException result = assertThrows(
                SyncStateStorageException.class,
                () -> converter.executeNewPlaylistPreview(preview, "Meu Rock"));

        assertSame(original, result);

        var order = inOrder(store);

        order.verify(store).startSyncRun(10L);

        order.verify(store).markSynced(10L, firstMatch.sourceSong(),
                "video-A", "success", firstMatch.confidence());

        order.verify(store).finishSyncRun(20L, SyncRunStatus.FAILED);

        verifyNoMoreInteractions(store);

        verify(destination, times(1)).addTracks(eq("new-playlist"),
                eq(List.of("video-A")), any());
    }

    private PlaylistPreview preview(MatchResult... matches) {
        return new PlaylistPreview(
                "source-playlist", List.of(matches));
    }

    private MatchResult match(String sourceId, String videoId) {
        Song song = new Song(
                sourceId, "Song " + sourceId, "Artist", 0);

        return new MatchResult(
                song, videoId, "Video " + videoId, 0.90);
    }
}
