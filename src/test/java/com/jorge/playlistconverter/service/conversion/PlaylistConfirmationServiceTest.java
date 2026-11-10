package com.jorge.playlistconverter.service.conversion;

import com.jorge.playlistconverter.converter.ConversionResult;
import com.jorge.playlistconverter.converter.PlaylistConverter;
import com.jorge.playlistconverter.converter.preview.InMemoryPlaylistPreviewStore;
import com.jorge.playlistconverter.converter.preview.PlaylistPreview;
import com.jorge.playlistconverter.converter.preview.StoredPlaylistPreview;
import com.jorge.playlistconverter.enums.PlaylistVisibility;
import com.jorge.playlistconverter.errors.conversion.PlaylistPreviewUnavailableException;
import com.jorge.playlistconverter.errors.youtube.YoutubeTrackInsertionException;
import com.jorge.playlistconverter.errors.conversion.InvalidTrackSelectionException;
import com.jorge.playlistconverter.model.MatchResult;
import com.jorge.playlistconverter.model.Song;
import com.jorge.playlistconverter.model.TrackReviewOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlaylistConfirmationServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-10-26T18:00:00Z");

    private static final Duration LIFETIME = Duration.ofMinutes(10);

    private Clock clock;
    private PlaylistConverter converter;
    private InMemoryPlaylistPreviewStore store;
    private PlaylistConfirmationService service;
    private PlaylistPreview newPlaylistPreview;

    @BeforeEach
    void setUp() {
        clock = mock(Clock.class);
        when(clock.instant()).thenReturn(NOW);

        converter = mock(PlaylistConverter.class);
        store = new InMemoryPlaylistPreviewStore(clock, LIFETIME);
        service = new PlaylistConfirmationService(converter, store);

        newPlaylistPreview = new PlaylistPreview("source-playlist",
                List.of());
    }

    @Test
    void shouldConfirmNewPlaylistWithStoredNameAndVisibility() {
        StoredPlaylistPreview stored = store.save(newPlaylistPreview,
                "Meu Rock", PlaylistVisibility.PUBLIC);

        ConversionResult expected = new ConversionResult("source-playlist", "new-playlist",
                List.of(), 0, 0);

        when(converter.executeNewPlaylistPreview(newPlaylistPreview,
                "Meu Rock", PlaylistVisibility.PUBLIC)).thenReturn(expected);

        ConversionResult result = service.confirm(stored.id());

        assertSame(expected, result);
        assertTrue(store.find(stored.id()).isEmpty());

        verify(converter).executeNewPlaylistPreview(newPlaylistPreview,
                "Meu Rock", PlaylistVisibility.PUBLIC);

        verifyNoMoreInteractions(converter);
    }

    @Test
    void shouldConfirmExistingPlaylistUsingStoredPreview() {
        PlaylistPreview preview = new PlaylistPreview("source-playlist",
                "destination-playlist", List.of(), Set.of());

        StoredPlaylistPreview stored = store.save(preview, null, null);

        ConversionResult expected = new ConversionResult(
                "source-playlist", "destination-playlist",
                List.of(), 0, 0);

        when(converter.executeExistingPlaylistPreview(preview))
                .thenReturn(expected);

        ConversionResult result = service.confirm(stored.id());

        assertSame(expected, result);
        assertTrue(store.find(stored.id()).isEmpty());

        verify(converter).executeExistingPlaylistPreview(preview);
        verifyNoMoreInteractions(converter);
    }

    @Test
    void shouldRejectUnknownPreviewWithoutCallingConverter() {
        assertThrows(PlaylistPreviewUnavailableException.class,
                () -> service.confirm(UUID.randomUUID()));

        verifyNoInteractions(converter);
    }

    @Test
    void shouldRejectExpiredPreviewWithoutCallingConverter() {
        StoredPlaylistPreview stored = store.save(newPlaylistPreview,
                "Meu Rock", PlaylistVisibility.PRIVATE);

        when(clock.instant()).thenReturn(NOW.plus(LIFETIME));

        assertThrows(PlaylistPreviewUnavailableException.class,
                () -> service.confirm(stored.id()));

        verifyNoInteractions(converter);
    }

    @Test
    void shouldRejectSecondConfirmationAfterSuccess() {
        StoredPlaylistPreview stored = store.save(newPlaylistPreview,
                "Meu Rock", PlaylistVisibility.PRIVATE);

        ConversionResult expected = new ConversionResult(
                "source-playlist", "new-playlist",
                List.of(), 0, 0);

        when(converter.executeNewPlaylistPreview(newPlaylistPreview, "Meu Rock",
                PlaylistVisibility.PRIVATE)).thenReturn(expected);

        assertSame(expected, service.confirm(stored.id()));

        assertThrows(PlaylistPreviewUnavailableException.class,
                () -> service.confirm(stored.id()));

        verify(converter, times(1)).executeNewPlaylistPreview(
                newPlaylistPreview, "Meu Rock", PlaylistVisibility.PRIVATE);

        verifyNoMoreInteractions(converter);
    }

    @Test
    void shouldPreserveConversionFailureAndRejectSecondConfirmation() {
        StoredPlaylistPreview stored = store.save(newPlaylistPreview,
                "Meu Rock", PlaylistVisibility.PRIVATE);

        YoutubeTrackInsertionException original =
                new YoutubeTrackInsertionException("Falha simulada.");

        when(converter.executeNewPlaylistPreview(newPlaylistPreview,
                "Meu Rock", PlaylistVisibility.PRIVATE)).thenThrow(original);

        YoutubeTrackInsertionException result = assertThrows(YoutubeTrackInsertionException.class,
                () -> service.confirm(stored.id()));

        assertSame(original, result);
        assertTrue(store.find(stored.id()).isEmpty());

        assertThrows(PlaylistPreviewUnavailableException.class,
                () -> service.confirm(stored.id()));

        verify(converter, times(1)).executeNewPlaylistPreview(
                newPlaylistPreview, "Meu Rock",
                PlaylistVisibility.PRIVATE);

        verifyNoMoreInteractions(converter);
    }

    @Test
    void shouldRejectNullPreviewIdWithoutCallingConverter() {
        assertThrows(NullPointerException.class,
                () -> service.confirm(null));

        verifyNoInteractions(converter);
    }

    @Test
    void shouldConfirmWithSelectedReviewOption() {
        StoredPlaylistPreview stored = savePreviewWithReviewOptions();
        MatchResult original = stored.preview().matches().getFirst();

        MatchResult selected = new MatchResult(
                original.sourceSong(), "video-B", "Artist - Song A", 0.96);

        PlaylistPreview expectedPreview = new PlaylistPreview(
                "source-playlist", "destination-playlist",
                List.of(selected), Set.of());

        service.confirm(stored.id(), Map.of(0, "video-B"));

        verify(converter).executeExistingPlaylistPreview(expectedPreview);
        assertTrue(store.find(stored.id()).isEmpty());
    }

    @Test
    void shouldKeepPreviewWhenSelectedVideoWasNotOffered() {
        StoredPlaylistPreview stored = savePreviewWithReviewOptions();

        assertThrows(InvalidTrackSelectionException.class,
                () -> service.confirm(stored.id(), Map.of(0, "video-C")));

        assertSame(stored, store.find(stored.id()).orElseThrow());
        verifyNoInteractions(converter);
    }

    private StoredPlaylistPreview savePreviewWithReviewOptions() {
        Song source = new Song("source-A", "Song A", "Artist", 200_000);
        Song suggested = new Song("video-A", "Song A", "Artist", 202_000);
        Song alternative = new Song("video-B", "Artist - Song A", "Label", 201_000);

        MatchResult match = new MatchResult(
                source, suggested.id(), suggested.title(), 0.98,
                List.of(
                        new TrackReviewOption(suggested, 0.98),
                        new TrackReviewOption(alternative, 0.96)));

        PlaylistPreview preview = new PlaylistPreview(
                "source-playlist", "destination-playlist",
                List.of(match), Set.of());

        return store.save(preview, null, null);
    }
}
