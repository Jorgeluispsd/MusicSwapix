package com.jorge.playlistconverter.controller;

import com.jorge.playlistconverter.converter.history.PlaylistDestinationRegistry;
import com.jorge.playlistconverter.dto.response.ApiErrorResponse;
import com.jorge.playlistconverter.dto.response.PlaylistDestinationResponse;
import com.jorge.playlistconverter.errors.http.ApiExceptionHandler;
import com.jorge.playlistconverter.input.SpotifyPlaylistParser;
import com.jorge.playlistconverter.model.SyncJob;
import com.jorge.playlistconverter.service.PlaylistDestinationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class PlaylistDestinationControllerTest  {

    private static final String DESTINATIONS_URL = "/api/conversions/destinations";
    private static final String SOURCE_ID = "1234567890123456789012";

    private PlaylistDestinationRegistry destinationRegistry;
    private MockMvc mockMvc;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        destinationRegistry = mock(PlaylistDestinationRegistry.class);

        var service = new PlaylistDestinationService(
                new SpotifyPlaylistParser(), destinationRegistry);

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new PlaylistDestinationController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnSavedDestinationsForSpotifyLink() throws Exception {
        String sourceLink = "https://open.spotify.com/playlist/" + SOURCE_ID + "?si=example";
        when(destinationRegistry.findDestinations(SOURCE_ID)).thenReturn(List.of(
                new SyncJob(10L, SOURCE_ID, "youtube-destination-1", "SPOTIFY_TO_YOUTUBE"),
                new SyncJob(11L, SOURCE_ID, "youtube-destination-2", "SPOTIFY_TO_YOUTUBE")));

        var result = mockMvc.perform(get(DESTINATIONS_URL)
                        .param("sourcePlaylist", sourceLink))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        var response = jsonMapper.readValue(
                result.getResponse().getContentAsString(),
                PlaylistDestinationResponse[].class);

        assertArrayEquals(new PlaylistDestinationResponse[]{
                new PlaylistDestinationResponse("youtube-destination-1"),
                new PlaylistDestinationResponse("youtube-destination-2")
        }, response);

        verify(destinationRegistry).findDestinations(SOURCE_ID);
    }

    @Test
    void shouldReturnEmptyListWhenNoDestinationsAreSaved() throws Exception {
        when(destinationRegistry.findDestinations(SOURCE_ID)).thenReturn(List.of());

        mockMvc.perform(get(DESTINATIONS_URL).param("sourcePlaylist", SOURCE_ID))
                .andExpect(status().isOk())
                .andExpect(content().string("[]"));
    }

    @Test
    void shouldRejectMissingSourceBeforeLookingUpDestinations() throws Exception {
        var result = mockMvc.perform(get(DESTINATIONS_URL))
                .andExpect(status().isBadRequest())
                .andReturn();

        ApiErrorResponse response = jsonMapper.readValue(
                result.getResponse().getContentAsString(), ApiErrorResponse.class);

        assertEquals("INVALID_PLAYLIST_INPUT", response.code());
        verifyNoInteractions(destinationRegistry);
    }
}
