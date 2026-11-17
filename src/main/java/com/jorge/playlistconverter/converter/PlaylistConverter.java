package com.jorge.playlistconverter.converter;

import com.jorge.playlistconverter.converter.execution.ExistingPlaylistExecutor;
import com.jorge.playlistconverter.converter.execution.NewPlaylistExecutor;
import com.jorge.playlistconverter.converter.preview.PlaylistPreview;
import com.jorge.playlistconverter.enums.PlaylistVisibility;

import com.jorge.playlistconverter.model.MatchResult;
import com.jorge.playlistconverter.model.Song;
import com.jorge.playlistconverter.service.MusicService;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class PlaylistConverter {
    private final PlaylistAnalyzer analyzer;
    private final MusicService destination;
    private final NewPlaylistExecutor newPlaylistExecutor;
    private final ExistingPlaylistExecutor existingPlaylistExecutor;
    private final PlaylistConversionValidator validator;

    public PlaylistConverter(PlaylistAnalyzer analyzer, MusicService destination,
                             NewPlaylistExecutor newPlaylistExecutor,
                             ExistingPlaylistExecutor existingPlaylistExecutor,
                             PlaylistConversionValidator validator) {
        this.analyzer = Objects.requireNonNull(analyzer);
        this.destination = Objects.requireNonNull(destination);
        this.newPlaylistExecutor = Objects.requireNonNull(newPlaylistExecutor);
        this.existingPlaylistExecutor = Objects.requireNonNull(existingPlaylistExecutor);
        this.validator = Objects.requireNonNull(validator);
    }

    public PlaylistPreview previewNewPlaylist(String sourcePlaylistId) {
        List<MatchResult> matches = analyzer.analyze(sourcePlaylistId);

        return new PlaylistPreview(sourcePlaylistId, matches);
    }

    public PlaylistPreview previewExistingPlaylist(String sourcePlaylistId,
                                                   String destinationPlaylistId){
        validator.validatePlaylistIds(sourcePlaylistId, destinationPlaylistId);

        List<Song> existingSongs =
                destination.getPlaylistTracksAlreadyAdded(destinationPlaylistId);

        Set<String> existingIds = existingSongs.stream()
                .map(Song::id)
                .collect(Collectors.toSet());

        List<MatchResult> matches = analyzer.analyze(sourcePlaylistId, existingSongs);

        return new PlaylistPreview(sourcePlaylistId, destinationPlaylistId,
                matches, existingIds);
    }

    public ConversionResult executeExistingPlaylistPreview(PlaylistPreview preview){

        return existingPlaylistExecutor.execute(preview);
    }

    public ConversionResult executeNewPlaylistPreview(PlaylistPreview preview,
                                                      String destinationPlaylistName){

        return executeNewPlaylistPreview(preview, destinationPlaylistName, PlaylistVisibility.PRIVATE);
    }

    public ConversionResult executeNewPlaylistPreview(PlaylistPreview preview,
                                                      String destinationPlaylistName,
                                                      PlaylistVisibility visibility){

        return newPlaylistExecutor.execute(preview, destinationPlaylistName, visibility);
    }
}
