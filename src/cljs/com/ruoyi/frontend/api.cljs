(ns com.ruoyi.frontend.api
  "HTTP API 客户端封装。各领域实现拆到 api.<domain> 子命名空间,此处按原有公共名重导出,
   使既有 api/... 调用点保持不变。"
  (:require
   [com.ruoyi.frontend.api.auth :as auth]
   [com.ruoyi.frontend.api.configs :as configs]
   [com.ruoyi.frontend.api.depts :as depts]
   [com.ruoyi.frontend.api.dicts :as dicts]
   [com.ruoyi.frontend.api.file :as file]
   [com.ruoyi.frontend.api.form-template :as formtemplate]
   [com.ruoyi.frontend.api.gen :as gen]
   [com.ruoyi.frontend.api.impexp :as impexp]
   [com.ruoyi.frontend.api.jobs :as jobs]
   [com.ruoyi.frontend.api.logs :as logs]
   [com.ruoyi.frontend.api.menus :as menus]
   [com.ruoyi.frontend.api.monitor :as monitor]
   [com.ruoyi.frontend.api.notices :as notices]
   [com.ruoyi.frontend.api.posts :as posts]
   [com.ruoyi.frontend.api.profile :as profile]
   [com.ruoyi.frontend.api.roles :as roles]
   [com.ruoyi.frontend.api.users :as users]))

(def login auth/login)

(def get-info auth/get-info)

(def logout auth/logout)

(def list-configs configs/list-configs)

(def create-config configs/create-config)

(def update-config configs/update-config)

(def delete-config configs/delete-config)

(def list-depts depts/list-depts)

(def create-dept depts/create-dept)

(def update-dept depts/update-dept)

(def delete-dept depts/delete-dept)

(def change-dept-status depts/change-dept-status)

(def list-dict-types dicts/list-dict-types)

(def list-dict-data dicts/list-dict-data)

(def create-dict-type dicts/create-dict-type)

(def update-dict-type dicts/update-dict-type)

(def delete-dict-type dicts/delete-dict-type)

(def create-dict-data dicts/create-dict-data)

(def update-dict-data dicts/update-dict-data)

(def delete-dict-data dicts/delete-dict-data)

(def file-list file/file-list)

(def file-upload file/file-upload)

(def file-download file/file-download)

(def file-delete file/file-delete)

(def list-form-templates formtemplate/list-form-templates)

(def get-form-template formtemplate/get-form-template)

(def save-form-template formtemplate/save-form-template)

(def update-form-template formtemplate/update-form-template)

(def delete-form-template formtemplate/delete-form-template)

(def gen-tables gen/gen-tables)

(def gen-columns gen/gen-columns)

(def gen-preview gen/gen-preview)

(def gen-generate gen/gen-generate)

(def gen-download gen/gen-download)

(def gen-deploy gen/gen-deploy)

(def export-users-csv impexp/export-users-csv)

(def import-users-csv impexp/import-users-csv)

(def export-generic-csv impexp/export-generic-csv)

(def export-roles impexp/export-roles)

(def export-menus impexp/export-menus)

(def export-depts impexp/export-depts)

(def export-posts impexp/export-posts)

(def export-dicts impexp/export-dicts)

(def export-configs impexp/export-configs)

(def export-operlogs impexp/export-operlogs)

(def export-loginlogs impexp/export-loginlogs)

(def list-jobs jobs/list-jobs)

(def list-job-logs jobs/list-job-logs)

(def create-job jobs/create-job)

(def update-job jobs/update-job)

(def delete-job jobs/delete-job)

(def run-job-once jobs/run-job-once)

(def list-oper-logs logs/list-oper-logs)

(def list-login-logs logs/list-login-logs)

(def clear-oper-logs logs/clear-oper-logs)

(def delete-oper-logs logs/delete-oper-logs)

(def clear-login-logs logs/clear-login-logs)

(def delete-login-logs logs/delete-login-logs)

(def list-menus menus/list-menus)

(def create-menu menus/create-menu)

(def update-menu menus/update-menu)

(def delete-menu menus/delete-menu)

(def change-menu-status menus/change-menu-status)

(def menu-tree menus/menu-tree)

(def list-online-users monitor/list-online-users)

(def get-datasource monitor/get-datasource)

(def get-server-info monitor/get-server-info)

(def get-dashboard-stats monitor/get-dashboard-stats)

(def get-integrant-info monitor/get-integrant-info)

(def set-integrant-trace monitor/set-integrant-trace)

(def get-integrant-trace-logs monitor/get-integrant-trace-logs)

(def get-cache-info monitor/get-cache-info)

(def get-cache-keys monitor/get-cache-keys)

(def clear-cache monitor/clear-cache)

(def get-cache-names monitor/get-cache-names)

(def get-cache-keys-by-name monitor/get-cache-keys-by-name)

(def get-cache-value monitor/get-cache-value)

(def clear-cache-name monitor/clear-cache-name)

(def clear-cache-key monitor/clear-cache-key)

(def force-logout monitor/force-logout)

(def list-notices notices/list-notices)

(def create-notice notices/create-notice)

(def update-notice notices/update-notice)

(def delete-notice notices/delete-notice)

(def list-posts posts/list-posts)

(def create-post posts/create-post)

(def update-post posts/update-post)

(def delete-post posts/delete-post)

(def change-post-status posts/change-post-status)

(def get-profile profile/get-profile)

(def update-profile profile/update-profile)

(def change-password profile/change-password)

(def upload-avatar profile/upload-avatar)

(def list-roles roles/list-roles)

(def create-role roles/create-role)

(def update-role roles/update-role)

(def delete-role roles/delete-role)

(def change-role-status roles/change-role-status)

(def get-role-dept-tree roles/get-role-dept-tree)

(def set-role-data-scope roles/set-role-data-scope)

(def list-role-allocated-users roles/list-role-allocated-users)

(def list-role-unallocated-users roles/list-role-unallocated-users)

(def cancel-role-auth-user roles/cancel-role-auth-user)

(def cancel-role-auth-user-all roles/cancel-role-auth-user-all)

(def select-role-auth-user-all roles/select-role-auth-user-all)

(def get-role roles/get-role)

(def list-users users/list-users)

(def get-user users/get-user)

(def create-user users/create-user)

(def update-user users/update-user)

(def delete-user users/delete-user)

(def change-user-status users/change-user-status)

(def reset-user-password users/reset-user-password)

(def get-user-roles users/get-user-roles)

(def update-user-roles users/update-user-roles)

(def export-users users/export-users)
