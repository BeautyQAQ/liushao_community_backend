CREATE TABLE legacy_community_id_map (
 resource VARCHAR(32) NOT NULL,
 legacy_id VARCHAR(64) NOT NULL,
 current_id BIGINT NOT NULL,
 PRIMARY KEY (resource,legacy_id),
 UNIQUE (resource,current_id)
);
