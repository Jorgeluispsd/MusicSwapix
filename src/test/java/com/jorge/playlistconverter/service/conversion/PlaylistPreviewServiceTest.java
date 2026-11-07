package com.jorge.playlistconverter.service.conversion;

import com.jorge.playlistconverter.converter.PlaylistConversionValidator;
import com.jorge.playlistconverter.converter.PlaylistConverter;
import com.jorge.playlistconverter.converter.preview.InMemoryPlaylistPreviewStore;
import com.jorge.playlistconverter.converter.preview.PlaylistPreview;
import com.jorge.playlistconverter.converter.preview.StoredPlaylistPreview;
import com.jorge.playlistconverter.enums.PlaylistVisibility;
import com.jorge.playlistconverter.errors.youtube.YoutubePlaylistReadException;
import com.jorge.playlistconverter.errors.youtube.YoutubeSearchException;
import com.jorge.playlistconverter.input.SpotifyPlaylistParser;
import com.jorge.playlistconverter.input.YoutubePlaylistParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlaylistPreviewServiceTest {

    private static final String SOURCE_ID = "1234567890123456789012";
    private static final String DESTINATION_ID = "PL_destination";

    private PlaylistConverter converter;
    private InMemoryPlaylistPreviewStore store;
    private PlaylistPreviewService service;

    @BeforeEach
    void setUp() {
        converter = mock(PlaylistConverter.class);
        store = mock(InMemoryPlaylistPreviewStore.class);

        service = new PlaylistPreviewService(converter, store,
                new SpotifyPlaylistParser(), new YoutubePlaylistParser(), new PlaylistConversionValidator());
    }

    @Test
    void shouldPrepareNewPlaylistFromLinkAndStoreSelectedOptions() {
        PlaylistPreview preview = new PlaylistPreview(SOURCE_ID, List.of());

        StoredPlaylistPreview expected = new StoredPlaylistPreview(UUID.randomUUID(),
                preview, "Meu Rock",
                PlaylistVisibility.PUBLIC, Instant.parse("2026-10-26T18:10:00Z"));

        when(converter.previewNewPlaylist(SOURCE_ID))
                .thenReturn(preview);

        when(store.save(preview, "Meu Rock", PlaylistVisibility.PUBLIC))
                .thenReturn(expected);

        StoredPlaylistPreview result = service.prepareNewPlaylist(
                "https://open.spotify.com/playlist/" + SOURCE_ID + "?si=test",
                "  Meu Rock  ",
                PlaylistVisibility.PUBLIC);

        assertSame(expected, result);

        var order = inOrder(converter, store);

        order.verify(converter).previewNewPlaylist(SOURCE_ID);

        order.verify(store).save(preview,
                "Meu Rock", PlaylistVisibility.PUBLIC);

        verifyNoMoreInteractions(converter, store);
    }

    @Test
    void shouldPrepareExistingPlaylistFromLinksWithoutCreationOptions() {
        PlaylistPreview preview = new PlaylistPreview(SOURCE_ID,
                DESTINATION_ID, List.of(), Set.of());

        StoredPlaylistPreview expected = new StoredPlaylistPreview(UUID.randomUUID(),
                preview, null, null, Instant.parse("2026-10-26T18:10:00Z"));

        when(converter.previewExistingPlaylist(SOURCE_ID, DESTINATION_ID))
                .thenReturn(preview);

        when(store.save(preview, null, null)).thenReturn(expected);

        StoredPlaylistPreview result = service.prepareExistingPlaylist(
                "https://open.spotify.com/playlist/" + SOURCE_ID,
                "https://www.youtube.com/playlist?list="
                        + DESTINATION_ID + "&si=test");

        assertSame(expected, result);

        var order = inOrder(converter, store);

        order.verify(converter)
                .previewExistingPlaylist(SOURCE_ID, DESTINATION_ID);

        order.verify(store).save(preview, null, null);

        verifyNoMoreInteractions(converter, store);
    }

    @Test
    void shouldRejectBlankNameBeforeAnalysisOrStorage() {
        assertThrows(IllegalArgumentException.class,
                () -> service.prepareNewPlaylist(SOURCE_ID,
                        "   ", PlaylistVisibility.PRIVATE));

        verifyNoInteractions(converter, store);
    }

    @Test
    void shouldRejectInvalidDestinationBeforeAnalysisOrStorage() {
        assertThrows(IllegalArgumentException.class,
                () -> service.prepareExistingPlaylist(SOURCE_ID,
                        "https://www.youtube.com/watch?v=video-A"));

        verifyNoInteractions(converter, store);
    }

    @Test
    void shouldPreserveSearchFailureWithoutStoringPreview() {
        YoutubeSearchException original = new YoutubeSearchException("Falha simulada na busca.",
                new IOException("Falha simulada"));

        when(converter.previewNewPlaylist(SOURCE_ID)).thenThrow(original);

        YoutubeSearchException result = assertThrows(
                YoutubeSearchException.class,
                () -> service.prepareNewPlaylist(SOURCE_ID,
                        "Meu Rock", PlaylistVisibility.PRIVATE));

        assertSame(original, result);

        verify(converter).previewNewPlaylist(SOURCE_ID);
        verifyNoMoreInteractions(converter);
        verifyNoInteractions(store);
    }

    @Test
    void shouldPreserveDestinationReadFailureWithoutStoringPreview() {
        YoutubePlaylistReadException original =
                new YoutubePlaylistReadException("Falha simulada na leitura do destino.",
                        new IOException("Falha simulada"));

        when(converter.previewExistingPlaylist(SOURCE_ID, DESTINATION_ID)).thenThrow(original);

        YoutubePlaylistReadException result = assertThrows(
                YoutubePlaylistReadException.class,
                () -> service.prepareExistingPlaylist(SOURCE_ID, DESTINATION_ID));

        assertSame(original, result);

        verify(converter)
                .previewExistingPlaylist(SOURCE_ID, DESTINATION_ID);

        verifyNoMoreInteractions(converter);
        verifyNoInteractions(store);
    }
}
