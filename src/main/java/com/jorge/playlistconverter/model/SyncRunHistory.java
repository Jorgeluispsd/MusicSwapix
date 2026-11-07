package com.jorge.playlistconverter.model;

import java.time.LocalDateTime;

public record SyncRunHistory(long executionId, long syncJobId, String sourcePlaylistId,
                             String destinationPlaylistId, String direction, String status,
                             LocalDateTime startedAt, LocalDateTime completedAt) {
}
