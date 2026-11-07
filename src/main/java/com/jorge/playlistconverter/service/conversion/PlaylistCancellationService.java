package com.jorge.playlistconverter.service.conversion;

import com.jorge.playlistconverter.converter.preview.InMemoryPlaylistPreviewStore;

import java.util.Objects;
import java.util.UUID;

public class PlaylistCancellationService {
    private final InMemoryPlaylistPreviewStore previewStore;

    public PlaylistCancellationService(InMemoryPlaylistPreviewStore previewStore) {
        this.previewStore = Objects.requireNonNull(previewStore);
    }

    public void cancel(UUID previewId){
        previewStore.consume(previewId);
    }
}
