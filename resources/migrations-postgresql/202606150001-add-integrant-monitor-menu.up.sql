UPDATE sys_menu
SET order_num = 11,
    update_time = to_char(CURRENT_TIMESTAMP, 'YYYY-MM-DD HH24:MI:SS')
WHERE menu_id = 20;
--;;
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon)
VALUES (21, 'Integrant 依赖', 2, 10, 'integrant', 'monitor/integrant/index', 'C', '0', '0', 'monitor:integrant:list', 'FunctionOutlined') ON CONFLICT DO NOTHING;
--;;
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (1, 21) ON CONFLICT DO NOTHING;
--;;
-- Keep generated IDs above explicit seeds and the current sequence position.
SELECT setval(pg_get_serial_sequence('sys_menu', 'menu_id'),
  GREATEST(COALESCE(MAX(menu_id), 1), (SELECT last_value FROM sys_menu_menu_id_seq)), true)
FROM sys_menu;
