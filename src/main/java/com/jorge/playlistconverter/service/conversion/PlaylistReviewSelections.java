package com.jorge.playlistconverter.service.conversion;

import com.jorge.playlistconverter.converter.preview.PlaylistPreview;
import com.jorge.playlistconverter.errors.conversion.InvalidTrackSelectionException;
import com.jorge.playlistconverter.model.MatchResult;
import com.jorge.playlistconverter.model.TrackReviewOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PlaylistReviewSelections {

    private PlaylistReviewSelections() {
    }

    static PlaylistPreview apply(PlaylistPreview preview, Map<Integer, String> selections,
                                            Set<Integer> skippedPositions) {
        if (selections.isEmpty() && skippedPositions.isEmpty()) {
            return preview;
        }

        List<MatchResult> matches = new ArrayList<>(preview.matches());

        for (Integer position : skippedPositions) {
            if (position == null || position < 0 || position >= matches.size()
                    || selections.containsKey(position)
                    || !matches.get(position).found()) {
                throw new InvalidTrackSelectionException(
                        "Uma posição pulada é inválida ou possui decisões conflitantes.");
            }

            matches.set(position, matches.get(position).skipByUser());
        }

        for (var selection : selections.entrySet()) {
            Integer position = selection.getKey();
            String videoId = selection.getValue();

            if (position == null || position < 0 || position >= matches.size()
                    || videoId == null || videoId.isBlank()) {
                throw new InvalidTrackSelectionException(
                        "A posição da faixa ou o vídeo selecionado é inválido.");
            }

            MatchResult original = matches.get(position);

            TrackReviewOption option = original.reviewOptions().stream()
                    .filter(candidate -> videoId.equals(candidate.song().id()))
                    .findFirst()
                    .orElseThrow(() -> new InvalidTrackSelectionException(
                            "O vídeo escolhido não é uma opção para esta faixa."));

            matches.set(position, new MatchResult(
                    original.sourceSong(),
                    option.song().id(),
                    option.song().title(),
                    option.confidence(),
                    List.of(),
                    option.song().artworkUrl()));
        }

        return new PlaylistPreview(
                preview.sourcePlaylistId(),
                preview.destinationPlaylistId(),
                matches,
                preview.existingDestinationIds());
    }

}
