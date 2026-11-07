package com.jorge.playlistconverter.service.history;

import com.jorge.playlistconverter.errors.input.InvalidHistoryPaginationException;
import com.jorge.playlistconverter.model.SyncRunHistory;
import com.jorge.playlistconverter.state.SyncStateStore;

import java.util.List;
import java.util.Objects;

public class ConversionHistoryService {

    private static final int MAX_LIMIT = 100;

    private final SyncStateStore store;

    public ConversionHistoryService(SyncStateStore store) {
        this.store = Objects.requireNonNull(store);
    }

    public List<SyncRunHistory> findHistory(int limit, int offset){
        if (limit < 1 || limit > MAX_LIMIT || offset < 0){
            throw new InvalidHistoryPaginationException(
                    "Informe um limite entre 1 e 100 e um deslocamento maior ou igual a zero.");
        }

        return store.findSyncRunHistory(limit, offset);
    }
}
