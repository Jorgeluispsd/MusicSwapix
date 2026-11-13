package com.jorge.playlistconverter.service.conversion;

import com.jorge.playlistconverter.converter.ConversionResult;
import com.jorge.playlistconverter.converter.PlaylistConverter;
import com.jorge.playlistconverter.converter.preview.InMemoryPlaylistPreviewStore;
import com.jorge.playlistconverter.converter.preview.StoredPlaylistPreview;
import com.jorge.playlistconverter.errors.conversion.PlaylistPreviewUnavailableException;
import com.jorge.playlistconverter.converter.preview.PlaylistPreview;

import java.util.Objects;
import java.util.UUID;
import java.util.Map;
import java.util.Set;

public class PlaylistConfirmationService {

    private final PlaylistConverter converter;
    private final InMemoryPlaylistPreviewStore previewStore;

    public PlaylistConfirmationService(PlaylistConverter converter,
                                       InMemoryPlaylistPreviewStore previewStore) {
        this.converter = Objects.requireNonNull(converter);
        this.previewStore = Objects.requireNonNull(previewStore);
    }

    public ConversionResult confirm(UUID previewId) {
        return confirm(previewId, Map.of(), Set.of());
    }

    public ConversionResult confirm(UUID previewId, Map<Integer, String> selections) {
        return confirm(previewId, selections, Set.of());
    }

    public ConversionResult confirm(UUID previewId, Map<Integer, String> selections,
                                    Set<Integer> skippedPositions) {
        Objects.requireNonNull(previewId, "O identificador da prévia deve ser informado.");
        Objects.requireNonNull(selections, "As seleções devem ser informadas.");
        Objects.requireNonNull(skippedPositions, "As posições puladas devem ser informadas.");

        StoredPlaylistPreview stored = previewStore.find(previewId)
                .orElseThrow(this::unavailablePreview);

        PlaylistPreview resolved = PlaylistReviewSelections.apply(stored.preview(), selections, skippedPositions);

        previewStore.consume(previewId).orElseThrow(this::unavailablePreview);

        return execute(stored, resolved);
    }

    private PlaylistPreviewUnavailableException unavailablePreview(){
        return new PlaylistPreviewUnavailableException(
                "A prévia não está disponível para confirmação. Gere uma nova prévia.");
    }

    private ConversionResult execute(StoredPlaylistPreview stored, PlaylistPreview preview) {
        if (preview.destinationPlaylistId() == null){
            return converter.executeNewPlaylistPreview(preview, stored.destinationName(), stored.visibility());
        }

        return converter.executeExistingPlaylistPreview(preview);
    }
}
