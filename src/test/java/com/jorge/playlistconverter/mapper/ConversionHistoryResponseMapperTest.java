package com.jorge.playlistconverter.mapper;

import com.jorge.playlistconverter.dto.response.ConversionHistoryResponse;
import com.jorge.playlistconverter.model.SyncRunHistory;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ConversionHistoryResponseMapperTest {


    private final ConversionHistoryResponseMapper mapper =
            new ConversionHistoryResponseMapper();

    @Test
    void shouldMapCompletedExecutionAndDestinationUrl() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 11, 7, 14, 0);
        LocalDateTime completedAt = startedAt.plusMinutes(1);

        SyncRunHistory execution = new SyncRunHistory(
                25L, 10L, "source-playlist", "destination-playlist",
                "SPOTIFY_TO_YOUTUBE", "SUCCESS", startedAt, completedAt);

        ConversionHistoryResponse response = mapper.toResponse(execution);

        assertEquals(new ConversionHistoryResponse(
                25L,
                "source-playlist",
                "destination-playlist",
                "https://www.youtube.com/playlist?list=destination-playlist",
                "SPOTIFY_TO_YOUTUBE",
                "SUCCESS",
                startedAt,
                completedAt), response);
    }

    @Test
    void shouldRepresentLegacyNullStatusAsUnknown() {
        SyncRunHistory execution = new SyncRunHistory(
                1L, 10L, "source-playlist", "destination-playlist",
                "SPOTIFY_TO_YOUTUBE", null, null, null);

        ConversionHistoryResponse response = mapper.toResponse(execution);

        assertEquals("UNKNOWN", response.status());
        assertNull(response.startedAt());
        assertNull(response.completedAt());
    }

    @Test
    void shouldPreserveRunningStatusWithoutInferringCompletion() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 11, 1, 10, 0);

        SyncRunHistory execution = new SyncRunHistory(
                2L, 10L, "source-playlist", "destination-playlist",
                "SPOTIFY_TO_YOUTUBE", "RUNNING", startedAt, null);

        ConversionHistoryResponse response = mapper.toResponse(execution);

        assertEquals("RUNNING", response.status());
        assertEquals(startedAt, response.startedAt());
        assertNull(response.completedAt());
    }

    @Test
    void shouldAvoidYoutubeUrlForAnotherDirection() {
        SyncRunHistory execution = new SyncRunHistory(
                3L, 11L, "youtube-source", "spotify-destination",
                "YOUTUBE_TO_SPOTIFY", "SUCCESS", null, null);

        ConversionHistoryResponse response = mapper.toResponse(execution);

        assertEquals("spotify-destination", response.destinationPlaylistId());
        assertEquals("YOUTUBE_TO_SPOTIFY", response.direction());
        assertNull(response.destinationPlaylistUrl());
    }

    @Test
    void shouldRejectNullExecution() {
        assertThrows(NullPointerException.class,
                () -> mapper.toResponse(null));
    }
}
