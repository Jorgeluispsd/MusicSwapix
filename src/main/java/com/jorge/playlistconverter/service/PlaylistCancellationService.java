package com.jorge.playlistconverter.service;

import com.jorge.playlistconverter.converter.preview.InMemoryPlaylistPreviewStore;

import java.util.UUID;

public class PlaylistCancellationService {
    private final InMemoryPlaylistPreviewStore previewStore;

    public PlaylistCancellationService(InMemoryPlaylistPreviewStore previewStore) {
        this.previewStore = previewStore;
    }

    public void cancel(UUID previewId){
        previewStore.consume(previewId);
    }
}
