-- Additive migration for existing installations.
CREATE TABLE IF NOT EXISTS notification (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id VARCHAR(50) NOT NULL,
  type VARCHAR(30) NOT NULL,
  content TEXT NOT NULL,
  post_id BIGINT NULL,
  is_read TINYINT(1) NOT NULL DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX notification_owner(user_id,is_read,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
