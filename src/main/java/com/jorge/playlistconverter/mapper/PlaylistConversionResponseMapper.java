package com.jorge.playlistconverter.mapper;

import com.jorge.playlistconverter.converter.ConversionResult;
import com.jorge.playlistconverter.dto.response.PlaylistConversionResponse;
import com.jorge.playlistconverter.dto.response.UnmatchedTrackResponse;
import com.jorge.playlistconverter.model.MatchResult;

import java.util.List;
import java.util.Objects;

public class PlaylistConversionResponseMapper {

    public PlaylistConversionResponse toResponse(ConversionResult result){
        Objects.requireNonNull(result, "O resultado da conversão deve ser informado.");

        List<UnmatchedTrackResponse> notFound = result.matches().stream()
                .filter(match -> !match.found() && !match.skippedByUser())
                .map(match -> new UnmatchedTrackResponse(
                        match.sourceSong().id(),
                        match.sourceSong().title(),
                        match.sourceSong().artist())).toList();

        int skippedByUserCount = (int) result.matches().stream()
                .filter(MatchResult::skippedByUser)
                .count();

        return new PlaylistConversionResponse(result.sourcePlaylistId(),
                result.destinationPlaylistId(),
                "https://www.youtube.com/playlist?list="
                + result.destinationPlaylistId(), result.insertedCount(),
                result.skippedCount(), notFound.size(), notFound, skippedByUserCount);
    }
}
