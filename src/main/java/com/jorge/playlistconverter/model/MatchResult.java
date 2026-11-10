package com.jorge.playlistconverter.model;

import java.util.List;

public record MatchResult(
        Song sourceSong,
        String matchedId,       // null se não achou nenhum candidato
        String matchedTitle,    // título retornado pela busca, para você comparar visualmente
        double confidence,       // 0.0 a 1.0 — semelhança calculada pelo TrackMatcher
        List<TrackReviewOption> reviewOptions,
        String artworkUrl
) {
    public MatchResult{
        reviewOptions = List.copyOf(reviewOptions);
    }

    public MatchResult(Song sourceSong, String matchedId, String matchedTitle, double confidence){
        this(sourceSong, matchedId, matchedTitle, confidence, List.of());
    }

    public MatchResult(Song sourceSong, String matchedId, String matchedTitle, double confidence,
                       List<TrackReviewOption> reviewOptions){
        this(sourceSong, matchedId, matchedTitle, confidence, reviewOptions, null);
    }

    public boolean found() {
        return matchedId != null;
    }
}
