SELECT COUNT(*)
FROM sync_run run
         JOIN sync_job job ON job.id = run.sync_job_id;