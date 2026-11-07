package com.jorge.playlistconverter.config.youtube;

import com.jorge.playlistconverter.youtube.authorization.GoogleAuthService;
import com.jorge.playlistconverter.youtube.authorization.GoogleAuthorizationBrowser;
import com.jorge.playlistconverter.youtube.authorization.GoogleAuthorizationFlowFactory;
import com.jorge.playlistconverter.youtube.authorization.GoogleClientSecretsLoader;
import com.jorge.playlistconverter.youtube.callback.GoogleCallbackServer;
import com.jorge.playlistconverter.youtube.session.GoogleSessionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class YoutubeAuthorizationConfig {

    @Bean
    public GoogleClientSecretsLoader googleClientSecretsLoader() {
        return new GoogleClientSecretsLoader();
    }

    @Bean
    public GoogleAuthorizationFlowFactory googleAuthorizationFlowFactory(GoogleClientSecretsLoader secretsLoader) {
        return new GoogleAuthorizationFlowFactory(secretsLoader);
    }

    @Bean
    public GoogleCallbackServer googleCallbackServer() {
        return new GoogleCallbackServer();
    }

    @Bean
    public GoogleAuthorizationBrowser googleAuthorizationBrowser() {
        return new GoogleAuthorizationBrowser();
    }

    @Bean
    public GoogleAuthService googleAuthService(GoogleAuthorizationFlowFactory flowFactory,
                                                GoogleCallbackServer callbackServer,
                                                GoogleAuthorizationBrowser browser,
                                                GoogleSessionManager sessionManager) {
        return new GoogleAuthService(flowFactory, callbackServer, browser, sessionManager);
    }

    @Bean
    public GoogleSessionManager googleSessionManager() {
        return new GoogleSessionManager();
    }
}
