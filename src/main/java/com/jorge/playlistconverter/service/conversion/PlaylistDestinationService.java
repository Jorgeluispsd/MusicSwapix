package com.jorge.playlistconverter.service.conversion;

import com.jorge.playlistconverter.converter.history.PlaylistDestinationRegistry;
import com.jorge.playlistconverter.input.SpotifyPlaylistParser;
import com.jorge.playlistconverter.input.YoutubePlaylistParser;
import com.jorge.playlistconverter.model.SyncJob;

import java.util.List;
import java.util.Objects;

public class PlaylistDestinationService {

    private final SpotifyPlaylistParser spotifyPlaylistParser;
    private final PlaylistDestinationRegistry destinationRegistry;
    private final YoutubePlaylistParser youtubePlaylistParser;
    private final PlaylistDestinationRegistry youtubeToSpotifyRegistry;

    public PlaylistDestinationService(
            SpotifyPlaylistParser spotifyPlaylistParser,
            PlaylistDestinationRegistry destinationRegistry, YoutubePlaylistParser youtubePlaylistParser,
            PlaylistDestinationRegistry youtubeToSpotifyRegistry
    ) {
        this.spotifyPlaylistParser = Objects.requireNonNull(spotifyPlaylistParser);
        this.destinationRegistry = Objects.requireNonNull(destinationRegistry);
        this.youtubePlaylistParser = Objects.requireNonNull(youtubePlaylistParser);
        this.youtubeToSpotifyRegistry = Objects.requireNonNull(youtubeToSpotifyRegistry);
    }

    public List<SyncJob> findDestinations(String sourceInput) {
        String sourcePlaylistId = spotifyPlaylistParser.parse(sourceInput);
        return destinationRegistry.findDestinations(sourcePlaylistId);
    }

    public List<SyncJob> findSpotifyDestinations(String sourceInput) {
        String sourcePlaylistId = youtubePlaylistParser.parse(sourceInput);
        return youtubeToSpotifyRegistry.findDestinations(sourcePlaylistId);
    }
}
