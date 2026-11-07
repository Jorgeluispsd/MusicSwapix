package com.jorge.playlistconverter.dto.response;

import java.time.LocalDateTime;

public record ConversionHistoryResponse(long executionId, String sourcePlaylistId, String destinationPlaylistId,
                                        String destinationPlaylistUrl, String direction, String status,
                                        LocalDateTime startedAt, LocalDateTime completedAt) {
}
