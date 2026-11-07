package com.jorge.playlistconverter.service.conversion;

import com.jorge.playlistconverter.converter.history.PlaylistDestinationRegistry;
import com.jorge.playlistconverter.errors.input.InvalidPlaylistInputException;
import com.jorge.playlistconverter.input.SpotifyPlaylistParser;
import com.jorge.playlistconverter.model.SyncJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PlaylistDestinationServiceTest {

    private static final String SOURCE_ID = "1234567890123456789012";

    private PlaylistDestinationRegistry destinationRegistry;
    private PlaylistDestinationService service;

    @BeforeEach
    void setUp() {
        destinationRegistry = mock(PlaylistDestinationRegistry.class);
        service = new PlaylistDestinationService(new SpotifyPlaylistParser(), destinationRegistry);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            SOURCE_ID,
            "https://open.spotify.com/playlist/" + SOURCE_ID + "?si=example"
    })
    void shouldFindSavedDestinationsBySpotifyLinkOrId(String sourceInput) {
        List<SyncJob> savedDestinations = List.of(
                new SyncJob(10L, SOURCE_ID, "youtube-destination-1", "SPOTIFY_TO_YOUTUBE"),
                new SyncJob(11L, SOURCE_ID, "youtube-destination-2", "SPOTIFY_TO_YOUTUBE"));
        when(destinationRegistry.findDestinations(SOURCE_ID))
                .thenReturn(savedDestinations);

        assertEquals(savedDestinations, service.findDestinations(sourceInput));

        verify(destinationRegistry).findDestinations(SOURCE_ID);
    }

    @Test
    void shouldReturnEmptyListWhenNoDestinationsAreSaved() {
        when(destinationRegistry.findDestinations(SOURCE_ID))
                .thenReturn(List.of());

        assertTrue(service.findDestinations(SOURCE_ID).isEmpty());

        verify(destinationRegistry).findDestinations(SOURCE_ID);
    }

    @Test
    void shouldRejectInvalidSourceBeforeLookingUpDestinations() {
        assertThrows(InvalidPlaylistInputException.class,
                () -> service.findDestinations("origem-invalida"));

        verifyNoInteractions(destinationRegistry);
    }
}
