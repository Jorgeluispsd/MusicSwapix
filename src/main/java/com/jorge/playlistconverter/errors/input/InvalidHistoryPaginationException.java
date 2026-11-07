package com.jorge.playlistconverter.errors.input;

public class InvalidHistoryPaginationException extends IllegalArgumentException {
    public InvalidHistoryPaginationException(String message) {
        super(message);
    }
}
