package com.jorge.playlistconverter.config.conversion;

import com.jorge.playlistconverter.converter.PlaylistAnalyzer;
import com.jorge.playlistconverter.converter.PlaylistConverter;
import com.jorge.playlistconverter.model.MatchResult;
import com.jorge.playlistconverter.model.Song;
import com.jorge.playlistconverter.spotify.SpotifyMusicService;
import com.jorge.playlistconverter.state.SyncStateStore;
import com.jorge.playlistconverter.youtube.YoutubeMusicService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConversionConfigTest {

    private final YoutubeMusicService youtube = mock(YoutubeMusicService.class);
    private final SpotifyMusicService spotify = mock(SpotifyMusicService.class);

    @Test
    void shouldReadYoutubeAndMatchSpotifyTracksInReverseAnalyzer() {
        Song video = new Song("youtube-video", "Nightcall", "Kavinsky", 0);
        Song track = new Song("spotify-track", "Nightcall", "Kavinsky", 258_000);

        when(youtube.getPlaylistTracks("youtube-playlist")).thenReturn(List.of(video));
        when(spotify.searchCandidates(video)).thenReturn(List.of(track));

        try (var context = createContext()) {
            var analyzer = context.getBean("youtubeToSpotifyAnalyzer", PlaylistAnalyzer.class);

            MatchResult match = analyzer.analyze("youtube-playlist").getFirst();

            assertEquals(video, match.sourceSong());
            assertEquals("spotify-track", match.matchedId());
            verify(youtube).getPlaylistTracks("youtube-playlist");
            verify(spotify).searchCandidates(video);
        }
    }

    @Test
    void shouldKeepSpotifyAsSourceForExistingConverter() {
        Song track = new Song("spotify-track", "Nightcall", "Kavinsky", 258_000);
        Song video = new Song("youtube-video", "Nightcall", "Kavinsky", 258_000);

        when(spotify.getPlaylistTracks("spotify-playlist")).thenReturn(List.of(track));
        when(youtube.searchCandidates(track)).thenReturn(List.of(video));

        try (var context = createContext()) {
            var converter = context.getBean("spotifyToYoutubeConverter", PlaylistConverter.class);

            var preview = converter.previewNewPlaylist("spotify-playlist");

            assertEquals("youtube-video", preview.matches().getFirst().matchedId());
            verify(spotify).getPlaylistTracks("spotify-playlist");
            verify(youtube).searchCandidates(track);
        }
    }

    @Test
    void shouldPreviewNewSpotifyPlaylistWithMatchedTrackId() {
        Song video = new Song("youtube-video", "Nightcall", "Kavinsky", 0);
        Song track = new Song("spotify-track", "Nightcall", "Kavinsky", 258_000);

        when(youtube.getPlaylistTracks("youtube-playlist")).thenReturn(List.of(video));
        when(spotify.searchCandidates(video)).thenReturn(List.of(track));

        try (var context = createContext()) {
            var converter = context.getBean("youtubeToSpotifyConverter", PlaylistConverter.class);

            var preview = converter.previewNewPlaylist("youtube-playlist");

            assertEquals(List.of("spotify-track"), preview.newTrackIds());
            verify(spotify, never()).createPlaylist(anyString(), any());
        }
    }

    @Test
    void shouldRecognizeTrackAlreadyInSpotifyDestinationWithoutSearching() {
        Song video = new Song("youtube-video", "Nightcall", "Kavinsky", 258_000);
        Song track = new Song("spotify-track", "Nightcall", "Kavinsky", 258_000);

        when(youtube.getPlaylistTracks("youtube-playlist")).thenReturn(List.of(video));
        when(spotify.getPlaylistTracksAlreadyAdded("spotify-playlist")).thenReturn(List.of(track));

        try (var context = createContext()) {
            var converter = context.getBean("youtubeToSpotifyConverter", PlaylistConverter.class);

            var preview = converter.previewExistingPlaylist("youtube-playlist", "spotify-playlist");

            assertEquals(1L, preview.alreadyPresentCount());
            assertTrue(preview.newTrackIds().isEmpty());
            verify(spotify, never()).searchCandidates(any());
        }
    }

    private AnnotationConfigApplicationContext createContext() {
        var context = new AnnotationConfigApplicationContext();
        context.registerBean(YoutubeMusicService.class, () -> youtube);
        context.registerBean(SpotifyMusicService.class, () -> spotify);
        context.registerBean(SyncStateStore.class, () -> mock(SyncStateStore.class));
        context.register(ConversionConfig.class);
        context.refresh();
        return context;
    }

}
