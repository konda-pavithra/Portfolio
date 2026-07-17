-- Executed automatically by the postgres image on first container start
-- (mounted into /docker-entrypoint-initdb.d). Each service owns one schema.
CREATE SCHEMA IF NOT EXISTS user_service;
CREATE SCHEMA IF NOT EXISTS portfolio_service;
CREATE SCHEMA IF NOT EXISTS threshold_service;
