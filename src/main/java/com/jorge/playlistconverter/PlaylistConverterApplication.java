package com.jorge.playlistconverter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PlaylistConverterApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(PlaylistConverterApplication.class);

        application.setHeadless(false);
        application.run(args);
    }
}
