package com.jorge.playlistconverter.converter.preview;

import com.jorge.playlistconverter.enums.ConversionDirection;
import com.jorge.playlistconverter.enums.PlaylistVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InMemoryPlaylistPreviewStoreTest {

    private static final Instant NOW =
            Instant.parse("2026-10-26T18:00:00Z");

    private static final Duration LIFETIME = Duration.ofMinutes(10);

    private Clock clock;
    private InMemoryPlaylistPreviewStore store;

    @BeforeEach
    void setUp() {
        clock = mock(Clock.class);

        when(clock.instant()).thenReturn(NOW);

        store = new InMemoryPlaylistPreviewStore(clock, LIFETIME);
    }

    @Test
    void shouldSaveAndRetrievePreviewWithCalculatedExpiration() {
        PlaylistPreview preview = new PlaylistPreview("source-playlist",
                List.of());

        StoredPlaylistPreview saved = store.save(preview,
                "Minha playlist", PlaylistVisibility.PRIVATE);

        StoredPlaylistPreview retrieved =
                store.find(saved.id()).orElseThrow();

        assertNotNull(saved.id());
        assertSame(preview, retrieved.preview());
        assertEquals("Minha playlist", retrieved.destinationName());
        assertEquals(PlaylistVisibility.PRIVATE, retrieved.visibility());
        assertEquals(NOW.plus(LIFETIME), retrieved.expiresAt());

        assertEquals(saved, store.find(saved.id()).orElseThrow());
        assertEquals(ConversionDirection.SPOTIFY_TO_YOUTUBE, saved.direction());
    }

    @Test
    void shouldStoreExistingDestinationWithoutCreationOptions() {
        PlaylistPreview preview = new PlaylistPreview(
                "source-playlist",
                "destination-playlist", List.of(), Set.of());

        StoredPlaylistPreview saved = store.save(preview, null, null);

        StoredPlaylistPreview retrieved =
                store.find(saved.id()).orElseThrow();

        assertEquals("destination-playlist",
                retrieved.preview().destinationPlaylistId());

        assertNull(retrieved.destinationName());
        assertNull(retrieved.visibility());
    }

    @Test
    void shouldNotFindPreviewAtExpirationTime() {
        StoredPlaylistPreview saved = store.save(
                new PlaylistPreview("source-playlist", List.of()),
                "Minha playlist", PlaylistVisibility.PRIVATE);

        when(clock.instant()).thenReturn(NOW.plus(LIFETIME));

        assertTrue(store.find(saved.id()).isEmpty());

        when(clock.instant()).thenReturn(NOW);

        assertTrue(store.find(saved.id()).isEmpty());
    }

    @Test
    void shouldConsumePreviewOnlyOnce() {
        StoredPlaylistPreview saved = store.save(
                new PlaylistPreview("source-playlist", List.of()),
                "Minha playlist", PlaylistVisibility.PRIVATE);

        StoredPlaylistPreview consumed =
                store.consume(saved.id()).orElseThrow();

        assertEquals(saved, consumed);

        assertTrue(store.consume(saved.id()).isEmpty());
        assertTrue(store.find(saved.id()).isEmpty());
    }

    @Test
    void shouldNotConsumeExpiredPreview() {
        StoredPlaylistPreview saved = store.save(
                new PlaylistPreview("source-playlist", List.of()),
                "Minha playlist", PlaylistVisibility.PRIVATE);

        when(clock.instant()).thenReturn(NOW.plus(LIFETIME));

        assertTrue(store.consume(saved.id()).isEmpty());

        when(clock.instant()).thenReturn(NOW);

        assertTrue(store.find(saved.id()).isEmpty());
    }

    @Test
    void shouldReturnEmptyForUnknownPreviewId() {
        UUID unknownId = UUID.randomUUID();

        assertTrue(store.find(unknownId).isEmpty());
        assertTrue(store.consume(unknownId).isEmpty());
    }

    @Test
    void shouldAllowOnlyOneConcurrentConsumption() throws Exception {
        StoredPlaylistPreview saved = store.save(
                new PlaylistPreview("source-playlist", List.of()),
                "Minha playlist",
                PlaylistVisibility.PRIVATE
        );

        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            var firstAttempt = executor.submit(() -> {
                start.await();
                return store.consume(saved.id());
            });

            var secondAttempt = executor.submit(() -> {
                start.await();
                return store.consume(saved.id());
            });

            start.countDown();

            Optional<StoredPlaylistPreview> firstResult =
                    firstAttempt.get(5, TimeUnit.SECONDS);

            Optional<StoredPlaylistPreview> secondResult =
                    secondAttempt.get(5, TimeUnit.SECONDS);

            var consumedPreviews = Stream.of(firstResult, secondResult)
                    .flatMap(Optional::stream).toList();

            assertEquals(List.of(saved), consumedPreviews);
            assertTrue(store.find(saved.id()).isEmpty());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @ParameterizedTest
    @MethodSource("invalidLifetimes")
    void shouldRejectNonPositiveLifetime(Duration lifetime) {
        assertThrows(IllegalArgumentException.class,
                () -> new InMemoryPlaylistPreviewStore(clock, lifetime));
    }

    @Test
    void shouldRejectNullPreviewId() {
        assertThrows(NullPointerException.class,
                () -> store.find(null));

        assertThrows(NullPointerException.class,
                () -> store.consume(null));
    }

    @Test
    void shouldPreserveReverseDirectionWhenConsumingPreview() {
        PlaylistPreview preview = new PlaylistPreview("youtube-playlist", java.util.List.of());

        StoredPlaylistPreview saved = store.save(
                preview, "Minha playlist", PlaylistVisibility.PRIVATE,
                ConversionDirection.YOUTUBE_TO_SPOTIFY);

        StoredPlaylistPreview consumed = store.consume(saved.id()).orElseThrow();

        assertEquals(ConversionDirection.YOUTUBE_TO_SPOTIFY, consumed.direction());
    }

    private static Stream<Duration> invalidLifetimes() {
        return Stream.of(Duration.ZERO, Duration.ofSeconds(-1));
    }
}
