package com.jorge.playlistconverter.spotify;

import com.jorge.playlistconverter.errors.spotify.SpotifyRequestLimitException;
import com.jorge.playlistconverter.model.Song;
import com.jorge.playlistconverter.spotify.authorization.SpotifyAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.detailed.TooManyRequestsException;
import se.michaelthelin.spotify.exceptions.detailed.UnauthorizedException;
import se.michaelthelin.spotify.model_objects.specification.Paging;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.requests.data.search.simplified.SearchTracksRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpotifyTrackSearchTest {

    private static final String QUERY = "Nightcall Kavinsky";
    private static final String READ_SCOPES =
            "playlist-read-private playlist-read-collaborative";
    private static final Song SOURCE =
            new Song("youtube-video", "Nightcall", "Kavinsky", 0);

    private final SpotifyAuthService authService = mock(SpotifyAuthService.class);
    private final SpotifyApi api = mock(SpotifyApi.class, RETURNS_DEEP_STUBS);
    private final SpotifyMusicService service = new SpotifyMusicService(authService);

    @BeforeEach
    void setUp() {
        when(authService.getSpotifyApi()).thenReturn(api);
    }

    @Test
    void shouldMapSearchCandidate() throws Exception {
        Track track = mock(Track.class);
        when(track.getId()).thenReturn("spotify-track");
        when(track.getName()).thenReturn("Nightcall");
        when(track.getDurationMs()).thenReturn(258_000);
        when(searchRequest().execute().getItems()).thenReturn(new Track[]{track});

        var candidates = service.searchCandidates(SOURCE);

        assertEquals(1, candidates.size());
        Song candidate = candidates.getFirst();
        assertEquals("spotify-track", candidate.id());
        assertEquals("Nightcall", candidate.title());
        assertEquals(258_000, candidate.durationMs());
        assertNull(candidate.artworkUrl());
    }

    @Test
    void shouldReturnEmptyListWhenSpotifyFindsNoTracks() throws Exception {
        when(searchRequest().execute().getItems()).thenReturn(new Track[0]);

        assertTrue(service.searchCandidates(SOURCE).isEmpty());
    }

    @Test
    void shouldReauthenticateAndRetrySearchOnceAfterUnauthorized() throws Exception {
        SearchTracksRequest request = searchRequest();
        Paging<Track> response = request.execute();
        when(response.getItems()).thenReturn(new Track[0]);
        when(request.execute())
                .thenThrow(new UnauthorizedException("Token expirado"))
                .thenReturn(response);
        clearInvocations(request);

        assertTrue(service.searchCandidates(SOURCE).isEmpty());

        var order = inOrder(authService, request);
        order.verify(authService).ensureAuthenticated(READ_SCOPES);
        order.verify(request).execute();
        order.verify(authService).invalidateAccessToken();
        order.verify(authService).ensureAuthenticated(READ_SCOPES);
        order.verify(request).execute();
        verify(request, times(2)).execute();
    }

    @Test
    void shouldPreserveRetryAfterWithoutRetryingSearch() throws Exception {
        SearchTracksRequest request = searchRequest();
        var original = new TooManyRequestsException("Limite simulado", 30);
        when(request.execute()).thenThrow(original);

        var result = assertThrows(SpotifyRequestLimitException.class,
                () -> service.searchCandidates(SOURCE));

        assertSame(original, result.getCause());
        assertEquals(30, result.getRetryAfterSeconds());
        verify(request).execute();
        verify(authService, never()).invalidateAccessToken();
    }

    private SearchTracksRequest searchRequest() {
        return api.searchTracks(QUERY).limit(5).build();
    }
}
