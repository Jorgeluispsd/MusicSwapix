package com.jorge.playlistconverter.controller;

import com.jorge.playlistconverter.converter.ConversionResult;
import com.jorge.playlistconverter.dto.response.ApiErrorResponse;
import com.jorge.playlistconverter.dto.response.PlaylistConversionResponse;
import com.jorge.playlistconverter.dto.response.UnmatchedTrackResponse;
import com.jorge.playlistconverter.errors.conversion.PlaylistPreviewChangedException;
import com.jorge.playlistconverter.errors.conversion.PlaylistPreviewUnavailableException;
import com.jorge.playlistconverter.errors.http.ApiExceptionHandler;
import com.jorge.playlistconverter.errors.youtube.YoutubePlaylistCreationException;
import com.jorge.playlistconverter.errors.youtube.YoutubeTrackInsertionException;
import com.jorge.playlistconverter.mapper.PlaylistConversionResponseMapper;
import com.jorge.playlistconverter.model.MatchResult;
import com.jorge.playlistconverter.model.Song;
import com.jorge.playlistconverter.service.PlaylistConfirmationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlaylistConfirmationControllerTest {


    private static final UUID PREVIEW_ID =
            UUID.fromString("3ebf4e4c-7ebd-4c52-b5b0-d943d4647206");

    private static final String CONFIRM_URL =
            "/api/conversions/previews/" + PREVIEW_ID + "/confirm";

    private PlaylistConfirmationService confirmationService;
    private MockMvc mockMvc;
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        confirmationService = mock(PlaylistConfirmationService.class);

        PlaylistConfirmationController controller =
                new PlaylistConfirmationController(confirmationService,
                        new PlaylistConversionResponseMapper());

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        jsonMapper = JsonMapper.builder().build();
    }

    @Test
    void shouldConfirmPreviewWithoutRequestBody() throws Exception {
        ConversionResult conversion = new ConversionResult(
                "source-playlist",
                "destination-playlist",
                List.of(
                        new MatchResult(
                                new Song("source-A", "Song A", "Artist A", 180000),
                                "video-A", "Video A", 0.95
                        ),
                        new MatchResult(
                                new Song("source-B", "Song B", "Artist B", 180000),
                                "video-B", "Video B", 0.90
                        ),
                        new MatchResult(
                                new Song("source-C", "Song C", "Artist C", 180000),
                                null, null, 0.0)), 1, 1);

        when(confirmationService.confirm(PREVIEW_ID))
                .thenReturn(conversion);

        var result = mockMvc.perform(post(CONFIRM_URL))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                )).andReturn();

        PlaylistConversionResponse response = jsonMapper.readValue(result.getResponse().getContentAsString(),
                PlaylistConversionResponse.class);

        assertEquals("source-playlist", response.sourcePlaylistId());
        assertEquals("destination-playlist", response.destinationPlaylistId());
        assertEquals("https://www.youtube.com/playlist?list=destination-playlist",
                response.destinationPlaylistUrl());
        assertEquals(1, response.insertedCount());
        assertEquals(1, response.skippedCount());
        assertEquals(1, response.notFoundCount());

        assertEquals(List.of(new UnmatchedTrackResponse(
                        "source-C", "Song C", "Artist C"
                )), response.notFound());

        verify(confirmationService).confirm(PREVIEW_ID);
        verifyNoMoreInteractions(confirmationService);
    }

    @Test
    void shouldReturnNotFoundWhenPreviewIsUnavailable() throws Exception {
        when(confirmationService.confirm(PREVIEW_ID))
                .thenThrow(new PlaylistPreviewUnavailableException(
                        "detalhe-interno-da-previa"));

        var result = mockMvc.perform(post(CONFIRM_URL))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON)).andReturn();

        String body = result.getResponse().getContentAsString();

        ApiErrorResponse response = jsonMapper.readValue(body, ApiErrorResponse.class);

        assertEquals(404, response.status());
        assertEquals("PREVIEW_UNAVAILABLE", response.code());
        assertEquals("A prévia não está disponível para confirmação. Gere uma nova prévia.",
                response.message());
        assertEquals(CONFIRM_URL, response.path());
        assertTrue(response.fieldErrors().isEmpty());
        assertFalse(body.contains("detalhe-interno-da-previa"));

        verify(confirmationService).confirm(PREVIEW_ID);
        verifyNoMoreInteractions(confirmationService);
    }

    @Test
    void shouldRejectMalformedPreviewIdBeforeCallingService() throws Exception {
        String path = "/api/conversions/previews/abc/confirm";

        var result = mockMvc.perform(post(path))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON)).andReturn();

        ApiErrorResponse response = jsonMapper.readValue(
                result.getResponse().getContentAsString(),
                ApiErrorResponse.class);

        assertEquals(400, response.status());
        assertEquals("INVALID_PATH_PARAMETER", response.code());
        assertEquals("Um parâmetro da URL possui formato inválido.",
                response.message());
        assertEquals(path, response.path());
        assertTrue(response.fieldErrors().isEmpty());

        verifyNoInteractions(confirmationService);
    }

    @Test
    void shouldReturnConflictWhenDestinationChangedAfterPreview() throws Exception {
        when(confirmationService.confirm(PREVIEW_ID))
                .thenThrow(new PlaylistPreviewChangedException(
                        "detalhe-interno-da-alteracao"));

        var result = mockMvc.perform(post(CONFIRM_URL))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON)).andReturn();

        String body = result.getResponse().getContentAsString();

        ApiErrorResponse response = jsonMapper.readValue(body, ApiErrorResponse.class);

        assertEquals(409, response.status());
        assertEquals("PREVIEW_CHANGED", response.code());
        assertEquals("O destino mudou desde a análise. Gere uma nova prévia antes de confirmar.",
                response.message());
        assertEquals(CONFIRM_URL, response.path());
        assertTrue(response.fieldErrors().isEmpty());
        assertFalse(body.contains("detalhe-interno-da-alteracao"));

        verify(confirmationService, times(1)).confirm(PREVIEW_ID);
        verifyNoMoreInteractions(confirmationService);
    }

    @Test
    void shouldReturnBadGatewayWhenPlaylistCreationFails() throws Exception {
        when(confirmationService.confirm(PREVIEW_ID))
                .thenThrow(new YoutubePlaylistCreationException(
                        "detalhe-interno-da-criacao",
                        new IllegalStateException("causa-interna-simulada")));

        var result = mockMvc.perform(post(CONFIRM_URL))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON))
                .andReturn();

        String body = result.getResponse().getContentAsString();

        ApiErrorResponse response = jsonMapper.readValue(
                body, ApiErrorResponse.class);

        assertEquals(502, response.status());
        assertEquals("YOUTUBE_PLAYLIST_CREATION_FAILED", response.code());
        assertEquals(
                "Não foi possível confirmar a criação da playlist no YouTube. "
                        + "Confira sua conta antes de tentar criar outra.",
                response.message());
        assertEquals(CONFIRM_URL, response.path());
        assertTrue(response.fieldErrors().isEmpty());

        assertFalse(body.contains("detalhe-interno-da-criacao"));
        assertFalse(body.contains("causa-interna-simulada"));

        verify(confirmationService, times(1)).confirm(PREVIEW_ID);
        verifyNoMoreInteractions(confirmationService);
    }

    @Test
    void shouldReturnBadGatewayWhenTrackInsertionFails() throws Exception {
        when(confirmationService.confirm(PREVIEW_ID))
                .thenThrow(new YoutubeTrackInsertionException(
                        "detalhe-interno-da-insercao",
                        new IllegalStateException("causa-interna-simulada")));

        var result = mockMvc.perform(post(CONFIRM_URL))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON))
                .andReturn();

        String body = result.getResponse().getContentAsString();

        ApiErrorResponse response = jsonMapper.readValue(body, ApiErrorResponse.class);

        assertEquals(502, response.status());
        assertEquals("YOUTUBE_TRACK_INSERTION_FAILED", response.code());
        assertEquals(
                "Não foi possível concluir a inserção das músicas no YouTube. "
                        + "Algumas músicas podem ter sido inseridas. "
                        + "Confira o destino e gere uma nova prévia antes de continuar.",
                response.message());
        assertEquals(CONFIRM_URL, response.path());
        assertTrue(response.fieldErrors().isEmpty());

        assertFalse(body.contains("detalhe-interno-da-insercao"));
        assertFalse(body.contains("causa-interna-simulada"));

        verify(confirmationService, times(1)).confirm(PREVIEW_ID);
        verifyNoMoreInteractions(confirmationService);
    }
}
