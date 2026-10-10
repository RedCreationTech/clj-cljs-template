-- 移除页面代码生成器(系统工具 → 代码生成)的菜单与按钮权限;新模块请用 bb new-module 生成。
DELETE FROM sys_role_menu WHERE menu_id IN (SELECT menu_id FROM sys_menu WHERE perms LIKE 'tool:gen:%');
--;;
DELETE FROM sys_menu WHERE perms LIKE 'tool:gen:%';
