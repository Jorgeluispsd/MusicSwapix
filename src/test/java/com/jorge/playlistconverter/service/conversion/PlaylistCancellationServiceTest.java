package com.jorge.playlistconverter.service.conversion;

import com.jorge.playlistconverter.converter.preview.InMemoryPlaylistPreviewStore;
import com.jorge.playlistconverter.converter.preview.PlaylistPreview;
import com.jorge.playlistconverter.enums.PlaylistVisibility;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaylistCancellationServiceTest {

    @Test
    void shouldDiscardPreviewAndAllowRepeatedCancellation() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC);
        var store = new InMemoryPlaylistPreviewStore(clock, Duration.ofMinutes(10));
        var service = new PlaylistCancellationService(store);
        var saved = store.save(
                new PlaylistPreview("source-playlist", List.of()),
                "Minha playlist",
                PlaylistVisibility.PRIVATE);

        service.cancel(saved.id());

        assertTrue(store.consume(saved.id()).isEmpty());
        assertDoesNotThrow(() -> service.cancel(saved.id()));
    }
}
