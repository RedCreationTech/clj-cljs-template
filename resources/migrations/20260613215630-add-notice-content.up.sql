-- 通知公告正文（与 SQLite 迁移 20260613215630 同步）
ALTER TABLE sys_notice ADD COLUMN notice_content LONGTEXT NULL;
