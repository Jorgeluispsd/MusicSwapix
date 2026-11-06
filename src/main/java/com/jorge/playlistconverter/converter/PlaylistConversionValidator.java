package com.jorge.playlistconverter.converter;

import com.jorge.playlistconverter.converter.preview.PlaylistPreview;
import com.jorge.playlistconverter.enums.PlaylistVisibility;
import com.jorge.playlistconverter.errors.conversion.NoTracksToInsertException;

import java.util.Objects;

public class PlaylistConversionValidator {

    public void validateForNewPlaylist(PlaylistPreview preview,
                                       String destinationName,
                                       PlaylistVisibility visibility) {
        Objects.requireNonNull(preview, "A prévia deve ser informada.");

        validateNewPlaylistOptions(destinationName, visibility);
        validateSource(preview);

        if (preview.destinationPlaylistId() != null
                || !preview.existingDestinationIds().isEmpty()){
            throw new IllegalArgumentException("A prévia deve ser destinada à criação de uma nova playlist.");
        }

        if (preview.newTrackCount() == 0){
            throw new NoTracksToInsertException(
                    "A prévia não possui músicas encontradas para adicionar.");
        }
    }

    public void validateForExistingPlaylist(PlaylistPreview preview){
        Objects.requireNonNull(preview, "A prévia deve ser informada.");

        validatePlaylistIds(preview.sourcePlaylistId(),
                preview.destinationPlaylistId());
    }

    public void validatePlaylistIds(String sourcePlaylistId, String destinationPlaylistId){
        requireNonBlank(sourcePlaylistId,
                "O ID da playlist de origem deve ser informado.");
        requireNonBlank(destinationPlaylistId,
                "O ID da playlist de destino deve ser informado.");
    }

    private void validateSource(PlaylistPreview preview){
        requireNonBlank(preview.sourcePlaylistId(),
                "O ID da playlist de origem deve ser informado.");
    }

    private void requireNonBlank(String value, String message){
        if (value == null || value.isBlank()){
            throw new IllegalArgumentException(message);
        }
    }

    public void validateNewPlaylistOptions(String destinationName, PlaylistVisibility visibility){
        Objects.requireNonNull(visibility, "A visibilidade da playlist deve ser informada.");
        requireNonBlank(destinationName, "O nome da playlist de destino deve ser informado.");
    }
}
