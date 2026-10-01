CREATE TABLE sys_post (
  post_id INTEGER PRIMARY KEY,
  post_code VARCHAR(64) NOT NULL,
  post_name VARCHAR(50) NOT NULL,
  post_sort INT DEFAULT 0,
  status CHAR(1) DEFAULT '0',
  create_by VARCHAR(64) DEFAULT '',
  create_time TEXT DEFAULT (datetime('now','localtime')),
  update_by VARCHAR(64) DEFAULT '',
  update_time TEXT DEFAULT (datetime('now','localtime')),
  remark VARCHAR(500) DEFAULT ''
);
--;;
CREATE INDEX idx_sys_post_status ON sys_post(status);
