package com.jorge.playlistconverter.model;

public record Song(
        String id,          // ID da faixa no serviço de origem
        String title,
        String artist,
        int durationMs,
        String artworkUrl
) {
    public Song(String id, String title, String artist, int durationMs){
        this(id, title, artist, durationMs, null);
    }
}
