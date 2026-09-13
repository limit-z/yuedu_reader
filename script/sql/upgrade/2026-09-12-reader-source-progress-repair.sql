-- Repair historical terminal task-book progress from acknowledged chapter rows.
-- This is idempotent and does not alter chapter or snapshot content.
UPDATE reader_source_task_book
SET planned_chapter_count = GREATEST(planned_chapter_count, processed_chapter_count),
    remote_latest_chapter_no = GREATEST(remote_latest_chapter_no, local_latest_chapter_no),
    update_time = NOW()
WHERE status IN ('COMPLETED', 'WAITING_REVIEW')
  AND processed_chapter_count > 0
  AND (planned_chapter_count = 0 OR remote_latest_chapter_no = 0);

-- Queued books are eligible for a future run and must not retain a dead run id.
UPDATE reader_source_task_book
SET run_id = NULL,
    started_at = NULL,
    update_time = NOW()
WHERE status = 'QUEUED'
  AND run_id IS NOT NULL;
