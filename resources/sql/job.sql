-- :name list-jobs :? :*
-- :doc 定时任务列表(分页),给管理界面用
SELECT * FROM sys_job WHERE 1=1
  AND (CAST(:job_name AS CHAR) IS NULL OR INSTR(job_name, :job_name) > 0)
  AND (CAST(:job_group AS CHAR) IS NULL OR job_group = :job_group)
  AND (CAST(:status AS CHAR) IS NULL OR status = :status)
ORDER BY job_id
LIMIT :page_size OFFSET :offset

-- :name count-jobs :? :1
-- :doc 统计定时任务数量(与 list-jobs 同样的筛选条件)
SELECT COUNT(*) AS total FROM sys_job WHERE 1=1
  AND (CAST(:job_name AS CHAR) IS NULL OR INSTR(job_name, :job_name) > 0)
  AND (CAST(:job_group AS CHAR) IS NULL OR job_group = :job_group)
  AND (CAST(:status AS CHAR) IS NULL OR status = :status)

-- :name all-jobs :? :*
-- :doc 全部定时任务,不分页:调度器启动时加载、仪表盘统计用
SELECT * FROM sys_job ORDER BY job_id

-- :name find-job-by-id :? :1
SELECT * FROM sys_job WHERE job_id = :job_id

-- :name create-job! :! :n
INSERT INTO sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
VALUES (:job_name, :job_group, :invoke_target, :cron_expression, :misfire_policy, :concurrent, :status, :create_by, :now, :remark)

-- :name last-insert-job-id :? :1
-- :doc 获取最后插入的任务ID (SQLite)
SELECT last_insert_rowid() AS job_id

-- :name last-insert-job-id-mysql :? :1
-- :doc 获取最后插入的任务ID (MySQL)
SELECT LAST_INSERT_ID() AS job_id

-- :name update-job! :! :n
UPDATE sys_job
SET job_name = COALESCE(:job_name, job_name),
    job_group = COALESCE(:job_group, job_group),
    invoke_target = COALESCE(:invoke_target, invoke_target),
    cron_expression = COALESCE(:cron_expression, cron_expression),
    misfire_policy = COALESCE(:misfire_policy, misfire_policy),
    concurrent = COALESCE(:concurrent, concurrent),
    status = COALESCE(:status, status),
    update_by = :update_by,
    update_time = :now,
    remark = COALESCE(:remark, remark)
WHERE job_id = :job_id

-- :name delete-job! :! :n
DELETE FROM sys_job WHERE job_id = :job_id

-- :name list-job-logs :? :*
SELECT * FROM sys_job_log WHERE 1=1
  AND (CAST(:job_name AS CHAR) IS NULL OR INSTR(job_name, :job_name) > 0)
  AND (CAST(:job_group AS CHAR) IS NULL OR job_group = :job_group)
  AND (CAST(:status AS CHAR) IS NULL OR status = :status)
ORDER BY job_log_id DESC
LIMIT :page_size OFFSET :offset

-- :name count-job-logs :? :1
SELECT COUNT(*) AS total FROM sys_job_log WHERE 1=1
  AND (CAST(:job_name AS CHAR) IS NULL OR INSTR(job_name, :job_name) > 0)
  AND (CAST(:job_group AS CHAR) IS NULL OR job_group = :job_group)
  AND (CAST(:status AS CHAR) IS NULL OR status = :status)

-- :name create-job-log! :! :n
INSERT INTO sys_job_log (job_name, job_group, invoke_target, job_message, status, exception_info, create_time)
VALUES (:job_name, :job_group, :invoke_target, :job_message, :status, :exception_info, :now)

-- :name clear-job-logs! :! :n
DELETE FROM sys_job_log WHERE 1=1
  AND (CAST(:job_name AS CHAR) IS NULL OR job_name = :job_name)
  AND (CAST(:job_group AS CHAR) IS NULL OR job_group = :job_group)

-- :name last-insert-job-id-postgresql :? :1
SELECT LASTVAL() AS job_id
