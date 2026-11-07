package com.jorge.playlistconverter.config.database;

import lombok.extern.slf4j.Slf4j;
import org.h2.tools.Server;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.SQLException;

@Slf4j
@Configuration
public class H2ConsoleConfig {

    @Bean(initMethod = "start", destroyMethod = "stop")
    public Server h2ConsoleServer() throws SQLException {
        return Server.createWebServer("-webPort", "8082");
    }

    @Bean
    public ApplicationListener<ApplicationStartedEvent> h2ConsoleLinkLogger(Server h2ConsoleServer) {
        return event -> {
            log.info("Console H2  disponível em: {}", h2ConsoleServer.getURL());
        };
    }
}
