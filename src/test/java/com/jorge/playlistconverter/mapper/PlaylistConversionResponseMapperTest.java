package com.jorge.playlistconverter.mapper;

import com.jorge.playlistconverter.converter.ConversionResult;
import com.jorge.playlistconverter.dto.response.PlaylistConversionResponse;
import com.jorge.playlistconverter.dto.response.UnmatchedTrackResponse;
import com.jorge.playlistconverter.model.MatchResult;
import com.jorge.playlistconverter.model.Song;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlaylistConversionResponseMapperTest {


    private PlaylistConversionResponseMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new PlaylistConversionResponseMapper();
    }

    @Test
    void shouldPreserveCountsAndIdentifyUnmatchedTracks() {
        ConversionResult result = new ConversionResult(
                "source-playlist",
                "destination-playlist",
                List.of(
                        match("source-A", "video-A"),
                        match("source-B", "video-B"),
                        match("source-C", null),
                        match("source-D", null)),
                1, 1);

        PlaylistConversionResponse response = mapper.toResponse(result);

        PlaylistConversionResponse expected =
                new PlaylistConversionResponse("source-playlist",
                        "destination-playlist",
                        "https://www.youtube.com/playlist?list=destination-playlist",
                        1, 1, 2,
                        List.of(
                                new UnmatchedTrackResponse("source-C",
                                        "Song source-C", "Artist"),
                                new UnmatchedTrackResponse("source-D",
                                        "Song source-D", "Artist")), 0);

        assertEquals(expected, response);
    }

    @Test
    void shouldReturnEmptyUnmatchedListWhenAllTracksWereFound() {
        ConversionResult result = new ConversionResult("source-playlist",
                "destination-playlist",
                List.of(match("source-A", "video-A")),
                1, 0);

        PlaylistConversionResponse response = mapper.toResponse(result);

        assertEquals(1, response.insertedCount());
        assertEquals(0, response.skippedCount());
        assertEquals(0, response.notFoundCount());
        assertTrue(response.notFound().isEmpty());
    }

    @Test
    void shouldRejectNullConversionResult() {
        assertThrows(NullPointerException.class,
                () -> mapper.toResponse(null));
    }

    @Test
    void shouldReportUserSkippedTrackSeparatelyFromUnmatchedTrack() {
        ConversionResult result = new ConversionResult(
                "source-playlist",
                "destination-playlist",
                List.of(
                        match("source-A", "video-A").skipByUser(),
                        match("source-B", null)),
                0, 0);

        PlaylistConversionResponse response = mapper.toResponse(result);

        assertEquals(1, response.skippedByUserCount());
        assertEquals(1, response.notFoundCount());
        assertEquals("source-B", response.notFound().getFirst().sourceTrackId());
    }

    private MatchResult match(String sourceId, String videoId) {
        Song source = new Song(sourceId, "Song " + sourceId,
                "Artist", 180000);

        return new MatchResult(source, videoId,
                videoId == null ? null : "Video " + videoId,
                videoId == null ? 0.0 : 0.95);
    }
}
