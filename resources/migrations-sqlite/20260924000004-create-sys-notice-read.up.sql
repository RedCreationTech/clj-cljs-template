-- 通知已读记录(顶部铃铛的未读数按用户计算)。见 controllers.system.notice/latest-notices。
CREATE TABLE sys_notice_read (
  user_id   INTEGER NOT NULL,
  notice_id INTEGER NOT NULL,
  read_time TEXT,
  PRIMARY KEY (user_id, notice_id)
);
