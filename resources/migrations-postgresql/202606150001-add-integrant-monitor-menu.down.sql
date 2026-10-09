DELETE FROM sys_role_menu WHERE menu_id = 21;
--;;
DELETE FROM sys_menu WHERE menu_id = 21;
--;;
UPDATE sys_menu
SET order_num = 10,
    update_time = to_char(CURRENT_TIMESTAMP, 'YYYY-MM-DD HH24:MI:SS')
WHERE menu_id = 20;
