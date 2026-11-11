package com.jorge.playlistconverter.service.conversion;

import com.jorge.playlistconverter.converter.ConversionResult;
import com.jorge.playlistconverter.converter.PlaylistConverter;
import com.jorge.playlistconverter.converter.preview.InMemoryPlaylistPreviewStore;
import com.jorge.playlistconverter.converter.preview.StoredPlaylistPreview;
import com.jorge.playlistconverter.errors.conversion.PlaylistPreviewUnavailableException;
import com.jorge.playlistconverter.converter.preview.PlaylistPreview;
import com.jorge.playlistconverter.errors.conversion.InvalidTrackSelectionException;
import com.jorge.playlistconverter.model.MatchResult;
import com.jorge.playlistconverter.model.TrackReviewOption;

import java.util.Objects;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PlaylistConfirmationService {

    private final PlaylistConverter converter;
    private final InMemoryPlaylistPreviewStore previewStore;

    public PlaylistConfirmationService(PlaylistConverter converter,
                                       InMemoryPlaylistPreviewStore previewStore) {
        this.converter = Objects.requireNonNull(converter);
        this.previewStore = Objects.requireNonNull(previewStore);
    }

    public ConversionResult confirm(UUID previewId) {
        return confirm(previewId, Map.of(), Set.of());
    }

    public ConversionResult confirm(UUID previewId, Map<Integer, String> selections) {
        return confirm(previewId, selections, Set.of());
    }

    public ConversionResult confirm(UUID previewId, Map<Integer, String> selections,
                                    Set<Integer> skippedPositions) {
        Objects.requireNonNull(previewId, "O identificador da prévia deve ser informado.");
        Objects.requireNonNull(selections, "As seleções devem ser informadas.");
        Objects.requireNonNull(skippedPositions, "As posições puladas devem ser informadas.");

        StoredPlaylistPreview stored = previewStore.find(previewId)
                .orElseThrow(this::unavailablePreview);

        PlaylistPreview resolved = applySelections(stored.preview(), selections, skippedPositions);

        previewStore.consume(previewId).orElseThrow(this::unavailablePreview);

        return execute(stored, resolved);
    }

    private PlaylistPreview applySelections(PlaylistPreview preview, Map<Integer, String> selections,
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

    private PlaylistPreviewUnavailableException unavailablePreview(){
        return new PlaylistPreviewUnavailableException(
                "A prévia não está disponível para confirmação. Gere uma nova prévia.");
    }

    private ConversionResult execute(StoredPlaylistPreview stored, PlaylistPreview preview) {
        if (preview.destinationPlaylistId() == null){
            return converter.executeNewPlaylistPreview(preview, stored.destinationName(), stored.visibility());
        }

        return converter.executeExistingPlaylistPreview(preview);
    }
}
