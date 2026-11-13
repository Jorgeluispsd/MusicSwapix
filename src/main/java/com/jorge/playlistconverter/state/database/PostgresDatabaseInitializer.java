package com.jorge.playlistconverter.state.database;

import com.jorge.playlistconverter.errors.persistence.DatabaseInitializationException;
import com.jorge.playlistconverter.state.sql.SqlResourceLoader;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class PostgresDatabaseInitializer {

    private static final String SCHEMA_PATH = "sql/postgresql/schema.sql";

    private final SqlResourceLoader sqlResourceLoader;

    public PostgresDatabaseInitializer(SqlResourceLoader sqlResourceLoader) {
        this.sqlResourceLoader = sqlResourceLoader;
    }

    public void initialize(DataSource dataSource){
        String schema = sqlResourceLoader.read(SCHEMA_PATH);

        try(Connection connection = dataSource.getConnection();
            Statement statement = connection.createStatement()){
            connection.setAutoCommit(false);

            for (String command: schema.split(";")){
                if (!command.isBlank()){
                    statement.execute(command);
                }
            }

            connection.commit();

        }catch (SQLException e){
            throw new DatabaseInitializationException(
                    "Não foi possível inicializar a estrutura do PostgreSQL.", e);
        }
    }
}
