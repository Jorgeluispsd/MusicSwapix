package com.jorge.playlistconverter.controller.conversion;

import com.jorge.playlistconverter.converter.ConversionResult;
import com.jorge.playlistconverter.dto.response.PlaylistConversionResponse;
import com.jorge.playlistconverter.mapper.PlaylistConversionResponseMapper;
import com.jorge.playlistconverter.service.conversion.PlaylistConfirmationService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/conversions/previews")
public class PlaylistConfirmationController {

    private final PlaylistConfirmationService confirmationService;
    private final PlaylistConversionResponseMapper responseMapper;

    public PlaylistConfirmationController(PlaylistConfirmationService confirmationService,
                                          PlaylistConversionResponseMapper responseMapper) {
        this.confirmationService = Objects.requireNonNull(confirmationService);
        this.responseMapper = Objects.requireNonNull(responseMapper);
    }

    @PostMapping("/{previewId}/confirm")
    public PlaylistConversionResponse confirm(@PathVariable("previewId") UUID previewId){
        ConversionResult result = confirmationService.confirm(previewId);
        return responseMapper.toResponse(result);
    }
}
