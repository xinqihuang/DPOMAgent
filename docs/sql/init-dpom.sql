-- DPOMAgent MySQL 初始化（由 MySQL 管理员执行一次）
-- 用法：mysql -uroot -p < docs/sql/init-dpom.sql
CREATE DATABASE IF NOT EXISTS dpom_agent CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'dpom'@'localhost' IDENTIFIED BY 'dpom';
GRANT ALL PRIVILEGES ON dpom_agent.* TO 'dpom'@'localhost';
FLUSH PRIVILEGES;
