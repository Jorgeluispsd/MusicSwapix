package com.jorge.playlistconverter.spotify;

import com.jorge.playlistconverter.errors.OperationInterruptedException;
import com.jorge.playlistconverter.errors.spotify.SpotifyRequestLimitException;
import com.jorge.playlistconverter.spotify.authorization.SpotifyAuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.detailed.TooManyRequestsException;
import se.michaelthelin.spotify.requests.data.playlists.GetPlaylistItemsRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import com.jorge.playlistconverter.model.Song;
import java.util.List;

class SpotifyMusicServiceTest {

    @AfterEach
    void clearInterrupetdFlag(){
        Thread.interrupted();
    }

    @Test
    void shouldPreserveInterruptionDuringInitialAuthentication() throws Exception{
        SpotifyAuthService authService = mock(SpotifyAuthService.class);
        InterruptedException original = new InterruptedException("Interrupção Simulada");

        doThrow(original).when(authService).ensureAuthenticated(anyString());

        SpotifyMusicService service = new SpotifyMusicService(authService);

        OperationInterruptedException result = assertThrows(OperationInterruptedException.class, () ->
            service.getPlaylistTracks("test-playlist")
        );

        assertSame(original, result.getCause());
        assertTrue(Thread.currentThread().isInterrupted());

        verify(authService, times(1)).ensureAuthenticated(anyString());

        verify(authService, never()).getSpotifyApi();
    }

    @Test
    void shouldPreserveInterruptionBeforeReadingPlaylistPage() throws Exception{
        SpotifyAuthService authService = mock(SpotifyAuthService.class);
        InterruptedException original = new InterruptedException("Interrupção Simulada");

        doNothing().doThrow(original).when(authService).ensureAuthenticated(anyString());

        SpotifyMusicService service = new SpotifyMusicService(authService);

        OperationInterruptedException result = assertThrows(OperationInterruptedException.class, () ->
            service.getPlaylistTracks("test-playlist")
        );

        assertSame(original, result.getCause());
        assertTrue(Thread.currentThread().isInterrupted());

        verify(authService, times(2)).ensureAuthenticated(anyString());
        verify(authService, times(1)).getSpotifyApi();
    }

    @Test
    void shouldPreserveRetryAfterWhenSpotifyLimitsRequests() throws Exception{
        TooManyRequestsException original = new TooManyRequestsException("Limite simulado", 30);

        assertRequestLimit(original, 30);
    }

    @Test
    void shouldHandleRequestLimitWithoutRetryAfter() throws Exception{
        TooManyRequestsException original = new TooManyRequestsException("Limite simulado");

        assertRequestLimit(original, null);
    }

    @Test
    void shouldReusePlaylistReadingToFindTracksAlreadyInDestination() {
        SpotifyMusicService service = spy(
                new SpotifyMusicService(mock(SpotifyAuthService.class))
        );
        List<Song> destinationTracks = List.of(
                new Song("spotify-track", "Nightcall", "Kavinsky", 258_000)
        );
        doReturn(destinationTracks).when(service).getPlaylistTracks("destination-playlist");

        assertEquals(
                destinationTracks,
                service.getPlaylistTracksAlreadyAdded("destination-playlist")
        );
        verify(service).getPlaylistTracks("destination-playlist");
    }

    private void assertRequestLimit(TooManyRequestsException original, Integer expectedRetryAfter) throws Exception{
        SpotifyAuthService authService = mock(SpotifyAuthService.class);
        SpotifyApi api = mock(SpotifyApi.class);

        GetPlaylistItemsRequest.Builder builder =
                mock(GetPlaylistItemsRequest.Builder.class, RETURNS_SELF);

        GetPlaylistItemsRequest request = mock(GetPlaylistItemsRequest.class);

        when(authService.getSpotifyApi()).thenReturn(api);
        when(api.getPlaylistItems("test-playlist")).thenReturn(builder);
        when(builder.build()).thenReturn(request);
        when(request.execute()).thenThrow(original);

        SpotifyMusicService service = new SpotifyMusicService(authService);

        SpotifyRequestLimitException result = assertThrows(
                SpotifyRequestLimitException.class,
                () -> service.getPlaylistTracks("test-playlist"));

        assertSame(original, result.getCause());
        assertEquals(expectedRetryAfter, result.getRetryAfterSeconds());

        verify(request, times(1)).execute();
        verify(authService, never()).invalidateAccessToken();
    }
}
