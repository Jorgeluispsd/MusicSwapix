package com.jorge.playlistconverter.mapper;

import com.jorge.playlistconverter.dto.response.ConversionHistoryResponse;
import com.jorge.playlistconverter.enums.ConversionDirection;
import com.jorge.playlistconverter.model.SyncRunHistory;

import java.util.Objects;

public class ConversionHistoryResponseMapper {

    public ConversionHistoryResponse toResponse(SyncRunHistory execution){
        Objects.requireNonNull(execution, "A execução deve ser informada.");

        return new ConversionHistoryResponse(
                execution.executionId(),
                execution.sourcePlaylistId(),
                execution.destinationPlaylistId(),
                destinationUrl(execution),
                execution.direction(),
                Objects.requireNonNullElse(execution.status(), "UNKNOWN"),
                execution.startedAt(),
                execution.completedAt());

    }

    private String destinationUrl(SyncRunHistory execution){
        if (ConversionDirection.SPOTIFY_TO_YOUTUBE.name()
                .equals(execution.direction())){
            return "https://www.youtube.com/playlist?list=" + execution.destinationPlaylistId();
        }

        return null;
    }
}
