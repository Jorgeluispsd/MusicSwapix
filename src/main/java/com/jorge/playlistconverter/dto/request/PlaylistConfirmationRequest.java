package com.jorge.playlistconverter.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.Set;

public record PlaylistConfirmationRequest(
        @NotNull(message = "Informe o mapa de seleções.") Map<Integer, String> selections,
        Set<Integer> skippedPositions) {

    public PlaylistConfirmationRequest{
        skippedPositions = skippedPositions == null ? Set.of() : skippedPositions;
    }
}
