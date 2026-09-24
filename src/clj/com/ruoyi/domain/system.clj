(ns com.ruoyi.domain.system
  "系统管理 Integrant 组件注册。"
  (:require
   [com.ruoyi.infra.kv :as kv]
   [com.ruoyi.infra.online :as online]
   [com.ruoyi.infra.scheduler :as scheduler]
   [integrant.core :as ig]))

(defmethod ig/init-key :app.system/user-service
  [_ {:keys [query-fn db]}]
  {:query-fn query-fn :db db})

(defmethod ig/init-key :app.system/role-service
  [_ {:keys [query-fn db]}]
  {:query-fn query-fn :db db})

(defmethod ig/init-key :app.system/menu-service
  [_ {:keys [query-fn db]}]
  {:query-fn query-fn :db db})

(defmethod ig/init-key :app.system/dept-service
  [_ {:keys [query-fn db]}]
  {:query-fn query-fn :db db})

(defmethod ig/init-key :app.system/post-service
  [_ {:keys [query-fn db]}]
  {:query-fn query-fn :db db})

(defmethod ig/init-key :app.system/dict-service
  [_ {:keys [query-fn db]}]
  {:query-fn query-fn :db db})

(defmethod ig/init-key :app.system/config-service
  [_ {:keys [query-fn db]}]
  {:query-fn query-fn :db db})

(defmethod ig/init-key :app.system/form-template-service
  [_ {:keys [query-fn db]}]
  {:query-fn query-fn :db db})

(defmethod ig/init-key :app.system/log-service
  [_ {:keys [query-fn db]}]
  {:query-fn query-fn :db db})

(defmethod ig/init-key :app.system/online-service
  [_ {:keys [query-fn]}]
  (online/set-query-fn! query-fn)
  ;; 验证码、登录限流、续期宽限改存数据库(sys_kv),多实例共享
  (kv/use-store! (kv/jdbc-store query-fn))
  {:list-online   (fn [params]
                    (apply online/list-online
                           (mapcat (fn [[k v]] [(keyword (name k)) v]) params)))
   :force-logout  (fn [token-id]
                    (online/force-logout! token-id))})

(defmethod ig/halt-key! :app.system/online-service
  [_ _]
  ;; 系统停止后连接池已关闭,切回内存实现(单元测试、REPL 里继续可用)
  (kv/use-store! (kv/memory-store)))

(defmethod ig/init-key :app.system/job-scheduler
  [_ {:keys [scheduler query-fn migrations]}]
  ;; 依赖 migrations 确保 sys_job 表已创建
  (scheduler/init! scheduler query-fn)
  {:scheduler scheduler})
