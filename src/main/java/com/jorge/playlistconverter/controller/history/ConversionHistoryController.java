package com.jorge.playlistconverter.controller.history;

import com.jorge.playlistconverter.dto.response.ConversionHistoryResponse;
import com.jorge.playlistconverter.mapper.ConversionHistoryResponseMapper;
import com.jorge.playlistconverter.service.history.ConversionHistoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/conversions/history")
public class ConversionHistoryController {

    private final ConversionHistoryService historyService;
    private final ConversionHistoryResponseMapper responseMapper;

    public ConversionHistoryController(ConversionHistoryService historyService,
                                       ConversionHistoryResponseMapper responseMapper){
        this.historyService = Objects.requireNonNull(historyService);
        this.responseMapper = Objects.requireNonNull(responseMapper);
    }

    @GetMapping
    public List<ConversionHistoryResponse> findHistory(@RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestParam(name = "offset", defaultValue = "0") int offset){

        return historyService.findHistory(limit, offset).stream()
                .map(responseMapper::toResponse)
                .toList();
    }
}
