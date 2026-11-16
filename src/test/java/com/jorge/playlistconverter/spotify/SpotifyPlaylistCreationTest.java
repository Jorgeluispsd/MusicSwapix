package com.jorge.playlistconverter.spotify;

import com.jorge.playlistconverter.enums.PlaylistVisibility;
import com.jorge.playlistconverter.errors.spotify.SpotifyPlaylistCreationException;
import com.jorge.playlistconverter.spotify.authorization.SpotifyAuthService;
import org.junit.jupiter.api.Test;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.specification.Playlist;
import se.michaelthelin.spotify.requests.data.playlists.CreatePlaylistRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpotifyPlaylistCreationTest {


    private static final String NAME = "Minhas favoritas";
    private static final String PLAYLIST_ID = "spotify-playlist-id";
    private static final String WRITE_SCOPES =
            "playlist-read-private playlist-read-collaborative "
                    + "playlist-modify-private playlist-modify-public";

    private final SpotifyAuthService authService = mock(SpotifyAuthService.class);
    private final SpotifyApi api = mock(SpotifyApi.class, RETURNS_DEEP_STUBS);
    private final SpotifyMusicService service = new SpotifyMusicService(authService);

    @Test
    void shouldCreatePrivatePlaylistByDefault() throws Exception {
        when(authService.getSpotifyApi()).thenReturn(api);
        CreatePlaylistRequest request = creationRequest(false);
        Playlist response = playlistWithId(PLAYLIST_ID);
        when(request.execute()).thenReturn(response);

        String result = service.createPlaylist("  " + NAME + "  ");

        assertEquals(PLAYLIST_ID, result);
        var order = inOrder(authService, request);
        order.verify(authService).ensureAuthenticated(WRITE_SCOPES);
        order.verify(request).execute();
        verify(request, times(1)).execute();
    }

    @Test
    void shouldCreatePublicPlaylistWhenRequested() throws Exception {
        when(authService.getSpotifyApi()).thenReturn(api);
        CreatePlaylistRequest request = creationRequest(true);
        Playlist response = playlistWithId(PLAYLIST_ID);
        when(request.execute()).thenReturn(response);

        String result = service.createPlaylist(NAME, PlaylistVisibility.PUBLIC);

        assertEquals(PLAYLIST_ID, result);
        verify(request).execute();
    }

    @Test
    void shouldRejectUnlistedVisibilityBeforeAuthentication() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createPlaylist(NAME, PlaylistVisibility.UNLISTED));

        verifyNoInteractions(authService, api);
    }

    @Test
    void shouldRejectCreationResponseWithoutPlaylistId() throws Exception {
        when(authService.getSpotifyApi()).thenReturn(api);
        CreatePlaylistRequest request = creationRequest(false);
        Playlist response = playlistWithId(null);
        when(request.execute()).thenReturn(response);

        assertThrows(SpotifyPlaylistCreationException.class,
                () -> service.createPlaylist(NAME, PlaylistVisibility.PRIVATE));

        verify(request).execute();
    }

    private CreatePlaylistRequest creationRequest(boolean publicPlaylist) {
        return api.createPlaylist(NAME).public_(publicPlaylist).build();
    }

    private Playlist playlistWithId(String id) {
        Playlist playlist = mock(Playlist.class);
        when(playlist.getId()).thenReturn(id);
        return playlist;
    }
}
