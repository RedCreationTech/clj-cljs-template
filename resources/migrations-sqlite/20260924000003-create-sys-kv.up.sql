-- 带过期时间的短期键值(验证码、登录失败计数与锁定、续期宽限),多实例共享。见 infra.kv。
CREATE TABLE sys_kv (
  k         VARCHAR(191) NOT NULL PRIMARY KEY,
  v         TEXT,
  expire_at INTEGER      NOT NULL
);
--;;
CREATE INDEX idx_sys_kv_expire_at ON sys_kv(expire_at);
