package com.jorge.playlistconverter.controller.history;

import com.jorge.playlistconverter.dto.response.ApiErrorResponse;
import com.jorge.playlistconverter.dto.response.ConversionHistoryResponse;
import com.jorge.playlistconverter.errors.http.ApiExceptionHandler;
import com.jorge.playlistconverter.errors.persistence.SyncStateStorageException;
import com.jorge.playlistconverter.mapper.ConversionHistoryResponseMapper;
import com.jorge.playlistconverter.model.SyncRunHistory;
import com.jorge.playlistconverter.service.history.ConversionHistoryService;
import com.jorge.playlistconverter.state.SyncStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


class ConversionHistoryControllerTest {


    private static final String HISTORY_URL = "/api/conversions/history";

    private SyncStateStore store;
    private MockMvc mockMvc;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        store = mock(SyncStateStore.class);

        var controller = new ConversionHistoryController(
                new ConversionHistoryService(store),
                new ConversionHistoryResponseMapper());

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnTwentyItemsPerPageByDefault() throws Exception {
        LocalDateTime startedAt = LocalDateTime.of(2026, 11, 7, 14, 0);

        when(store.findSyncRunHistory(20, 0)).thenReturn(List.of(
                new SyncRunHistory(
                        25L, 10L, "source-playlist",
                        "destination-playlist",
                        "SPOTIFY_TO_YOUTUBE", "SUCCESS",
                        startedAt, startedAt.plusMinutes(1))));

        when(store.countSyncRunHistory()).thenReturn(41L);

        var result = mockMvc.perform(get(HISTORY_URL))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "41"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        ConversionHistoryResponse[] response = jsonMapper.readValue(
                result.getResponse().getContentAsString(),
                ConversionHistoryResponse[].class);

        assertEquals(1, response.length);
        assertEquals(25L, response[0].executionId());
        assertEquals(
                "https://www.youtube.com/playlist?list=destination-playlist",
                response[0].destinationPlaylistUrl());
        assertEquals(startedAt, response[0].startedAt());

        verify(store).findSyncRunHistory(20, 0);
        verify(store).countSyncRunHistory();
        verifyNoMoreInteractions(store);
    }

    @Test
    void shouldUseRequestedPaginationAndReturnEmptyPage() throws Exception {
        when(store.findSyncRunHistory(20, 40)).thenReturn(List.of());
        when(store.countSyncRunHistory()).thenReturn(40L);

        mockMvc.perform(get(HISTORY_URL)
                        .param("limit", "20")
                        .param("offset", "40"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "40"))
                .andExpect(content().string("[]"));

        verify(store).findSyncRunHistory(20, 40);
        verify(store).countSyncRunHistory();
        verifyNoMoreInteractions(store);
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "-1, 0",
            "101, 0",
            "20, -1"
    })
    void shouldRejectInvalidPaginationBeforeQueryingStore(String limit, String offset) throws Exception {
        var result = mockMvc.perform(get(HISTORY_URL)
                        .param("limit", limit)
                        .param("offset", offset))
                .andExpect(status().isBadRequest())
                .andReturn();

        ApiErrorResponse response = jsonMapper.readValue(
                result.getResponse().getContentAsString(),
                ApiErrorResponse.class);

        assertEquals("INVALID_HISTORY_PAGINATION", response.code());
        assertEquals(
                "Informe um limite entre 1 e 100 e um deslocamento maior ou igual a zero.",
                response.message());
        assertEquals(HISTORY_URL, response.path());

        verifyNoInteractions(store);
    }

    @ParameterizedTest
    @CsvSource({
            "abc, 0",
            "20, abc",
            "2147483648, 0"
    })
    void shouldRejectNonIntegerPaginationBeforeQueryingStore(String limit, String offset) throws Exception {
        var result = mockMvc.perform(get(HISTORY_URL)
                        .param("limit", limit)
                        .param("offset", offset))
                .andExpect(status().isBadRequest())
                .andReturn();

        ApiErrorResponse response = jsonMapper.readValue(
                result.getResponse().getContentAsString(),
                ApiErrorResponse.class);

        assertEquals("INVALID_PATH_PARAMETER", response.code());
        verifyNoInteractions(store);
    }

    @Test
    void shouldReturnControlledErrorWhenHistoryReadFails() throws Exception {
        when(store.findSyncRunHistory(20, 0))
                .thenThrow(new SyncStateStorageException(
                        "detalhe-interno",
                        new SQLException("causa-interna")));

        var result = mockMvc.perform(get(HISTORY_URL))
                .andExpect(status().isInternalServerError())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        ApiErrorResponse response = jsonMapper.readValue(body, ApiErrorResponse.class);

        assertEquals("SYNC_STATE_STORAGE_FAILED", response.code());
        assertEquals(
                "Não foi possível consultar o histórico local. Tente novamente mais tarde.",
                response.message());
        assertEquals(HISTORY_URL, response.path());
        assertFalse(body.contains("detalhe-interno"));
        assertFalse(body.contains("causa-interna"));

        verify(store).findSyncRunHistory(20, 0);
        verifyNoMoreInteractions(store);
    }
}
