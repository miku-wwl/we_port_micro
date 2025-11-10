-- 先强制删除旧表（不管是否存在），避免结构残留
DROP TABLE IF EXISTS security;

-- schema.sql：R2DBC启动时创建表结构
CREATE TABLE IF NOT EXISTS security (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticker VARCHAR(50) UNIQUE NOT NULL,
    security_type VARCHAR(20) NOT NULL CHECK (security_type IN ('STOCK', 'CALL', 'PUT')),
    strike_price DECIMAL(18,2),
    maturity_date DATE,
    underlying_ticker VARCHAR(50)
);

-- 清空表数据（避免重复初始化）
DELETE FROM security;

-- security 表中的 6 条记录（2 只股票 + 4 个期权），本质是6 个独立的 “金融产品” 的静态定义
-- 1. 股票类（修正字段名：securityType→security_type）
INSERT INTO security (ticker, security_type, underlying_ticker)
VALUES
    ('AAPL', 'STOCK', 'AAPL'),
    ('TELSA', 'STOCK', 'TELSA');

-- 2. 期权类（修正字段名：securityType→security_type、strikePrice→strike_price、maturityDate→maturity_date）
INSERT INTO security (ticker, security_type, strike_price, maturity_date, underlying_ticker)
VALUES
('AAPL-MAY-2026-110-C', 'CALL', 110.00, '2026-05-31', 'AAPL'),
('AAPL-MAY-2026-110-P', 'PUT', 110.00, '2026-05-31', 'AAPL'),
('TELSA-JUN-2026-400-C', 'CALL', 400.00, '2026-06-30', 'TELSA'),
('TELSA-JUL-2026-400-P', 'PUT', 400.00, '2026-07-31', 'TELSA');