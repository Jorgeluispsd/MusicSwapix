package com.jorge.playlistconverter.errors.http;


import com.jorge.playlistconverter.dto.response.ApiErrorResponse;
import com.jorge.playlistconverter.dto.response.FieldValidationErrorResponse;
import com.jorge.playlistconverter.errors.conversion.PlaylistPreviewChangedException;
import com.jorge.playlistconverter.errors.conversion.PlaylistPreviewUnavailableException;
import com.jorge.playlistconverter.errors.input.InvalidPlaylistInputException;
import com.jorge.playlistconverter.errors.spotify.SpotifyPlaylistReadException;
import com.jorge.playlistconverter.errors.spotify.SpotifyRequestLimitException;
import com.jorge.playlistconverter.errors.youtube.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Objects;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception,
                                                             HttpServletRequest request){
        List<FieldValidationErrorResponse> fieldErros = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldValidationErrorResponse(error.getField(),
                        Objects.requireNonNullElse(error.getDefaultMessage(),"Valor inválido.")))
                .toList();

        ApiErrorResponse response = new ApiErrorResponse(HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                "Existem campos inválidos na requisição.", request.getRequestURI(), fieldErros);

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpServletRequest request){
        ApiErrorResponse response = new ApiErrorResponse(HttpStatus.BAD_REQUEST.value(),
                "INVALID_REQUEST_BODY",
                "O corpo JSON está ausente, malformado ou contém um valor inválido.",
                request.getRequestURI(), List.of());

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(InvalidPlaylistInputException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidPlaylistInput(HttpServletRequest request){
        ApiErrorResponse response = new ApiErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_PLAYLIST_INPUT",
                "Informe um link ou ID válido para a plataforma da playlist.",
                request.getRequestURI(), List.of());

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(PlaylistPreviewUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnavailablePreview(HttpServletRequest request){
        ApiErrorResponse response = new ApiErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "PREVIEW_UNAVAILABLE",
                "A prévia não está disponível para confirmação. Gere uma nova prévia.",
                request.getRequestURI(), List.of());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidPathParameter(HttpServletRequest request){
        ApiErrorResponse response = new ApiErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_PATH_PARAMETER",
                "Um parâmetro da URL possui formato inválido.",
                request.getRequestURI(), List.of());

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(PlaylistPreviewChangedException.class)
    public ResponseEntity<ApiErrorResponse> handleChangedPreview(HttpServletRequest request){
        ApiErrorResponse response = new ApiErrorResponse(HttpStatus.CONFLICT.value(),
                "PREVIEW_CHANGED",
                "O destino mudou desde a análise. Gere uma nova prévia antes de confirmar.",
                request.getRequestURI(), List.of());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(YoutubeQuotaExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleYoutubeQuotaExceeded(HttpServletRequest request){
        ApiErrorResponse response = new ApiErrorResponse(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "YOUTUBE_QUOTA_EXCEEDED",
                "A quota da API do YouTube foi esgotada. "
                        + "A operação foi interrompida. "
                        + "Confira o destino antes de iniciar uma nova tentativa.",
                request.getRequestURI(), List.of());

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
    }

    @ExceptionHandler(SpotifyRequestLimitException.class)
    public ResponseEntity<ApiErrorResponse> handleSpotifyRequestLimit(SpotifyRequestLimitException exception,
                                                                      HttpServletRequest request){
        ApiErrorResponse response = new ApiErrorResponse(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "SPOTIFY_RATE_LIMITED",
                "O Spotify limitou as requisições. "
                        + "Aguarde antes de iniciar uma nova tentativa.",
                request.getRequestURI(), List.of());

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS);

        Integer retryAfterSeconds = exception.getRetryAfterSeconds();

        if (retryAfterSeconds != null && retryAfterSeconds > 0){
            builder.header(HttpHeaders.RETRY_AFTER, retryAfterSeconds.toString());
        }

        return builder.body(response);
    }

    @ExceptionHandler(SpotifyPlaylistReadException.class)
    public ResponseEntity<ApiErrorResponse> handleSpotifyPlaylistRead(HttpServletRequest request){
        return platformFailure("SPOTIFY_PLAYLIST_READ_FAILED",
                "Não foi possível concluir a leitura da playlist no Spotify.", request);
    }

    @ExceptionHandler(YoutubePlaylistReadException.class)
    public ResponseEntity<ApiErrorResponse> handleYoutubePlaylistRead(HttpServletRequest request){
        return platformFailure("YOUTUBE_PLAYLIST_READ_FAILED",
                "Não foi possível concluir a leitura da playlist no YouTube.", request);
    }

    @ExceptionHandler(YoutubeSearchException.class)
    public ResponseEntity<ApiErrorResponse> handleYoutubeSearch(HttpServletRequest request){
        return platformFailure("YOUTUBE_SEARCH_FAILED",
                "Não foi possível concluir a busca de músicas no YouTube.", request);
    }

    @ExceptionHandler(YoutubePlaylistCreationException.class)
    public ResponseEntity<ApiErrorResponse> handleYoutubePlaylistCreation(HttpServletRequest request){
        return platformFailure("YOUTUBE_PLAYLIST_CREATION_FAILED",
                "Não foi possível confirmar a criação da playlist no YouTube. "
                        + "Confira sua conta antes de tentar criar outra.",
                request);
    }

    @ExceptionHandler(YoutubeTrackInsertionException.class)
    public ResponseEntity<ApiErrorResponse> handleYoutubeTrackInsertion(HttpServletRequest request){
        return platformFailure("YOUTUBE_TRACK_INSERTION_FAILED",
                "Não foi possível concluir a inserção das músicas no YouTube. "
                        + "Algumas músicas podem ter sido inseridas. "
                        + "Confira o destino e gere uma nova prévia antes de continuar.",
                request);
    }

    private ResponseEntity<ApiErrorResponse> platformFailure(String code, String message, HttpServletRequest request){
        ApiErrorResponse response = new ApiErrorResponse(HttpStatus.BAD_GATEWAY.value(),
                code, message, request.getRequestURI(), List.of());

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
    }
}
