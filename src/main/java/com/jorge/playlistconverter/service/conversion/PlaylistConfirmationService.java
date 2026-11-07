package com.jorge.playlistconverter.service.conversion;

import com.jorge.playlistconverter.converter.ConversionResult;
import com.jorge.playlistconverter.converter.PlaylistConverter;
import com.jorge.playlistconverter.converter.preview.InMemoryPlaylistPreviewStore;
import com.jorge.playlistconverter.converter.preview.StoredPlaylistPreview;
import com.jorge.playlistconverter.errors.conversion.PlaylistPreviewUnavailableException;

import java.util.Objects;
import java.util.UUID;

public class PlaylistConfirmationService {

    private final PlaylistConverter converter;
    private final InMemoryPlaylistPreviewStore previewStore;

    public PlaylistConfirmationService(PlaylistConverter converter,
                                       InMemoryPlaylistPreviewStore previewStore) {
        this.converter = Objects.requireNonNull(converter);
        this.previewStore = Objects.requireNonNull(previewStore);
    }

    public ConversionResult confirm(UUID previewId) {
        Objects.requireNonNull(previewId, "O identificador da prévia deve ser informado.");

        StoredPlaylistPreview stored = previewStore.consume(previewId)
                .orElseThrow(() -> new PlaylistPreviewUnavailableException(
                        "A prévia não está disponível para confirmação. "
                                + "Gere uma nova prévia."));

        return execute(stored);
    }

    private ConversionResult execute(StoredPlaylistPreview stored) {
        if (stored.preview().destinationPlaylistId() == null){
            return converter.executeNewPlaylistPreview(stored.preview(),
                    stored.destinationName(), stored.visibility());
        }

        return converter.executeExistingPlaylistPreview(stored.preview());
    }
}
