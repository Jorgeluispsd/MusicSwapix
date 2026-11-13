package com.jorge.playlistconverter.state.database;

import com.jorge.playlistconverter.state.sql.SqlResourceLoader;

import javax.sql.DataSource;

public class PostgresSyncStateStore extends JdbcSyncStateStore{

    private static final String MARK_SYNCED_PATH = "sql/postgresql/queries/mark_synced.sql";

    public PostgresSyncStateStore(SqlResourceLoader sqlResourceLoader, DataSource dataSource){
        super(sqlResourceLoader, dataSource::getConnection, MARK_SYNCED_PATH);
    }
}
