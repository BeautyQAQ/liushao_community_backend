ALTER TABLE bms_post ADD COLUMN likes INT NOT NULL DEFAULT 0;
ALTER TABLE bms_post ADD COLUMN moderation_status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED';
ALTER TABLE bms_post ADD COLUMN moderation_reason VARCHAR(255);
ALTER TABLE bms_post ADD CONSTRAINT ck_post_moderation CHECK (moderation_status IN ('PUBLISHED','PENDING','HIDDEN'));
CREATE INDEX idx_post_moderation ON bms_post(moderation_status,create_time,id);

CREATE TABLE bms_comment (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, post_id INT NOT NULL, user_id INT NOT NULL,
 parent_id BIGINT, content VARCHAR(2000) NOT NULL, visible BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(post_id) REFERENCES bms_post(id) ON DELETE CASCADE,
 FOREIGN KEY(user_id) REFERENCES ums_user(id) ON DELETE CASCADE,
 FOREIGN KEY(parent_id) REFERENCES bms_comment(id) ON DELETE CASCADE
);
CREATE INDEX idx_comment_post ON bms_comment(post_id,created_at,id);
CREATE TABLE bms_post_like (
 post_id INT NOT NULL, user_id INT NOT NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(post_id,user_id),
 FOREIGN KEY(post_id) REFERENCES bms_post(id) ON DELETE CASCADE,
 FOREIGN KEY(user_id) REFERENCES ums_user(id) ON DELETE CASCADE
);
CREATE TABLE bms_bookmark (
 post_id INT NOT NULL, user_id INT NOT NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(post_id,user_id),
 FOREIGN KEY(post_id) REFERENCES bms_post(id) ON DELETE CASCADE,
 FOREIGN KEY(user_id) REFERENCES ums_user(id) ON DELETE CASCADE
);
CREATE INDEX idx_bookmark_user ON bms_bookmark(user_id,created_at,post_id);
CREATE TABLE ums_follow (
 follower_id INT NOT NULL, followed_id INT NOT NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(follower_id,followed_id), CHECK (follower_id <> followed_id),
 FOREIGN KEY(follower_id) REFERENCES ums_user(id) ON DELETE CASCADE,
 FOREIGN KEY(followed_id) REFERENCES ums_user(id) ON DELETE CASCADE
);
CREATE INDEX idx_follow_target ON ums_follow(followed_id,created_at,follower_id);
CREATE TABLE ums_notification (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, recipient_id INT NOT NULL, actor_id INT,
 type VARCHAR(24) NOT NULL, post_id INT, comment_id BIGINT, content VARCHAR(255) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), read_at TIMESTAMP(6),
 FOREIGN KEY(recipient_id) REFERENCES ums_user(id) ON DELETE CASCADE,
 FOREIGN KEY(actor_id) REFERENCES ums_user(id) ON DELETE CASCADE,
 FOREIGN KEY(post_id) REFERENCES bms_post(id) ON DELETE CASCADE,
 FOREIGN KEY(comment_id) REFERENCES bms_comment(id) ON DELETE CASCADE
);
CREATE INDEX idx_notification_recipient ON ums_notification(recipient_id,created_at,id);
CREATE TABLE ums_message (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, sender_id INT NOT NULL, recipient_id INT NOT NULL,
 content VARCHAR(2000) NOT NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), read_at TIMESTAMP(6),
 CHECK (sender_id <> recipient_id),
 FOREIGN KEY(sender_id) REFERENCES ums_user(id) ON DELETE CASCADE,
 FOREIGN KEY(recipient_id) REFERENCES ums_user(id) ON DELETE CASCADE
);
CREATE INDEX idx_message_pair ON ums_message(sender_id,recipient_id,id);
CREATE INDEX idx_message_inbox ON ums_message(recipient_id,read_at,id);
CREATE TABLE community_audit (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, actor VARCHAR(64) NOT NULL, action VARCHAR(64) NOT NULL,
 resource VARCHAR(32) NOT NULL, resource_id BIGINT NOT NULL, detail VARCHAR(1000) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
CREATE INDEX idx_audit_created ON community_audit(created_at,id);
