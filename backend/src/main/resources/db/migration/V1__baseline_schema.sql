-- Baseline schema. Mirrors the JPA entities so that ddl-auto=validate is happy.
-- On an existing Railway database that pre-dates Flyway, this migration is
-- recorded as already-applied via spring.flyway.baseline-on-migrate=true.

CREATE TABLE manga (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    mangadex_id VARCHAR(255),
    cover_url VARCHAR(255),
    latest_chapter VARCHAR(255),
    last_read_chapter VARCHAR(255),
    next_check_date DATE,
    no_source BIT NOT NULL,
    update_day_of_week VARCHAR(255),
    PRIMARY KEY (id),
    CONSTRAINT uk_manga_title UNIQUE (title)
);

CREATE TABLE app_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    phone_number VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_app_user_phone_number UNIQUE (phone_number)
);

CREATE TABLE subscription (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    manga_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_subscription_user_manga UNIQUE (user_id, manga_id)
);

CREATE TABLE notification_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    manga_id BIGINT NOT NULL,
    chapter VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    attempts INT NOT NULL,
    last_error VARCHAR(1000),
    created_at DATETIME(6) NOT NULL,
    last_attempt_at DATETIME(6),
    PRIMARY KEY (id)
);
