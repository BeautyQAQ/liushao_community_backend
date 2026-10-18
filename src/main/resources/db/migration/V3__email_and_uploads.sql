CREATE TABLE community_email_code (
 user_id INT NOT NULL, purpose VARCHAR(16) NOT NULL, code_hash VARCHAR(64) NOT NULL,
 expires_at TIMESTAMP(6) NOT NULL, created_at TIMESTAMP(6) NOT NULL,
 attempts INT NOT NULL DEFAULT 0,
 PRIMARY KEY (user_id,purpose),
 FOREIGN KEY(user_id) REFERENCES ums_user(id) ON DELETE CASCADE
);
CREATE TABLE community_upload (
 filename VARCHAR(64) PRIMARY KEY, user_id INT NOT NULL, size_bytes BIGINT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(user_id) REFERENCES ums_user(id)
);
