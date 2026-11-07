SELECT
    run.id AS execution_id,
    job.id AS sync_job_id,
    job.source_playlist_id,
    job.destination_playlist_id,
    job.direction,
    run.status,
    run.started_at,
    run.completed_at
FROM sync_run run
         JOIN sync_job job ON job.id = run.sync_job_id
ORDER BY run.started_at DESC NULLS LAST, run.id DESC
    LIMIT ? OFFSET ?;