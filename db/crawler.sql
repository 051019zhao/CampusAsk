-- Additive, idempotent schema; application also initializes these tables.

CREATE TABLE IF NOT EXISTS crawl_candidate (
  id VARCHAR(64) PRIMARY KEY, url VARCHAR(1024) NOT NULL, title VARCHAR(255) NOT NULL,
  content MEDIUMTEXT NOT NULL, fingerprint VARCHAR(64) NOT NULL,
  status VARCHAR(20) NOT NULL, knowledge_id BIGINT NULL,
  fetched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  reviewed_at TIMESTAMP NULL, reviewed_by BIGINT NULL,
  INDEX crawl_review(status,fetched_at));

CREATE TABLE IF NOT EXISTS crawl_run (
  id VARCHAR(36) PRIMARY KEY, status VARCHAR(20) NOT NULL,
  started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, finished_at TIMESTAMP NULL,
  visited INT NOT NULL DEFAULT 0, changed_count INT NOT NULL DEFAULT 0,
  failed INT NOT NULL DEFAULT 0, detail VARCHAR(1000) NOT NULL DEFAULT '');

CREATE TABLE IF NOT EXISTS crawl_lease (
  id INT PRIMARY KEY, holder VARCHAR(36) NOT NULL, expires_at TIMESTAMP NOT NULL);
