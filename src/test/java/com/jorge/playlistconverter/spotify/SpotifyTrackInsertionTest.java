package com.jorge.playlistconverter.spotify;

import com.google.gson.JsonArray;
import com.jorge.playlistconverter.errors.spotify.SpotifyTrackInsertionException;
import com.jorge.playlistconverter.spotify.authorization.SpotifyAuthService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.special.SnapshotResult;
import se.michaelthelin.spotify.requests.data.playlists.AddItemsToPlaylistRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpotifyTrackInsertionTest {

    private static final String PLAYLIST_ID = "destination-playlist";

    private final SpotifyAuthService authService = mock(SpotifyAuthService.class);
    private final SpotifyApi api = mock(SpotifyApi.class);
    private final SpotifyMusicService service = new SpotifyMusicService(authService);

    @Test
    void shouldInsertTracksInOrderedBatches() throws Exception {
        AddItemsToPlaylistRequest first = successfulRequest();
        AddItemsToPlaylistRequest second = successfulRequest();
        prepareRequests(first, second);

        List<String> ids = trackIds(101);
        List<String> inserted = new ArrayList<>();

        service.addTracks(PLAYLIST_ID, ids, inserted::add);

        var payloads = ArgumentCaptor.forClass(JsonArray.class);
        var order = inOrder(api, first, second);
        order.verify(api).addItemsToPlaylist(eq(PLAYLIST_ID), payloads.capture());
        order.verify(first).execute();
        order.verify(api).addItemsToPlaylist(eq(PLAYLIST_ID), payloads.capture());
        order.verify(second).execute();

        assertEquals(expectedUris(ids.subList(0, 100)), payloads.getAllValues().get(0));
        assertEquals(expectedUris(ids.subList(100, 101)), payloads.getAllValues().get(1));
        assertEquals(ids, inserted);
    }

    @Test
    void shouldReportOnlyConfirmedTracksWhenSecondBatchFails() throws Exception {
        AddItemsToPlaylistRequest first = successfulRequest();
        AddItemsToPlaylistRequest second = mock(AddItemsToPlaylistRequest.class);
        IOException original = new IOException("Falha simulada");
        when(second.execute()).thenThrow(original);
        prepareRequests(first, second);

        List<String> ids = trackIds(201);
        List<String> inserted = new ArrayList<>();

        var error = assertThrows(SpotifyTrackInsertionException.class,
                () -> service.addTracks(PLAYLIST_ID, ids, inserted::add));

        assertSame(original, error.getCause());
        assertEquals(ids.subList(0, 100), inserted);
        verify(api, times(2)).addItemsToPlaylist(eq(PLAYLIST_ID), any(JsonArray.class));
        verify(second, times(1)).execute();
    }

    @Test
    void shouldRejectBatchWithoutSnapshotBeforeReportingTracks() throws Exception {
        AddItemsToPlaylistRequest request = mock(AddItemsToPlaylistRequest.class);
        when(request.execute()).thenReturn(null);
        prepareRequests(request, request);

        List<String> inserted = new ArrayList<>();

        assertThrows(SpotifyTrackInsertionException.class,
                () -> service.addTracks(PLAYLIST_ID, List.of("track-1"), inserted::add));

        assertTrue(inserted.isEmpty());
        verify(request, times(1)).execute();
    }

    private void prepareRequests(AddItemsToPlaylistRequest first,
                                 AddItemsToPlaylistRequest second) {
        var firstBuilder = mock(AddItemsToPlaylistRequest.Builder.class);
        var secondBuilder = mock(AddItemsToPlaylistRequest.Builder.class);
        when(firstBuilder.build()).thenReturn(first);
        when(secondBuilder.build()).thenReturn(second);
        when(authService.getSpotifyApi()).thenReturn(api);
        when(api.addItemsToPlaylist(eq(PLAYLIST_ID), any(JsonArray.class)))
                .thenReturn(firstBuilder, secondBuilder);
    }

    private AddItemsToPlaylistRequest successfulRequest() throws Exception {
        SnapshotResult snapshot = mock(SnapshotResult.class);
        when(snapshot.getSnapshotId()).thenReturn("confirmed-snapshot");

        AddItemsToPlaylistRequest request = mock(AddItemsToPlaylistRequest.class);
        when(request.execute()).thenReturn(snapshot);
        return request;
    }

    private List<String> trackIds(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(number -> "track-" + number)
                .toList();
    }

    private JsonArray expectedUris(List<String> ids) {
        JsonArray uris = new JsonArray();
        ids.forEach(id -> uris.add("spotify:track:" + id));
        return uris;
    }
}
