package com.jorge.playlistconverter.controller.conversion;

import com.jorge.playlistconverter.converter.preview.PlaylistPreview;
import com.jorge.playlistconverter.converter.preview.StoredPlaylistPreview;
import com.jorge.playlistconverter.dto.response.PlaylistPreviewResponse;
import com.jorge.playlistconverter.enums.PlaylistVisibility;
import com.jorge.playlistconverter.mapper.PlaylistPreviewResponseMapper;
import com.jorge.playlistconverter.service.conversion.PlaylistPreviewService;
import com.jorge.playlistconverter.dto.response.ApiErrorResponse;
import com.jorge.playlistconverter.dto.response.FieldValidationErrorResponse;
import com.jorge.playlistconverter.errors.http.ApiExceptionHandler;
import com.jorge.playlistconverter.errors.input.InvalidPlaylistInputException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import tools.jackson.databind.json.JsonMapper;
import com.jorge.playlistconverter.errors.spotify.SpotifyRequestLimitException;
import com.jorge.playlistconverter.errors.youtube.YoutubeQuotaExceededException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import com.jorge.playlistconverter.dto.request.ExistingPlaylistPreviewRequest;
import com.jorge.playlistconverter.dto.request.NewPlaylistPreviewRequest;
import com.jorge.playlistconverter.errors.spotify.SpotifyPlaylistReadException;
import com.jorge.playlistconverter.errors.youtube.YoutubePlaylistReadException;
import com.jorge.playlistconverter.errors.youtube.YoutubeSearchException;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlaylistPreviewControllerTest {

    private static final String SOURCE_ID = "1234567890123456789012";

    private static final UUID PREVIEW_ID =
            UUID.fromString("3ebf4e4c-7ebd-4c52-b5b0-d943d4647206");

    private static final Instant EXPIRES_AT =
            Instant.parse("2026-10-27T18:10:00Z");

    private PlaylistPreviewService previewService;
    private LocalValidatorFactoryBean validator;
    private MockMvc mockMvc;
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        previewService = mock(PlaylistPreviewService.class);

        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        PlaylistPreviewController controller = new PlaylistPreviewController(previewService,
                new PlaylistPreviewResponseMapper());

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        jsonMapper = JsonMapper.builder().build();
    }

    @AfterEach
    void closeValidation() {
        validator.close();
    }

    @Test
    void shouldPrepareNewPlaylistAndDefaultToPrivate() throws Exception {
        PlaylistPreview preview = new PlaylistPreview(SOURCE_ID, List.of());

        StoredPlaylistPreview stored = new StoredPlaylistPreview(PREVIEW_ID, preview,
                "Meu Rock", PlaylistVisibility.PRIVATE, EXPIRES_AT);

        when(previewService.prepareNewPlaylist(SOURCE_ID,
                "Meu Rock", PlaylistVisibility.PRIVATE)).thenReturn(stored);

        String json = """
                {
                  "sourcePlaylist": "1234567890123456789012",
                  "destinationName": "Meu Rock"
                }
                """;

        var result = mockMvc.perform(
                        post("/api/conversions/previews/new")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON)).andReturn();

        PlaylistPreviewResponse response = jsonMapper.readValue(result.getResponse().getContentAsString(),
                PlaylistPreviewResponse.class);

        assertEquals(PREVIEW_ID, response.previewId());
        assertEquals(SOURCE_ID, response.sourcePlaylistId());
        assertEquals("Meu Rock", response.destinationName());
        assertEquals(PlaylistVisibility.PRIVATE, response.visibility());
        assertEquals(EXPIRES_AT, response.expiresAt());
        assertNull(response.destinationPlaylistId());
        assertEquals(0, response.total());

        verify(previewService).prepareNewPlaylist(SOURCE_ID, "Meu Rock", PlaylistVisibility.PRIVATE);

        verifyNoMoreInteractions(previewService);
    }

    @Test
    void shouldPrepareExistingPlaylistWithoutCreationOptions()
            throws Exception {
        PlaylistPreview preview = new PlaylistPreview(SOURCE_ID,
                "PL_destination", List.of(), Set.of());

        StoredPlaylistPreview stored = new StoredPlaylistPreview(PREVIEW_ID, preview,
                null, null, EXPIRES_AT);

        when(previewService.prepareExistingPlaylist(SOURCE_ID,
                "PL_destination")).thenReturn(stored);

        String json = """
                {
                  "sourcePlaylist": "1234567890123456789012",
                  "destinationPlaylist": "PL_destination"
                }
                """;

        var result = mockMvc.perform(post("/api/conversions/previews/existing")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON)).andReturn();

        PlaylistPreviewResponse response = jsonMapper.readValue(result.getResponse().getContentAsString(),
                PlaylistPreviewResponse.class);

        assertEquals(PREVIEW_ID, response.previewId());
        assertEquals(SOURCE_ID, response.sourcePlaylistId());
        assertEquals("PL_destination", response.destinationPlaylistId());
        assertEquals(EXPIRES_AT, response.expiresAt());
        assertNull(response.destinationName());
        assertNull(response.visibility());

        verify(previewService).prepareExistingPlaylist(SOURCE_ID, "PL_destination");

        verifyNoMoreInteractions(previewService);
    }

    @Test
    void shouldRejectBlankSourceBeforePreparingNewPlaylist() throws Exception {
        String json = """
            {
              "sourcePlaylist": "   ",
              "destinationName": "Meu Rock",
              "visibility": "PRIVATE"
            }
            """;

        ApiErrorResponse response = submitInvalidRequest("/api/conversions/previews/new",
                json);

        assertEquals(400, response.status());
        assertEquals("VALIDATION_ERROR", response.code());
        assertEquals("Existem campos inválidos na requisição.",
                response.message());
        assertEquals("/api/conversions/previews/new", response.path());

        assertEquals(List.of(new FieldValidationErrorResponse(
                        "sourcePlaylist",
                        "Informe o link ou o ID da playlist de origem.")),
                response.fieldErrors());
    }

    @Test
    void shouldRejectBlankExistingDestinationBeforePreparingPreview() throws Exception {
        String json = """
            {
              "sourcePlaylist": "1234567890123456789012",
              "destinationPlaylist": "   "
            }
            """;

        ApiErrorResponse response = submitInvalidRequest("/api/conversions/previews/existing", json);

        assertEquals(400, response.status());
        assertEquals("VALIDATION_ERROR", response.code());
        assertEquals("/api/conversions/previews/existing", response.path());

        assertEquals(List.of(new FieldValidationErrorResponse(
                        "destinationPlaylist",
                        "Informe o link ou o ID da playlist de destino.")), response.fieldErrors());
    }

    @Test
    void shouldRejectUnknownVisibilityBeforePreparingPreview() throws Exception {
        String json = """
            {
              "sourcePlaylist": "1234567890123456789012",
              "destinationName": "Meu Rock",
              "visibility": "UNKNOWN"
            }
            """;

        ApiErrorResponse response = submitInvalidRequest("/api/conversions/previews/new", json);

        assertEquals(400, response.status());
        assertEquals("INVALID_REQUEST_BODY", response.code());
        assertEquals(
                "O corpo JSON está ausente, malformado ou contém um valor inválido.",
                response.message());
        assertEquals("/api/conversions/previews/new", response.path());
        assertTrue(response.fieldErrors().isEmpty());
    }

    @Test
    void shouldRejectMalformedJsonBeforePreparingPreview() throws Exception {
        ApiErrorResponse response = submitInvalidRequest("/api/conversions/previews/new", "{");

        assertEquals(400, response.status());
        assertEquals("INVALID_REQUEST_BODY", response.code());
        assertEquals("O corpo JSON está ausente, malformado ou contém um valor inválido.",
                response.message());
        assertEquals("/api/conversions/previews/new", response.path());
        assertTrue(response.fieldErrors().isEmpty());
    }

    @Test
    void shouldReturnBadRequestWhenSourceInputIsInvalid() throws Exception {
        InvalidPlaylistInputException original =
                new InvalidPlaylistInputException("detalhe-interno-origem");

        when(previewService.prepareNewPlaylist("invalid-source",
                "Meu Rock", PlaylistVisibility.PRIVATE)).thenThrow(original);

        String json = """
            {
              "sourcePlaylist": "invalid-source",
              "destinationName": "Meu Rock",
              "visibility": "PRIVATE"
            }
            """;

        var result = mockMvc.perform(post("/api/conversions/previews/new")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)).andReturn();

        String body = result.getResponse().getContentAsString();

        ApiErrorResponse response = jsonMapper.readValue(body,
                ApiErrorResponse.class);

        assertEquals(400, response.status());
        assertEquals("INVALID_PLAYLIST_INPUT", response.code());
        assertEquals("Informe um link ou ID válido para a plataforma da playlist.",
                response.message());
        assertEquals("/api/conversions/previews/new", response.path());
        assertTrue(response.fieldErrors().isEmpty());
        assertFalse(body.contains(original.getMessage()));

        verify(previewService).prepareNewPlaylist("invalid-source",
                "Meu Rock", PlaylistVisibility.PRIVATE);

        verifyNoMoreInteractions(previewService);
    }

    @Test
    void shouldReturnBadRequestWhenDestinationInputIsInvalid() throws Exception {
        InvalidPlaylistInputException original =
                new InvalidPlaylistInputException("detalhe-interno-destino");

        when(previewService.prepareExistingPlaylist(SOURCE_ID,
                "invalid-destination")).thenThrow(original);

        String json = """
            {
              "sourcePlaylist": "1234567890123456789012",
              "destinationPlaylist": "invalid-destination"
            }
            """;

        var result = mockMvc.perform(post("/api/conversions/previews/existing")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)).andReturn();

        String body = result.getResponse().getContentAsString();

        ApiErrorResponse response = jsonMapper.readValue(body, ApiErrorResponse.class);

        assertEquals(400, response.status());
        assertEquals("INVALID_PLAYLIST_INPUT", response.code());
        assertEquals("Informe um link ou ID válido para a plataforma da playlist.",
                response.message());
        assertEquals("/api/conversions/previews/existing", response.path());
        assertTrue(response.fieldErrors().isEmpty());
        assertFalse(body.contains(original.getMessage()));

        verify(previewService).prepareExistingPlaylist(SOURCE_ID,
                "invalid-destination");

        verifyNoMoreInteractions(previewService);
    }

    @Test
    void shouldReturnQuotaErrorWithoutRetryAfterForYoutube() throws Exception {
        YoutubeQuotaExceededException original = new YoutubeQuotaExceededException(
                        "detalhe-interno-youtube", new IOException("Falha simulada"));

        when(previewService.prepareNewPlaylist(SOURCE_ID, "Meu Rock",
                PlaylistVisibility.PRIVATE)).thenThrow(original);

        MvcResult result = submitRateLimitedPreviewRequest();

        String body = result.getResponse().getContentAsString();

        ApiErrorResponse response = jsonMapper.readValue(body, ApiErrorResponse.class);

        assertEquals(429, response.status());
        assertEquals("YOUTUBE_QUOTA_EXCEEDED", response.code());
        assertEquals("A quota da API do YouTube foi esgotada. "
                        + "A operação foi interrompida. "
                        + "Confira o destino antes de iniciar uma nova tentativa.",
                response.message());
        assertEquals("/api/conversions/previews/new", response.path());
        assertTrue(response.fieldErrors().isEmpty());

        assertNull(result.getResponse().getHeader(HttpHeaders.RETRY_AFTER));
        assertFalse(body.contains(original.getMessage()));
    }

    @Test
    void shouldPreservePositiveRetryAfterForSpotify() throws Exception {
        SpotifyRequestLimitException original =
                new SpotifyRequestLimitException("detalhe-interno-spotify", 30,
                        new IOException("Falha simulada"));

        when(previewService.prepareNewPlaylist(SOURCE_ID, "Meu Rock",
                PlaylistVisibility.PRIVATE)).thenThrow(original);

        MvcResult result = submitRateLimitedPreviewRequest();

        String body = result.getResponse().getContentAsString();

        ApiErrorResponse response = jsonMapper.readValue(body, ApiErrorResponse.class);

        assertEquals(429, response.status());
        assertEquals("SPOTIFY_RATE_LIMITED", response.code());
        assertEquals("O Spotify limitou as requisições. "
                        + "Aguarde antes de iniciar uma nova tentativa.",
                response.message());
        assertEquals("/api/conversions/previews/new", response.path());
        assertTrue(response.fieldErrors().isEmpty());

        assertEquals("30",
                result.getResponse().getHeader(HttpHeaders.RETRY_AFTER));

        assertFalse(body.contains(original.getMessage()));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1})
    void shouldOmitRetryAfterWhenSpotifyDelayIsMissingOrInvalid(Integer retryAfterSeconds) throws Exception {
        SpotifyRequestLimitException original =
                new SpotifyRequestLimitException(
                        "Falha simulada.",
                        retryAfterSeconds,
                        new IOException("Falha simulada"));

        when(previewService.prepareNewPlaylist(
                SOURCE_ID, "Meu Rock",
                PlaylistVisibility.PRIVATE)).thenThrow(original);

        MvcResult result = submitRateLimitedPreviewRequest();

        ApiErrorResponse response = jsonMapper.readValue(
                result.getResponse().getContentAsString(),
                ApiErrorResponse.class);

        assertEquals(429, response.status());
        assertEquals("SPOTIFY_RATE_LIMITED", response.code());
        assertEquals("/api/conversions/previews/new", response.path());
        assertTrue(response.fieldErrors().isEmpty());

        assertNull(result.getResponse().getHeader(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void shouldReturnBadGatewayWhenSpotifyPlaylistReadFails() throws Exception {
        SpotifyPlaylistReadException original = new SpotifyPlaylistReadException(
                        "detalhe-interno-plataforma",
                        new IOException("causa-interna-simulada"));

        when(previewService.prepareNewPlaylist(SOURCE_ID, "Meu Rock",
                PlaylistVisibility.PRIVATE)).thenThrow(original);

        String json = jsonMapper.writeValueAsString(
                new NewPlaylistPreviewRequest(SOURCE_ID,
                        "Meu Rock", PlaylistVisibility.PRIVATE));

        ApiErrorResponse response = submitPlatformFailure("/api/conversions/previews/new", json);

        assertEquals("SPOTIFY_PLAYLIST_READ_FAILED", response.code());
        assertEquals("Não foi possível concluir a leitura da playlist no Spotify.",
                response.message());

        verify(previewService).prepareNewPlaylist(SOURCE_ID,
                "Meu Rock", PlaylistVisibility.PRIVATE);

        verifyNoMoreInteractions(previewService);
    }

    @Test
    void shouldReturnBadGatewayWhenYoutubePlaylistReadFails() throws Exception {
        YoutubePlaylistReadException original =
                new YoutubePlaylistReadException(
                        "detalhe-interno-plataforma",
                        new IOException("causa-interna-simulada")
                );

        when(previewService.prepareExistingPlaylist(SOURCE_ID, "PL_destination")).thenThrow(original);

        String json = jsonMapper.writeValueAsString(
                new ExistingPlaylistPreviewRequest(SOURCE_ID, "PL_destination"));

        ApiErrorResponse response = submitPlatformFailure("/api/conversions/previews/existing", json);

        assertEquals("YOUTUBE_PLAYLIST_READ_FAILED", response.code());
        assertEquals("Não foi possível concluir a leitura da playlist no YouTube.",
                response.message());

        verify(previewService).prepareExistingPlaylist(SOURCE_ID, "PL_destination");

        verifyNoMoreInteractions(previewService);
    }

    @Test
    void shouldReturnBadGatewayWhenYoutubeSearchFails() throws Exception {
        YoutubeSearchException original = new YoutubeSearchException("detalhe-interno-plataforma",
                new IOException("causa-interna-simulada"));

        when(previewService.prepareNewPlaylist(SOURCE_ID,
                "Meu Rock", PlaylistVisibility.PRIVATE)).thenThrow(original);

        String json = jsonMapper.writeValueAsString(
                new NewPlaylistPreviewRequest(SOURCE_ID,
                        "Meu Rock", PlaylistVisibility.PRIVATE));

        ApiErrorResponse response = submitPlatformFailure("/api/conversions/previews/new", json);

        assertEquals("YOUTUBE_SEARCH_FAILED", response.code());
        assertEquals("Não foi possível concluir a busca de músicas no YouTube.",
                response.message());

        verify(previewService).prepareNewPlaylist(SOURCE_ID,
                "Meu Rock", PlaylistVisibility.PRIVATE);

        verifyNoMoreInteractions(previewService);
    }

    private ApiErrorResponse submitInvalidRequest(String path, String body) throws Exception {
        var result = mockMvc.perform(post(path)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON)).andReturn();

        verifyNoInteractions(previewService);

        return jsonMapper.readValue(result.getResponse().getContentAsString(),
                ApiErrorResponse.class);
    }

    private MvcResult submitRateLimitedPreviewRequest() throws Exception {
        String json = """
            {
              "sourcePlaylist": "1234567890123456789012",
              "destinationName": "Meu Rock",
              "visibility": "PRIVATE"
            }
            """;

        var result = mockMvc.perform(
                        post("/api/conversions/previews/new")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON)).andReturn();

        verify(previewService, times(1)).prepareNewPlaylist(
                SOURCE_ID,
                "Meu Rock",
                PlaylistVisibility.PRIVATE);

        verifyNoMoreInteractions(previewService);

        return result;
    }

    private ApiErrorResponse submitPlatformFailure(String path, String json) throws Exception {
        var result = mockMvc.perform(post(path)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON)).andReturn();

        String body = result.getResponse().getContentAsString();

        ApiErrorResponse response = jsonMapper.readValue(
                body,
                ApiErrorResponse.class);

        assertEquals(502, response.status());
        assertEquals(path, response.path());
        assertTrue(response.fieldErrors().isEmpty());
        assertFalse(body.contains("detalhe-interno-plataforma"));
        assertFalse(body.contains("causa-interna-simulada"));

        return response;
    }
}
