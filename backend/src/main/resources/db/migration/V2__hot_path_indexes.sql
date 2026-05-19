-- Indexes for current query paths.
--
-- Skipped redundancies:
--   * subscription(user_id): covered by uk_subscription_user_manga as a left prefix.
--   * notification_log(user_id): covered by idx_notification_log_user_manga_chapter.
-- Skipped uniqueness on (user_id, manga_id, chapter): the dispatcher's
-- find-then-upsert can in theory race; existing prod data has not been
-- audited for duplicates, so a non-unique index is the safe choice for now.

CREATE INDEX idx_manga_next_check_date
    ON manga (next_check_date);

CREATE INDEX idx_subscription_manga_id
    ON subscription (manga_id);

CREATE INDEX idx_notification_log_manga_id
    ON notification_log (manga_id);

CREATE INDEX idx_notification_log_status_attempts
    ON notification_log (status, attempts);

CREATE INDEX idx_notification_log_user_manga_chapter
    ON notification_log (user_id, manga_id, chapter);
