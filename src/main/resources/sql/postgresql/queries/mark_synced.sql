INSERT INTO synced_track (
    sync_job_id, source_track_id, source_title, source_artist,
    destination_track_id, match_confidence, status, synced_at
)
VALUES (?, ?, ?, ?, ?, ?, ?, ?)
ON CONFLICT (sync_job_id, source_track_id)
DO UPDATE SET
    source_title = EXCLUDED.source_title,
    source_artist = EXCLUDED.source_artist,
    destination_track_id = EXCLUDED.destination_track_id,
    match_confidence = EXCLUDED.match_confidence,
    status = EXCLUDED.status,
    synced_at = EXCLUDED.synced_at;