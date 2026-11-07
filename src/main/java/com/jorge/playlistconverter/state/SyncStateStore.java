package com.jorge.playlistconverter.state;

import com.jorge.playlistconverter.enums.SyncRunStatus;
import com.jorge.playlistconverter.model.Song;
import com.jorge.playlistconverter.model.SyncJob;
import com.jorge.playlistconverter.model.SyncRunHistory;

import java.util.OptionalLong;
import java.util.List;

/**
 * Contrato de persistência de trabalhos, execuções e faixas sincronizadas.
 * Implementado por state.database.H2SyncStateStore com JDBC e recursos SQL.
 * O histórico atual não armazena candidatos de pesquisa nem representa uma prévia de conversão.
 */
public interface SyncStateStore {

    boolean isAlreadySynced(long syncJobId, String sourceTrackId);

    void markSynced(long syncJobId, Song sourceSong, String destinationId, String status, double confidence);

    List<String> getHistory(long syncJobId);

    long createSyncJob(String sourcePlaylistId, String destinationPlaylistId, String direction);

    OptionalLong findSyncJob(String sourcePlaylistId, String destinationPlaylistId, String direction);

    long getOrCreateSyncJob(String sourcePlaylistId, String destinationPlaylistId, String direction);

    long startSyncRun(long syncJobId);

    void finishSyncRun(long syncRunId, SyncRunStatus status);

    List<SyncJob> findSyncJobsBySource(String sourcePlaylistId, String direction);

    List<SyncRunHistory> findSyncRunHistory(int limit, int offset);
}
