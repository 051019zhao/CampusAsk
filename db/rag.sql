-- Additive, idempotent schema; application also initializes these tables.

CREATE TABLE IF NOT EXISTS rag_document (
  id VARCHAR(64) PRIMARY KEY, owner VARCHAR(128) NOT NULL, session_id VARCHAR(100) NOT NULL,
  title VARCHAR(255) NOT NULL, fingerprint VARCHAR(64) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX rag_owner_session(owner,session_id));

CREATE TABLE IF NOT EXISTS rag_chunk (
  id VARCHAR(64) PRIMARY KEY, document_id VARCHAR(64) NOT NULL, ordinal INT NOT NULL,
  content TEXT NOT NULL, embedding MEDIUMTEXT NOT NULL, model VARCHAR(512) NOT NULL,
  INDEX rag_document_chunks(document_id),
  FOREIGN KEY (document_id) REFERENCES rag_document(id) ON DELETE CASCADE);

CREATE TABLE IF NOT EXISTS rag_turn (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, owner VARCHAR(128) NOT NULL,
  session_id VARCHAR(100) NOT NULL, question TEXT NOT NULL, answer TEXT NOT NULL,
  embedding MEDIUMTEXT NOT NULL, model VARCHAR(512) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX rag_turn_owner(owner,session_id,id));

CREATE TABLE IF NOT EXISTS rag_summary (
  owner VARCHAR(128) NOT NULL, session_id VARCHAR(100) NOT NULL,
  content TEXT NOT NULL, PRIMARY KEY(owner,session_id));
