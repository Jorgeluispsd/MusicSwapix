package com.jorge.playlistconverter.mapper;

import com.jorge.playlistconverter.converter.preview.PlaylistPreview;
import com.jorge.playlistconverter.converter.preview.StoredPlaylistPreview;
import com.jorge.playlistconverter.dto.response.MatchedTrackResponse;
import com.jorge.playlistconverter.dto.response.PlaylistPreviewResponse;
import com.jorge.playlistconverter.dto.response.UnmatchedTrackResponse;
import com.jorge.playlistconverter.enums.PlaylistVisibility;
import com.jorge.playlistconverter.model.MatchResult;
import com.jorge.playlistconverter.model.Song;
import com.jorge.playlistconverter.dto.response.TrackReviewOptionResponse;
import com.jorge.playlistconverter.model.TrackReviewOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlaylistPreviewResponseMapperTest {

    private static final UUID PREVIEW_ID =
            UUID.fromString("3ebf4e4c-7ebd-4c52-b5b0-d943d4647206");

    private static final Instant EXPIRES_AT =
            Instant.parse("2026-10-27T18:10:00Z");

    private PlaylistPreviewResponseMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new PlaylistPreviewResponseMapper();
    }

    @Test
    void shouldMapNewPlaylistMetadataAndMatchedTrack() {
        PlaylistPreview preview = new PlaylistPreview("source-playlist",
                List.of(match("source-A", "video-A")));

        StoredPlaylistPreview stored = new StoredPlaylistPreview(
                PREVIEW_ID, preview,
                "Meu Rock", PlaylistVisibility.PUBLIC, EXPIRES_AT);

        PlaylistPreviewResponse response = mapper.toResponse(stored);

        PlaylistPreviewResponse expected = new PlaylistPreviewResponse(
                PREVIEW_ID,
                "source-playlist",
                null,
                "Meu Rock",
                PlaylistVisibility.PUBLIC,
                EXPIRES_AT,
                1,
                1L,
                0L,
                1,
                0L,
                0,
                List.of(new MatchedTrackResponse("source-A", "Song source-A",
                        "Artist", "video-A", "Video video-A",
                        0.95, false, List.of(), 0
                )), List.of());

        assertEquals(expected, response);
    }

    @Test
    void shouldMapExistingRepeatedAndUnmatchedTracks() {
        MatchResult missing = new MatchResult(
                new Song("source-D", "Song D", "Artist D", 180000),
                null, null, 0.0);

        PlaylistPreview preview = new PlaylistPreview(
                "source-playlist",
                "destination-playlist",
                List.of(
                        match("source-A", "video-A"),
                        match("source-B", "video-B"),
                        match("source-C", "video-B"),
                        missing),
                Set.of("video-A"));

        StoredPlaylistPreview stored = new StoredPlaylistPreview(
                PREVIEW_ID, preview, null, null, EXPIRES_AT);

        PlaylistPreviewResponse response = mapper.toResponse(stored);

        assertEquals(PREVIEW_ID, response.previewId());
        assertEquals("source-playlist", response.sourcePlaylistId());
        assertEquals("destination-playlist", response.destinationPlaylistId());
        assertEquals(EXPIRES_AT, response.expiresAt());
        assertNull(response.destinationName());
        assertNull(response.visibility());

        assertEquals(4, response.total());
        assertEquals(3L, response.found());
        assertEquals(1L, response.alreadyPresent());
        assertEquals(1, response.newTrackCount());
        assertEquals(1L, response.repeatedCandidateCount());
        assertEquals(1, response.notFoundCount());

        assertEquals(
                List.of(
                        new MatchedTrackResponse(
                                "source-A", "Song source-A", "Artist",
                                "video-A", "Video video-A", 0.95, true,
                                List.of(), 0
                        ),
                        new MatchedTrackResponse(
                                "source-B", "Song source-B", "Artist",
                                "video-B", "Video video-B", 0.95, false,
                                List.of(), 1
                        ),
                        new MatchedTrackResponse(
                                "source-C", "Song source-C", "Artist",
                                "video-B", "Video video-B", 0.95, false,
                                List.of(), 2
                        )
                ),
                response.matches());

        assertEquals(
                List.of(new UnmatchedTrackResponse(
                        "source-D", "Song D", "Artist D")), response.notFound());
    }

    @Test
    void shouldRepresentPreviewWithoutAnyMatch() {
        PlaylistPreview preview = new PlaylistPreview(
                "source-playlist",
                List.of(new MatchResult(
                        new Song("source-A", "Song A", "Artist A", 180000),
                        null, null, 0.0))
        );

        StoredPlaylistPreview stored = new StoredPlaylistPreview(PREVIEW_ID, preview,
                "Meu Rock", PlaylistVisibility.PRIVATE, EXPIRES_AT);

        PlaylistPreviewResponse response = mapper.toResponse(stored);

        assertEquals(1, response.total());
        assertEquals(0L, response.found());
        assertEquals(0L, response.alreadyPresent());
        assertEquals(0, response.newTrackCount());
        assertEquals(0L, response.repeatedCandidateCount());
        assertEquals(1, response.notFoundCount());
        assertTrue(response.matches().isEmpty());

        assertEquals(List.of(new UnmatchedTrackResponse(
                        "source-A", "Song A", "Artist A")), response.notFound());
    }

    @Test
    void shouldRejectNullStoredPreview() {
        assertThrows(NullPointerException.class,
                () -> mapper.toResponse(null));
    }

    @Test
    void shouldMapReviewOptionsInOrderAndMarkVideosAlreadyPresent() {
        Song source = new Song("source-A", "Song A", "Artist", 200_000);
        Song suggested = new Song("video-A", "Song A", "Artist", 202_000);
        Song alternative = new Song("video-B", "Artist - Song A", "Label", 0);

        MatchResult missing = new MatchResult(new Song("source-missing", "Missing Song", "Artist", 0),
                null, null, 0.0);

        MatchResult match = new MatchResult(
                source,
                suggested.id(),
                suggested.title(),
                0.98,
                List.of(
                        new TrackReviewOption(suggested, 0.98),
                        new TrackReviewOption(alternative, 0.96)));

        PlaylistPreview preview = new PlaylistPreview(
                "source-playlist",
                "destination-playlist",
                List.of(missing, match),
                Set.of("video-B"));

        StoredPlaylistPreview stored = new StoredPlaylistPreview(
                PREVIEW_ID, preview, null, null, EXPIRES_AT);

        MatchedTrackResponse response = mapper.toResponse(stored).matches().getFirst();

        assertEquals("video-A", response.destinationTrackId());
        assertFalse(response.alreadyPresent());
        assertEquals(List.of(
                new TrackReviewOptionResponse(
                        "video-A", "Song A", "Artist", 202_000, 0.98, false),
                new TrackReviewOptionResponse(
                        "video-B", "Artist - Song A", "Label", 0, 0.96, true)
        ), response.reviewOptions());
        assertEquals(1, response.trackPosition());
    }

    private MatchResult match(String sourceId, String videoId) {
        return new MatchResult(
                new Song(sourceId, "Song " + sourceId, "Artist", 180000),
                videoId, "Video " + videoId, 0.95);
    }
}
