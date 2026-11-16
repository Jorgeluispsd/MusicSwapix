package com.jorge.playlistconverter.errors.spotify;

public class SpotifySearchException extends RuntimeException {
    public SpotifySearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
