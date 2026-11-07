package com.jorge.playlistconverter.config;

import com.jorge.playlistconverter.converter.ConversionResult;
import com.jorge.playlistconverter.converter.PlaylistConversionValidator;
import com.jorge.playlistconverter.converter.PlaylistConverter;
import com.jorge.playlistconverter.converter.history.PlaylistDestinationRegistry;
import com.jorge.playlistconverter.converter.preview.InMemoryPlaylistPreviewStore;
import com.jorge.playlistconverter.converter.preview.PlaylistPreview;
import com.jorge.playlistconverter.converter.preview.StoredPlaylistPreview;
import com.jorge.playlistconverter.enums.PlaylistVisibility;
import com.jorge.playlistconverter.input.SpotifyPlaylistParser;
import com.jorge.playlistconverter.input.YoutubePlaylistParser;
import com.jorge.playlistconverter.service.PlaylistConfirmationService;
import com.jorge.playlistconverter.service.PlaylistPreviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlaylistPreviewConfigTest {

    private static final String SOURCE_ID = "1234567890123456789012";

    private PlaylistConverter converter;
    private ApplicationContextRunner contextRunner;

    @BeforeEach
    void setUp() {
        converter = mock(PlaylistConverter.class);

        contextRunner = new ApplicationContextRunner()
                .withUserConfiguration(PlaylistPreviewConfig.class)
                .withBean(PlaylistConverter.class, () -> converter)
                .withBean(
                        PlaylistDestinationRegistry.class,
                        () -> mock(PlaylistDestinationRegistry.class))
                .withBean(
                        SpotifyPlaylistParser.class,
                        SpotifyPlaylistParser::new)
                .withBean(
                        YoutubePlaylistParser.class,
                        YoutubePlaylistParser::new)
                .withBean(
                        PlaylistConversionValidator.class,
                        PlaylistConversionValidator::new);
    }

    @Test
    void shouldSharePreviewStoreBetweenPreparationAndConfirmation() {
        PlaylistPreview preview = new PlaylistPreview(
                SOURCE_ID, List.of());

        ConversionResult expected = new ConversionResult(
                SOURCE_ID, "new-playlist", List.of(), 0, 0);

        when(converter.previewNewPlaylist(SOURCE_ID))
                .thenReturn(preview);

        when(converter.executeNewPlaylistPreview(
                preview, "Meu Rock", PlaylistVisibility.PRIVATE)).thenReturn(expected);

        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context)
                    .hasSingleBean(InMemoryPlaylistPreviewStore.class);

            PlaylistPreviewService preparation =
                    context.getBean(PlaylistPreviewService.class);

            PlaylistConfirmationService confirmation =
                    context.getBean(PlaylistConfirmationService.class);

            InMemoryPlaylistPreviewStore store =
                    context.getBean(InMemoryPlaylistPreviewStore.class);

            StoredPlaylistPreview stored = preparation.prepareNewPlaylist(SOURCE_ID, "Meu Rock",
                    PlaylistVisibility.PRIVATE);

            assertEquals(stored, store.find(stored.id()).orElseThrow());

            ConversionResult result = confirmation.confirm(stored.id());

            assertSame(expected, result);
            assertTrue(store.find(stored.id()).isEmpty());
        });

        verify(converter).previewNewPlaylist(SOURCE_ID);

        verify(converter).executeNewPlaylistPreview(
                preview, "Meu Rock", PlaylistVisibility.PRIVATE);

        verifyNoMoreInteractions(converter);
    }

    @Test
    void shouldUseConfiguredPreviewLifetime() {
        contextRunner
                .withPropertyValues("playlist.converter.preview-lifetime=PT7M")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    InMemoryPlaylistPreviewStore store =
                            context.getBean(InMemoryPlaylistPreviewStore.class);

                    Instant beforeSave = Instant.now();

                    StoredPlaylistPreview stored = store.save(new PlaylistPreview(SOURCE_ID, List.of()),
                            "Meu Rock",
                            PlaylistVisibility.PRIVATE);

                    Instant afterSave = Instant.now();
                    Duration lifetime = Duration.ofMinutes(7);

                    assertFalse(stored.expiresAt().isBefore(beforeSave.plus(lifetime)));

                    assertFalse(stored.expiresAt().isAfter(afterSave.plus(lifetime)));});

        verifyNoInteractions(converter);
    }

    @Test
    void shouldRejectZeroLifetimeDuringContextStartup() {
        contextRunner
                .withPropertyValues("playlist.converter.preview-lifetime=PT0S")
                .run(context -> {
                    assertThat(context).hasFailed();

                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(
                                    IllegalArgumentException.class);});

        verifyNoInteractions(converter);
    }
}
