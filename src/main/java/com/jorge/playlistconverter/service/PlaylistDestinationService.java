package com.jorge.playlistconverter.service;

import com.jorge.playlistconverter.converter.history.PlaylistDestinationRegistry;
import com.jorge.playlistconverter.input.SpotifyPlaylistParser;
import com.jorge.playlistconverter.model.SyncJob;

import java.util.List;
import java.util.Objects;

public class PlaylistDestinationService {

    private final SpotifyPlaylistParser spotifyPlaylistParser;
    private final PlaylistDestinationRegistry destinationRegistry;

    public PlaylistDestinationService(
            SpotifyPlaylistParser spotifyPlaylistParser,
            PlaylistDestinationRegistry destinationRegistry
    ) {
        this.spotifyPlaylistParser = Objects.requireNonNull(spotifyPlaylistParser);
        this.destinationRegistry = Objects.requireNonNull(destinationRegistry);
    }

    public List<SyncJob> findDestinations(String sourceInput) {
        String sourcePlaylistId = spotifyPlaylistParser.parse(sourceInput);
        return destinationRegistry.findDestinations(sourcePlaylistId);
    }
}
