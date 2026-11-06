package com.jorge.playlistconverter.controller;

import com.jorge.playlistconverter.dto.response.ApiErrorResponse;
import com.jorge.playlistconverter.errors.http.ApiExceptionHandler;
import com.jorge.playlistconverter.service.PlaylistCancellationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlaylistCancellationControllerTest {

    private static final String PREVIEWS_URL = "/api/conversions/previews";
    private static final UUID PREVIEW_ID =
            UUID.fromString("3ebf4e4c-7ebd-4c52-b5b0-d943d4647206");

    private PlaylistCancellationService cancellationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        cancellationService = mock(PlaylistCancellationService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new PlaylistCancellationController(cancellationService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldCancelPreviewWithoutResponseBody() throws Exception {
        mockMvc.perform(delete(PREVIEWS_URL + "/" + PREVIEW_ID))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(cancellationService).cancel(PREVIEW_ID);
    }

    @Test
    void shouldRejectMalformedPreviewIdBeforeCallingService() throws Exception {
        String path = PREVIEWS_URL + "/abc";

        var result = mockMvc.perform(delete(path))
                .andExpect(status().isBadRequest())
                .andReturn();

        ApiErrorResponse response = JsonMapper.builder().build().readValue(
                result.getResponse().getContentAsString(),
                ApiErrorResponse.class);

        assertEquals("INVALID_PATH_PARAMETER", response.code());
        assertEquals(path, response.path());
        verifyNoInteractions(cancellationService);
    }
}
