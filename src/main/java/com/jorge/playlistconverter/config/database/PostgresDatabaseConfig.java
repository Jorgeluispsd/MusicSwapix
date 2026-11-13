package com.jorge.playlistconverter.config.database;

import com.jorge.playlistconverter.state.SyncStateStore;
import com.jorge.playlistconverter.state.database.JdbcSyncStateStore;
import com.jorge.playlistconverter.state.database.PostgresDatabaseInitializer;
import com.jorge.playlistconverter.state.database.PostgresSyncStateStore;
import com.jorge.playlistconverter.state.sql.SqlResourceLoader;
import io.github.cdimascio.dotenv.Dotenv;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
@Profile("!h2")
public class PostgresDatabaseConfig {

    @Bean
    public DataSource postgresDataSource(
            Dotenv dotenv,
            SqlResourceLoader sqlResourceLoader,
            @Value("${playlist.converter.postgres.url:jdbc:postgresql://127.0.0.1:5432/musicswapix}") String jdbcUrl,
            @Value("${playlist.converter.postgres.user:musicswapix}") String username) {

        String password = dotenv.get("POSTGRES_PASSWORD");

        if (System.getenv("POSTG    RES_PASSWORD") == null
                && password != null
                && password.length() >= 2
                && password.startsWith("'")
                && password.endsWith("'")) {
            password = password.substring(1, password.length() - 1);
        }

        if (password == null || password.isBlank()){
            throw new IllegalStateException("Defina POSTGRES_PASSWORD para iniciar o PostgreSQL.");
        }

        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(jdbcUrl);
        dataSource.setUser(username);
        dataSource.setPassword(password);

        new PostgresDatabaseInitializer(sqlResourceLoader).initialize(dataSource);

        return dataSource;
    }

    @Bean
    public SyncStateStore postgresSyncStateStore(SqlResourceLoader sqlResourceLoader, DataSource postgresDataSource) {
        return new PostgresSyncStateStore(sqlResourceLoader, postgresDataSource);
    }
}
