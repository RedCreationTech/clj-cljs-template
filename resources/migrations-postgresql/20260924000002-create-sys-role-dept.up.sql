-- 角色的自定义数据范围(data_scope = '2' 时生效):角色可以看到哪些部门的数据。见 domain.system.data-scope。
CREATE TABLE sys_role_dept (
  role_id BIGINT NOT NULL,
  dept_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, dept_id)
);
