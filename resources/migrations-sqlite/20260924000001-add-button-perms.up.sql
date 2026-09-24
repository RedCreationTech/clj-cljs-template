-- 按钮级权限:补齐各模块的按钮菜单(F),新增「文件管理」菜单,全部授权给超级管理员角色。
-- 后端路由用 :perms 声明所需标识(web.middleware.auth),前端按 getInfo 的 permissions 显隐按钮。
-- 按钮菜单 ID 从 1000 开始,避开界面新建菜单的自增区间。
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
VALUES (22, '文件管理', 1, 9, 'file', 'system/file/index', 'C', '0', '0', 'system:file:list', 'FolderOpenOutlined');
--;;
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, menu_type, visible, status, perms, icon) VALUES
(1000, '用户导入', 3, 6, 'F', '0', '0', 'system:user:import', '#'),
(1001, '重置密码', 3, 7, 'F', '0', '0', 'system:user:resetPwd', '#'),
(1002, '菜单导出', 5, 5, 'F', '0', '0', 'system:menu:export', '#'),
(1003, '部门查询', 6, 1, 'F', '0', '0', 'system:dept:query', '#'),
(1004, '部门新增', 6, 2, 'F', '0', '0', 'system:dept:add', '#'),
(1005, '部门修改', 6, 3, 'F', '0', '0', 'system:dept:edit', '#'),
(1006, '部门删除', 6, 4, 'F', '0', '0', 'system:dept:remove', '#'),
(1007, '部门导出', 6, 5, 'F', '0', '0', 'system:dept:export', '#'),
(1008, '岗位查询', 7, 1, 'F', '0', '0', 'system:post:query', '#'),
(1009, '岗位新增', 7, 2, 'F', '0', '0', 'system:post:add', '#'),
(1010, '岗位修改', 7, 3, 'F', '0', '0', 'system:post:edit', '#'),
(1011, '岗位删除', 7, 4, 'F', '0', '0', 'system:post:remove', '#'),
(1012, '岗位导出', 7, 5, 'F', '0', '0', 'system:post:export', '#'),
(1013, '字典查询', 8, 1, 'F', '0', '0', 'system:dict:query', '#'),
(1014, '字典新增', 8, 2, 'F', '0', '0', 'system:dict:add', '#'),
(1015, '字典修改', 8, 3, 'F', '0', '0', 'system:dict:edit', '#'),
(1016, '字典删除', 8, 4, 'F', '0', '0', 'system:dict:remove', '#'),
(1017, '字典导出', 8, 5, 'F', '0', '0', 'system:dict:export', '#'),
(1018, '参数查询', 9, 1, 'F', '0', '0', 'system:config:query', '#'),
(1019, '参数新增', 9, 2, 'F', '0', '0', 'system:config:add', '#'),
(1020, '参数修改', 9, 3, 'F', '0', '0', 'system:config:edit', '#'),
(1021, '参数删除', 9, 4, 'F', '0', '0', 'system:config:remove', '#'),
(1022, '参数导出', 9, 5, 'F', '0', '0', 'system:config:export', '#'),
(1023, '公告查询', 10, 1, 'F', '0', '0', 'system:notice:query', '#'),
(1024, '公告新增', 10, 2, 'F', '0', '0', 'system:notice:add', '#'),
(1025, '公告修改', 10, 3, 'F', '0', '0', 'system:notice:edit', '#'),
(1026, '公告删除', 10, 4, 'F', '0', '0', 'system:notice:remove', '#'),
(1027, '操作删除', 11, 1, 'F', '0', '0', 'monitor:operlog:remove', '#'),
(1028, '日志导出', 11, 2, 'F', '0', '0', 'monitor:operlog:export', '#'),
(1029, '登录删除', 12, 1, 'F', '0', '0', 'monitor:logininfor:remove', '#'),
(1030, '日志导出', 12, 2, 'F', '0', '0', 'monitor:logininfor:export', '#'),
(1031, '账户解锁', 12, 3, 'F', '0', '0', 'monitor:logininfor:unlock', '#'),
(1032, '强退用户', 13, 1, 'F', '0', '0', 'monitor:online:forceLogout', '#'),
(1033, '任务查询', 14, 1, 'F', '0', '0', 'monitor:job:query', '#'),
(1034, '任务新增', 14, 2, 'F', '0', '0', 'monitor:job:add', '#'),
(1035, '任务修改', 14, 3, 'F', '0', '0', 'monitor:job:edit', '#'),
(1036, '任务删除', 14, 4, 'F', '0', '0', 'monitor:job:remove', '#'),
(1037, '状态修改', 14, 5, 'F', '0', '0', 'monitor:job:changeStatus', '#'),
(1038, '预览代码', 15, 1, 'F', '0', '0', 'tool:gen:preview', '#'),
(1039, '生成代码', 15, 2, 'F', '0', '0', 'tool:gen:code', '#'),
(1040, '清除缓存', 18, 1, 'F', '0', '0', 'monitor:cache:remove', '#'),
(1041, '表单新增', 20, 1, 'F', '0', '0', 'tool:build:add', '#'),
(1042, '表单修改', 20, 2, 'F', '0', '0', 'tool:build:edit', '#'),
(1043, '表单删除', 20, 3, 'F', '0', '0', 'tool:build:remove', '#'),
(1044, '函数追踪', 21, 1, 'F', '0', '0', 'monitor:integrant:trace', '#'),
(1045, '文件上传', 22, 1, 'F', '0', '0', 'system:file:upload', '#'),
(1046, '文件下载', 22, 2, 'F', '0', '0', 'system:file:download', '#'),
(1047, '文件删除', 22, 3, 'F', '0', '0', 'system:file:remove', '#');
--;;
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu WHERE menu_id = 22 OR menu_id BETWEEN 1000 AND 1047;
