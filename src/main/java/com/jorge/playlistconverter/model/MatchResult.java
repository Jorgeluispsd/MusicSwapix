package com.jorge.playlistconverter.model;

import java.util.List;

/**
 * Resultado do TrackMatcher para uma faixa de origem e seus candidatos no destino.
 * A pontuação representa semelhança, não probabilidade de acerto nem confirmação do usuário.
 * A escolha/revisão dos candidatos na interface será implementada na Fase 8.
 */
public record MatchResult(
        Song sourceSong,
        String matchedId,       // null se não achou nenhum candidato
        String matchedTitle,    // título retornado pela busca, para você comparar visualmente
        double confidence,       // 0.0 a 1.0 — semelhança calculada pelo TrackMatcher
        List<TrackReviewOption> reviewOptions
) {
    public MatchResult{
        reviewOptions = List.copyOf(reviewOptions);
    }

    public MatchResult(Song sourceSong, String matchedId, String matchedTitle, double confidence){
        this(sourceSong, matchedId, matchedTitle, confidence, List.of());
    }

    public boolean found() {
        return matchedId != null;
    }
}
