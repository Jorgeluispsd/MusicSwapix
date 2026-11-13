package com.jorge.playlistconverter.state.database;

import com.jorge.playlistconverter.state.sql.SqlResourceLoader;

import java.sql.DriverManager;

public class H2SyncStateStore extends JdbcSyncStateStore{

    public H2SyncStateStore(
            SqlResourceLoader sqlResourceLoader,
            H2DatabaseInitializer databaseInitializer) {
        super(
                sqlResourceLoader,
                connectionFactory(databaseInitializer),
                "sql/queries/mark_synced.sql"
        );
    }

    private static JdbcSyncStateStore.ConnectionFactory connectionFactory(
            H2DatabaseInitializer databaseInitializer) {
        String jdbcUrl = "jdbc:h2:" + databaseInitializer.initialize();
        return () -> DriverManager.getConnection(jdbcUrl);
    }
}
