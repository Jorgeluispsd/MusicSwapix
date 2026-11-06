package com.jorge.playlistconverter.controller;

import com.jorge.playlistconverter.service.PlaylistCancellationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/conversions/previews")
public class PlaylistCancellationController {

    private final PlaylistCancellationService cancellationService;

    public PlaylistCancellationController(PlaylistCancellationService cancellationService){
        this.cancellationService = Objects.requireNonNull(cancellationService);
    }

    @DeleteMapping("/{previewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable("previewId")UUID previewId){
        cancellationService.cancel(previewId);
    }
}
