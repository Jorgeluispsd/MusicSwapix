package com.jorge.playlistconverter.config.database;

import com.jorge.playlistconverter.state.database.H2DatabaseInitializer;
import com.jorge.playlistconverter.state.database.H2SyncStateStore;
import com.jorge.playlistconverter.state.sql.SqlResourceLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataConfig {

    @Bean
    public H2SyncStateStore syncStateStore(SqlResourceLoader sqlResourceLoader, H2DatabaseInitializer databaseInitializer) {
        return new H2SyncStateStore(sqlResourceLoader, databaseInitializer);
    }

    @Bean
    public SqlResourceLoader sqlResourceLoader() {
        return new SqlResourceLoader();
    }

    @Bean
    public H2DatabaseInitializer databaseInitializer(SqlResourceLoader sqlResourceLoader) {
        return new H2DatabaseInitializer(sqlResourceLoader);
    }
}
