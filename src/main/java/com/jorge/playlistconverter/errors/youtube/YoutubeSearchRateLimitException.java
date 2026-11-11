package com.jorge.playlistconverter.errors.youtube;

public class YoutubeSearchRateLimitException extends RuntimeException {
    public YoutubeSearchRateLimitException(Throwable cause) {
        super("O YouTube limitou as buscas.", cause);
    }
}
