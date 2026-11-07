package com.jorge.playlistconverter.service.conversion;

import com.jorge.playlistconverter.converter.PlaylistConversionValidator;
import com.jorge.playlistconverter.converter.PlaylistConverter;
import com.jorge.playlistconverter.converter.preview.InMemoryPlaylistPreviewStore;
import com.jorge.playlistconverter.converter.preview.PlaylistPreview;
import com.jorge.playlistconverter.converter.preview.StoredPlaylistPreview;
import com.jorge.playlistconverter.enums.PlaylistVisibility;
import com.jorge.playlistconverter.input.SpotifyPlaylistParser;
import com.jorge.playlistconverter.input.YoutubePlaylistParser;

import java.util.Objects;

public class PlaylistPreviewService {

    private final PlaylistConverter converter;
    private final InMemoryPlaylistPreviewStore previewStore;
    private final SpotifyPlaylistParser spotifyParser;
    private final YoutubePlaylistParser youtubeParser;
    private final PlaylistConversionValidator validator;

    public PlaylistPreviewService(PlaylistConverter converter, InMemoryPlaylistPreviewStore previewStore,
                                  SpotifyPlaylistParser spotifyParser, YoutubePlaylistParser youtubeParser,
                                  PlaylistConversionValidator validator) {
        this.converter = Objects.requireNonNull(converter);
        this.previewStore = Objects.requireNonNull(previewStore);
        this.spotifyParser = Objects.requireNonNull(spotifyParser);
        this.youtubeParser = Objects.requireNonNull(youtubeParser);
        this.validator = Objects.requireNonNull(validator);
    }

    public StoredPlaylistPreview prepareNewPlaylist(String sourceInput, String destinationName,
                                                    PlaylistVisibility visibility){
        validator.validateNewPlaylistOptions(destinationName, visibility);

        String sourcePlaylistId = spotifyParser.parse(sourceInput);

        PlaylistPreview preview = converter.previewNewPlaylist(sourcePlaylistId);

        return previewStore.save(preview, destinationName.strip(), visibility);
    }

    public StoredPlaylistPreview prepareExistingPlaylist(String sourceInput, String destinationInput){
        String sourcePlaylistId = spotifyParser.parse(sourceInput);
        String destinationPlaylistId = youtubeParser.parse(destinationInput);

        PlaylistPreview preview = converter.previewExistingPlaylist(sourcePlaylistId, destinationPlaylistId);

        return previewStore.save(preview, null, null);
    }
}
