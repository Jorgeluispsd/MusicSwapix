package com.jorge.playlistconverter.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record PlaylistConfirmationRequest(
        @NotNull(message = "Informe o mapa de seleções.") Map<Integer, String> selections) {
}
