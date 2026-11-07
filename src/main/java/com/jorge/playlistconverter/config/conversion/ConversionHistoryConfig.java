package com.jorge.playlistconverter.config.conversion;

import com.jorge.playlistconverter.mapper.ConversionHistoryResponseMapper;
import com.jorge.playlistconverter.service.history.ConversionHistoryService;
import com.jorge.playlistconverter.state.SyncStateStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConversionHistoryConfig {

    @Bean
    public ConversionHistoryService conversionHistoryService(SyncStateStore store){
        return new ConversionHistoryService(store);
    }

    @Bean
    public ConversionHistoryResponseMapper conversionHistoryResponseMapper(){
        return new ConversionHistoryResponseMapper();
    }
}
