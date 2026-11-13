package com.jorge.playlistconverter.state.database;

import com.jorge.playlistconverter.enums.SyncRunStatus;
import com.jorge.playlistconverter.errors.persistence.SyncStateStorageException;
import com.jorge.playlistconverter.model.Song;
import com.jorge.playlistconverter.model.SyncJob;
import com.jorge.playlistconverter.model.SyncRunHistory;
import com.jorge.playlistconverter.state.SyncStateStore;
import com.jorge.playlistconverter.state.sql.SqlResourceLoader;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

public class JdbcSyncStateStore implements SyncStateStore {

    private static final String IS_ALREADY_SYNCED_PATH = "sql/queries/is_already_synced.sql";
    private static final String MARK_SYNCED_PATH = "sql/queries/mark_synced.sql";
    private static final String GET_HISTORY_PATH = "sql/queries/get_history.sql";
    private static final String CREATE_SYNC_JOB_PATH = "sql/queries/create_sync_job.sql";
    private static final String FIND_SYNC_JOB_PATH = "sql/queries/find_sync_job.sql";
    private static final String START_SYNC_RUN_PATH = "sql/queries/start_sync_run.sql";
    private static final String FINISH_SYNC_RUN_PATH = "sql/queries/finish_sync_run.sql";
    private static final String FIND_SYNC_JOBS_BY_SOURCE_PATH = "sql/queries/find_sync_jobs_by_source.sql";
    private static final String FIND_SYNC_RUN_HISTORY_PATH = "sql/queries/find_sync_run_history.sql";
    private static final String COUNT_SYNC_RUN_HISTORY_PATH = "sql/queries/count_sync_run_history.sql";

    @FunctionalInterface
    interface ConnectionFactory {
        Connection open() throws SQLException;
    }

    private final ConnectionFactory connectionFactory;

    private final String isAlreadySyncedSql;
    private final String markSyncedSql;
    private final String getHistorySql;
    private final String createSyncJobSql;
    private final String findSyncJobSql;
    private final String startSyncRunSql;
    private final String finishSyncRunSql;
    private final String findSyncJobsBySourceSql;
    private final String findSyncRunHistorySql;
    private final String countSyncRunHistorySql;

    JdbcSyncStateStore(SqlResourceLoader sqlResourceLoader,  ConnectionFactory connectionFactory,
                              String markSyncedPath){
        this.connectionFactory = connectionFactory;

        this.isAlreadySyncedSql = sqlResourceLoader.read(IS_ALREADY_SYNCED_PATH);
        this.markSyncedSql = sqlResourceLoader.read(markSyncedPath);
        this.getHistorySql = sqlResourceLoader.read(GET_HISTORY_PATH);
        this.createSyncJobSql = sqlResourceLoader.read(CREATE_SYNC_JOB_PATH);
        this.findSyncJobSql = sqlResourceLoader.read(FIND_SYNC_JOB_PATH);
        this.startSyncRunSql = sqlResourceLoader.read(START_SYNC_RUN_PATH);
        this.finishSyncRunSql = sqlResourceLoader.read(FINISH_SYNC_RUN_PATH);
        this.findSyncJobsBySourceSql = sqlResourceLoader.read(FIND_SYNC_JOBS_BY_SOURCE_PATH);
        this.findSyncRunHistorySql = sqlResourceLoader.read(FIND_SYNC_RUN_HISTORY_PATH);
        this.countSyncRunHistorySql = sqlResourceLoader.read(COUNT_SYNC_RUN_HISTORY_PATH);
    }

    private Connection getConnection() throws SQLException {
        return connectionFactory.open();
    }

    @Override
    public long createSyncJob(String sourcePlaylistId, String destinationPlaylistId, String direction){
        try(Connection conn = getConnection();
            PreparedStatement pstmt = conn.prepareStatement(createSyncJobSql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, sourcePlaylistId);
            pstmt.setString(2, destinationPlaylistId);
            pstmt.setString(3, direction);
            pstmt.executeUpdate();

            try(ResultSet keys = pstmt.getGeneratedKeys()) {
                if (keys.next()){
                    return keys.getLong(1);
                }
                throw new SQLException("Nenhum ID gerado para o novo sync_job");
            }

        }catch (SQLException e) {
            throw new SyncStateStorageException("Erro ao criar sync_job", e);
        }
    }

    @Override
    public boolean isAlreadySynced(long syncJobId, String sourceTrackId){
        try(Connection conn = getConnection();
            PreparedStatement pstmt = conn.prepareStatement(isAlreadySyncedSql)) {

            pstmt.setLong(1, syncJobId);
            pstmt.setString(2, sourceTrackId);

            try(ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;

            }
        }catch (SQLException e) {
            throw new SyncStateStorageException("Erro ao verificar se a faixa já foi sincronizada", e);
        }
    }


    @Override
    public void markSynced(long syncJobId, Song sourceSong, String destinationId, String status, double confidence){

        try(Connection conn = getConnection();
            PreparedStatement pstmt = conn.prepareStatement(markSyncedSql)) {

            pstmt.setLong(1, syncJobId);
            pstmt.setString(2, sourceSong.id());
            pstmt.setString(3, sourceSong.title());
            pstmt.setString(4, sourceSong.artist());
            pstmt.setString(5, destinationId);
            pstmt.setDouble(6, confidence);
            pstmt.setString(7, status);
            pstmt.setTimestamp(8, Timestamp.from(Instant.now()));

            pstmt.executeUpdate();
        }catch (SQLException e) {
            throw new SyncStateStorageException("Erro ao marcar a faixa como sincronizada", e);
        }
    }

    @Override
    public List<String> getHistory(long syncJobId){
        List<String> history = new ArrayList<>();

        try(Connection conn = getConnection();
            PreparedStatement pstmt = conn.prepareStatement(getHistorySql)) {

            pstmt.setLong(1, syncJobId);

            try(ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()){
                    history.add(rs.getString("source_track_id"));
                }
            }

        }catch (SQLException e) {
            throw new SyncStateStorageException("Erro ao obter o histórico de sincronização", e);
        }

        return history;
    }

    @Override
    public OptionalLong findSyncJob(String sourcePlaylistId, String destinationPlaylistId, String direction){
        try (Connection conn = getConnection();
        PreparedStatement statement = conn.prepareStatement(findSyncJobSql)){

            statement.setString(1, sourcePlaylistId);
            statement.setString(2, destinationPlaylistId);
            statement.setString(3, direction);

            try (ResultSet result = statement.executeQuery()) {

                return result.next()
                        ? OptionalLong.of(result.getLong("id"))
                        : OptionalLong.empty();
            }

        }catch (SQLException e){
            throw new SyncStateStorageException("Erro ao buscar trabalho de sincronização", e);
        }
    }

    @Override
    public long getOrCreateSyncJob(String sourcePlaylistId, String destinationPlaylistId, String direction){
        OptionalLong existing = findSyncJob(sourcePlaylistId, destinationPlaylistId, direction);

        if(existing.isPresent()){
            return existing.getAsLong();
        }

        try {
            return createSyncJob(sourcePlaylistId, destinationPlaylistId, direction);
        }catch (SyncStateStorageException e){
            if (!isDuplicatedKey(e)){
                throw e;
            }

            return findSyncJob(sourcePlaylistId, destinationPlaylistId, direction)
                    .orElseThrow(() -> e);
        }
    }

    private boolean isDuplicatedKey(SyncStateStorageException exception){
        return exception.getCause() instanceof SQLException sqlException
                && "23505".equals(sqlException.getSQLState());
    }

    @Override
    public long startSyncRun(long syncJobId){
        try(Connection conn = getConnection();
            PreparedStatement pstmt = conn.prepareStatement(startSyncRunSql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setLong(1, syncJobId);
            pstmt.executeUpdate();

            try(ResultSet keys = pstmt.getGeneratedKeys()) {
                if (keys.next()){
                    return keys.getLong(1);
                }

                throw new SQLException("Nenhum Id gerado para o sync_run");
            }

        }catch (SQLException e){
            throw new SyncStateStorageException("Erro ao iniciar execução de sincronização", e);
        }
    }

    @Override
    public void finishSyncRun(long syncRunId,
                              SyncRunStatus status){

        Objects.requireNonNull(status, "O status da execução não pode ser nulo");

        if (status == SyncRunStatus.RUNNING){
            throw new IllegalArgumentException("Para finalizar, informe SUCCESS ou FAILED");
        }

        try(Connection conn = getConnection();
            PreparedStatement pstmt = conn.prepareStatement(finishSyncRunSql)) {

            pstmt.setString(1, status.name());
            pstmt.setLong(2, syncRunId);

            int updatedRows = pstmt.executeUpdate();

            if (updatedRows == 0){
                throw new IllegalStateException(
                        "Execução inexistente ou já finalizada: " + syncRunId
                );
            }

        }catch (SQLException e){
            throw new SyncStateStorageException("Erro ao finalizar execução de sincronização: ", e);
        }
    }

    @Override
    public List<SyncJob> findSyncJobsBySource(String sourcePlaylistId, String direction){

        List<SyncJob> jobs = new ArrayList<>();

        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(findSyncJobsBySourceSql)){
            statement.setString(1, sourcePlaylistId);
            statement.setString(2, direction);

            try (ResultSet result = statement.executeQuery()){
                while (result.next()){
                    jobs.add(new SyncJob(
                            result.getLong("id"),
                            result.getString("source_playlist_id"),
                            result.getString("destination_playlist_id"),
                            result.getString("direction")));
                }
            }

            return List.copyOf(jobs);

        }catch (SQLException e){
            throw new SyncStateStorageException(
                    "Erro ao consultar os destinos vinculados à playlist.", e);
        }
    }

    @Override
    public List<SyncRunHistory> findSyncRunHistory(int limit, int offset){
        if (limit <= 0 || offset < 0){
            throw new IllegalArgumentException(
                    "Informe um limite positivo e um deslocamento maior ou igual a zero.");
        }

        List<SyncRunHistory> history = new ArrayList<>();

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(findSyncRunHistorySql)) {

            statement.setInt(1, limit);
            statement.setInt(2, offset);

            try (ResultSet result = statement.executeQuery()){
                while (result.next()){

                    history.add(new SyncRunHistory(
                            result.getLong("execution_id"),
                            result.getLong("sync_job_id"),
                            result.getString("source_playlist_id"),
                            result.getString("destination_playlist_id"),
                            result.getString("direction"),
                            result.getString("status"),
                            result.getObject("started_at", java.time.LocalDateTime.class),
                            result.getObject("completed_at", java.time.LocalDateTime.class)));
                }
            }

            return List.copyOf(history);

        }catch (SQLException exception){
            throw new SyncStateStorageException(
                    "Não foi possível consultar o histórico das execuções.", exception);
        }
    }

    @Override
    public long countSyncRunHistory() {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(countSyncRunHistorySql);
             ResultSet result = statement.executeQuery()) {
            result.next();

            return result.getLong(1);

        } catch (SQLException exception) {
            throw new SyncStateStorageException("Não foi possível contar as execuções do histórico.", exception);
        }
    }
}
