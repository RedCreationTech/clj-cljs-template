INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
VALUES (15, '代码生成', 2, 5, 'gen', 'tool/gen/index', 'C', '0', '0', 'tool:gen:list', 'code');
--;;
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, menu_type, visible, status, perms, icon) VALUES
(1038, '预览代码', 15, 1, 'F', '0', '0', 'tool:gen:preview', '#'),
(1039, '生成代码', 15, 2, 'F', '0', '0', 'tool:gen:code', '#');
--;;
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (1, 15), (1, 1038), (1, 1039);
--;;
-- Keep generated IDs above explicit seeds and the current sequence position.
SELECT setval(pg_get_serial_sequence('sys_menu', 'menu_id'),
  GREATEST(COALESCE(MAX(menu_id), 1), (SELECT last_value FROM sys_menu_menu_id_seq)), true)
FROM sys_menu;
