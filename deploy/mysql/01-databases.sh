#!/usr/bin/env bash
# Sourced by the official MySQL entrypoint on an empty data volume only.
# NO_BACKSLASH_ESCAPES and doubled quotes keep environment values SQL literals.
admin_password_sql=${ADMIN_DB_PASSWORD//\'/\'\'}
community_password_sql=${COMMUNITY_DB_PASSWORD//\'/\'\'}
docker_process_sql <<SQL
SET SESSION sql_mode = 'NO_BACKSLASH_ESCAPES';
CREATE DATABASE IF NOT EXISTS liushao_community CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'admin_app'@'%' IDENTIFIED BY '${admin_password_sql}';
CREATE USER 'community_app'@'%' IDENTIFIED BY '${community_password_sql}';
GRANT ALL PRIVILEGES ON liushao_community.* TO 'admin_app'@'%';
GRANT ALL PRIVILEGES ON liushao_community.* TO 'community_app'@'%';
SQL
unset admin_password_sql community_password_sql
