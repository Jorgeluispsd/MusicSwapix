package com.jorge.playlistconverter.service.history;

import com.jorge.playlistconverter.errors.input.InvalidHistoryPaginationException;
import com.jorge.playlistconverter.errors.persistence.SyncStateStorageException;
import com.jorge.playlistconverter.model.SyncRunHistory;
import com.jorge.playlistconverter.state.SyncStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConversionHistoryServiceTest {

    private SyncStateStore store;
    private ConversionHistoryService service;

    @BeforeEach
    void setUp() {
        store = mock(SyncStateStore.class);
        service = new ConversionHistoryService(store);
    }

    @Test
    void shouldReturnHistoryForRequestedPagination() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 11, 7, 14, 0);

        List<SyncRunHistory> expected = List.of(
                new SyncRunHistory(
                        25L,
                        10L,
                        "source-playlist",
                        "destination-playlist",
                        "SPOTIFY_TO_YOUTUBE",
                        "SUCCESS",
                        startedAt,
                        startedAt.plusMinutes(1)));

        when(store.findSyncRunHistory(20, 40)).thenReturn(expected);

        List<SyncRunHistory> result = service.findHistory(20, 40);

        assertEquals(expected, result);
        verify(store).findSyncRunHistory(20, 40);
        verifyNoMoreInteractions(store);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    void shouldAcceptHistoryLimitBoundaries(int limit) {
        when(store.findSyncRunHistory(limit, 0)).thenReturn(List.of());

        assertTrue(service.findHistory(limit, 0).isEmpty());

        verify(store).findSyncRunHistory(limit, 0);
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "-1, 0",
            "101, 0",
            "20, -1"
    })
    void shouldRejectInvalidPaginationBeforeQueryingStore(int limit, int offset) {
        assertThrows(InvalidHistoryPaginationException.class,
                () -> service.findHistory(limit, offset));

        verifyNoInteractions(store);
    }

    @Test
    void shouldPreserveStorageFailure() {
        SyncStateStorageException original = new SyncStateStorageException(
                "Falha simulada na consulta.",
                new SQLException("Causa simulada."));

        when(store.findSyncRunHistory(20, 0)).thenThrow(original);

        SyncStateStorageException result = assertThrows(
                SyncStateStorageException.class,
                () -> service.findHistory(20, 0));

        assertSame(original, result);
        verify(store).findSyncRunHistory(20, 0);
        verifyNoMoreInteractions(store);
    }
}
