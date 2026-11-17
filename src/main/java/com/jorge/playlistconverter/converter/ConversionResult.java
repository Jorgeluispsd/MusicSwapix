package com.jorge.playlistconverter.converter;

import com.jorge.playlistconverter.enums.ConversionDirection;
import com.jorge.playlistconverter.model.MatchResult;

import java.util.List;
import java.util.Objects;

public record ConversionResult(String sourcePlaylistId,
                               String destinationPlaylistId,
                               List<MatchResult> matches,
                               int insertedCount, int skippedCount,
                               ConversionDirection direction
) {
   public ConversionResult {
       matches = List.copyOf(matches);
       Objects.requireNonNull(direction, "A direção deve ser informada.");
   }

   public ConversionResult(String sourcePlaylistId, String destinationPlaylistId,
                           List<MatchResult> matches, int insertedCount, int skippedCount){
       this(sourcePlaylistId, destinationPlaylistId, matches, insertedCount, skippedCount,
               ConversionDirection.SPOTIFY_TO_YOUTUBE);
   }

   public ConversionResult withDirection(ConversionDirection direction){
       return new ConversionResult(sourcePlaylistId, destinationPlaylistId, matches,
               insertedCount, skippedCount, direction);
   }
}
