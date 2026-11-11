package com.jorge.playlistconverter.converter.preview;

import com.jorge.playlistconverter.model.MatchResult;
import com.jorge.playlistconverter.model.Song;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlaylistPreviewSelectionTest {

    @Test
    void shouldExcludeUserSkippedTrackWithoutMarkingItAsNotFound() {
        MatchResult found = new MatchResult(
                new Song("source-A", "Song A", "Artist", 200_000),
                "video-A", "Video A", 0.95);

        MatchResult skipped = found.skipByUser();

        PlaylistPreview preview = new PlaylistPreview(
                "source-playlist", List.of(skipped));

        assertEquals(1, preview.totalCount());
        assertEquals(0, preview.foundCount());
        assertTrue(preview.newTrackIds().isEmpty());
        assertTrue(preview.notFoundSongs().isEmpty());
        assertTrue(skipped.skippedByUser());
        assertEquals("source-A", skipped.sourceSong().id());
    }

    @Test
    void shouldKeepUnmatchedTrackSeparateFromUserSkippedTrack() {
        MatchResult missing = new MatchResult(
                new Song("source-A", "Song A", "Artist", 200_000),
                null, null, 0.0);

        PlaylistPreview preview = new PlaylistPreview(
                "source-playlist", List.of(missing));

        assertFalse(missing.skippedByUser());
        assertEquals(List.of(missing.sourceSong()), preview.notFoundSongs());
    }
}
