(ns com.ruoyi.web.routes.system
  "系统管理路由聚合。"
  (:require
   [com.ruoyi.web.controllers.system.config :as config]
   [com.ruoyi.web.controllers.system.dept :as dept]
   [com.ruoyi.web.controllers.system.dict :as dict]
   [com.ruoyi.web.controllers.system.file :as file]
   [com.ruoyi.web.controllers.system.form-template :as form-template]
   [com.ruoyi.web.controllers.system.import-export :as im]
   [com.ruoyi.web.controllers.system.menu :as menu]
   [com.ruoyi.web.controllers.system.notice :as notice]
   [com.ruoyi.web.controllers.system.post :as post]
   [com.ruoyi.web.controllers.system.profile :as profile]
   [com.ruoyi.web.controllers.system.role :as role]
   [com.ruoyi.web.controllers.system.user :as user]))

;; ── Shared Swagger schemas ──────────────────────────────────────────
(def PagingQuery [:map {:closed true}
                  [:page {:optional true} :int] [:size {:optional true} :int]
                  [:order_by {:optional true} :string] [:is_asc {:optional true} :string]])
(def RoleUserQuery [:map {:closed true}
                    [:role_id :int]
                    [:user_name {:optional true} :string]
                    [:phonenumber {:optional true} :string]
                    [:page {:optional true} :int] [:size {:optional true} :int]
                    [:order_by {:optional true} :string] [:is_asc {:optional true} :string]])

(def PathId [:map [:id [:re #"\d+"]]])

;; 用户表单里的角色/岗位/部门选择器要读这些列表:有用户新增或修改权限也可以读(满足任一即可)。
(def ^:private role-pickers ["system:role:list" "system:user:add" "system:user:edit"])
(def ^:private post-pickers ["system:post:list" "system:user:add" "system:user:edit"])
(def ^:private dept-pickers ["system:dept:list" "system:user:list"])

;; ── Routes ──────────────────────────────────────────────────────────
;; 每个路由组是一个返回 [route ...] 向量的函数,system-routes 把它们拼到 "/system" 之下。
;; 没写 :perms 的接口(个人中心、选项/树形下拉)登录即可访问;监控类接口在 routes.monitor。
;; 新增业务模块时优先新建 routes/<module>.clj,而不是继续往这里加。

(defn- user-routes
  "用户管理路由。"
  [{:keys [user-service dept-service]}]
  [["/user"
    ["" {:get  {:perms "system:user:list" :summary    "用户列表"
                :description "分页查询用户列表（支持搜索、数据权限过滤）"
                :parameters {:query PagingQuery}

                :handler    (partial user/list-users {:user-service user-service})}
         :post {:perms "system:user:add" :summary    "新增用户"
                :description "创建新用户（含角色分配）"
                :handler    (partial user/create-user {:user-service user-service})}}]
    ["/export" {:get {:perms "system:user:export" :summary "导出用户" :description "导出用户数据为CSV文件"
                      :handler (partial im/export-users {:user-service user-service})}}]
    ["/importTemplate" {:get {:perms "system:user:import" :summary "下载导入模板" :description "下载CSV导入模板文件"
                              :handler (partial im/import-template {})}}]
    ["/deptTree" {:get {:perms "system:user:list" :summary "部门树" :description "获取部门树（用于选择）"
                        :handler (partial dept/dept-tree {:dept-service dept-service})}}]
    ["/import" {:post {:perms "system:user:import" :summary "导入用户" :description "从CSV文件批量导入用户"
                       :handler (partial im/import-users {:user-service user-service})}}]
    ["/:id/authRole" {:get {:perms "system:user:query" :summary "用户已分配角色"
                            :handler (partial user/auth-role {:user-service user-service})}
                      :put {:perms "system:user:edit" :summary "分配角色" :description "分配用户角色"
                            :handler (partial user/update-auth-role {:user-service user-service})}}]
    ["/:id" {:get    {:perms "system:user:query" :summary "用户详情" :parameters {:path PathId}
                      :handler (partial user/get-user {:user-service user-service})}
             :put    {:perms "system:user:edit" :summary "更新用户" :parameters {:path PathId}
                      :handler (partial user/update-user {:user-service user-service})}
             :delete {:perms "system:user:remove" :summary "删除用户" :parameters {:path PathId}
                      :handler (partial user/delete-user {:user-service user-service})}}]
    ["/:id/status/:status" {:put {:perms "system:user:edit" :summary "修改用户状态"
                                  :handler (partial user/change-status {:user-service user-service})}}]
    ["/:id/resetPwd"       {:put {:perms "system:user:resetPwd" :summary "重置用户密码"
                                  :handler (partial user/reset-password {:user-service user-service})}}]]])

(defn- role-routes
  "角色管理路由。"
  [{:keys [user-service role-service dept-service]}]
  [["/role"
    ["" {:get  {:perms role-pickers :summary "角色列表" :description "分页查询角色列表"
                :handler (partial role/list-roles {:role-service role-service})}
         :post {:perms "system:role:add" :summary "新增角色" :handler (partial role/create-role {:role-service role-service})}}]
    ["/export" {:get {:perms "system:role:export" :summary "导出角色" :description "导出角色数据为CSV文件"
                      :handler (partial im/export-roles {:role-service role-service})}}]
    ["/optionselect" {:get {:summary "角色选项"
                            :handler (partial role/option-select {:role-service role-service})}}]
    ["/authUser/allocatedList" {:get {:perms "system:role:list" :summary "角色已分配用户" :parameters {:query RoleUserQuery}
                                      :handler (partial role/allocated-list {:role-service role-service :user-service user-service})}}]
    ["/authUser/unallocatedList" {:get {:perms "system:role:list" :summary "角色未分配用户" :parameters {:query RoleUserQuery}
                                        :handler (partial role/unallocated-list {:role-service role-service :user-service user-service})}}]
    ["/authUser/cancel" {:put {:perms "system:role:edit" :summary "取消用户角色"
                               :parameters {:body [:map [:role_id :int] [:user_id :int]]}
                               :handler (partial role/cancel-auth-user {:role-service role-service})}}]
    ["/authUser/cancelAll" {:put {:perms "system:role:edit" :summary "批量取消角色"
                                  :parameters {:query [:map [:role_id :int] [:user_ids :string]]}
                                  :handler (partial role/cancel-auth-user-all {:role-service role-service})}}]
    ["/authUser/selectAll" {:put {:perms "system:role:edit" :summary "批量授权角色"
                                  :parameters {:query [:map [:role_id :int] [:user_ids :string]]}
                                  :handler (partial role/select-auth-user-all {:role-service role-service})}}]
    ["/deptTree/:id" {:get {:perms "system:role:query" :summary "角色部门树"
                            :handler (partial role/dept-tree-by-role {:role-service role-service})}}]
    ["/dataScope" {:put {:perms "system:role:edit" :summary "数据权限分配"
                         :parameters {:body [:map [:role_id :int] [:data_scope :string] [:dept_ids {:optional true} :string]]}
                         :handler (partial role/data-scope {:role-service role-service})}}]
    ["/:id" {:get    {:perms "system:role:query" :summary "角色详情" :parameters {:path PathId}
                      :handler (partial role/get-role {:role-service role-service})}
             :put    {:perms "system:role:edit" :summary "更新角色" :parameters {:path PathId}
                      :handler (partial role/update-role {:role-service role-service})}
             :delete {:perms "system:role:remove" :summary "删除角色" :parameters {:path PathId}
                      :handler (partial role/delete-role {:role-service role-service})}}]]])

(defn- menu-routes
  "菜单管理路由。"
  [{:keys [menu-service]}]
  [["/menu"
    ["" {:get  {:perms "system:menu:list" :summary "菜单列表（树形）" :description "查询所有菜单（树形结构）"
                :handler (partial menu/list-menus {:menu-service menu-service})}
         :post {:perms "system:menu:add" :summary "新增菜单" :handler (partial menu/create-menu {:menu-service menu-service})}}]
    ["/export" {:get {:perms "system:menu:export" :summary "导出菜单" :description "导出菜单数据为CSV文件"
                      :handler (partial im/export-menus {:menu-service menu-service})}}]
    ["/treeselect" {:get {:summary "菜单树选项" :description "获取菜单树（用于角色权限选择）"
                          :handler (partial menu/menu-tree {:menu-service menu-service})}}]
    ["/:id" {:get    {:perms "system:menu:query" :summary "菜单详情" :parameters {:path PathId}
                      :handler (partial menu/get-menu {:menu-service menu-service})}
             :put    {:perms "system:menu:edit" :summary "更新菜单" :parameters {:path PathId}
                      :handler (partial menu/update-menu {:menu-service menu-service})}
             :delete {:perms "system:menu:remove" :summary "删除菜单" :parameters {:path PathId}
                      :handler (partial menu/delete-menu {:menu-service menu-service})}}]]])

(defn- dict-type-routes
  "字典类型路由。"
  [{:keys [dict-service]}]
  [["/dict/type"
    ["" {:get  {:perms "system:dict:list" :summary "字典类型列表" :parameters {:query PagingQuery}
                :handler (partial dict/list-dict-types {:dict-service dict-service})}
         :post {:perms "system:dict:add" :summary "新增字典类型" :handler (partial dict/create-dict-type {:dict-service dict-service})}}]
    ["/export" {:get {:perms "system:dict:export" :summary "导出字典类型" :description "导出字典类型数据为CSV文件"
                      :handler (partial im/export-dict-types {:dict-service dict-service})}}]
    ["/:id" {:get    {:perms "system:dict:query" :summary "字典类型详情" :parameters {:path PathId}
                      :handler (partial dict/get-dict-type {:dict-service dict-service})}
             :put    {:perms "system:dict:edit" :summary "更新字典类型" :parameters {:path PathId}
                      :handler (partial dict/update-dict-type {:dict-service dict-service})}
             :delete {:perms "system:dict:remove" :summary "删除字典类型" :parameters {:path PathId}
                      :handler (partial dict/delete-dict-type {:dict-service dict-service})}}]
    ["/optionselect" {:get {:summary "字典类型选项" :handler (partial dict/option-select {:dict-service dict-service})}}]
    ["/refreshCache" {:delete {:perms "system:dict:remove" :summary "刷新字典缓存" :handler (partial dict/refresh-cache {})}}]]])

(defn- dict-data-routes
  "字典数据路由。"
  [{:keys [dict-service]}]
  [["/dict/data"
    ["" {:get  {:perms "system:dict:list" :summary "字典数据列表" :parameters {:query [:map {:closed true}
                                                                                 [:page {:optional true} :int] [:size {:optional true} :int]
                                                                                 [:order_by {:optional true} :string] [:is_asc {:optional true} :string]
                                                                                 [:dict_type {:optional true} :string]]}
                :handler (partial dict/list-dict-data {:dict-service dict-service})}
         :post {:perms "system:dict:add" :summary "新增字典数据" :handler (partial dict/create-dict-data {:dict-service dict-service})}}]
    ["/export" {:get {:perms "system:dict:export" :summary "导出字典数据" :description "导出字典数据为CSV文件"
                      :handler (partial im/export-dict-data {:dict-service dict-service})}}]
    ["/:id" {:get    {:perms "system:dict:query" :summary "字典数据详情" :parameters {:path PathId}
                      :handler (partial dict/get-dict-data {:dict-service dict-service})}
             :put    {:perms "system:dict:edit" :summary "更新字典数据" :parameters {:path PathId}
                      :handler (partial dict/update-dict-data {:dict-service dict-service})}
             :delete {:perms "system:dict:remove" :summary "删除字典数据" :parameters {:path PathId}
                      :handler (partial dict/delete-dict-data {:dict-service dict-service})}}]]])

(defn- dept-routes
  "部门管理路由。"
  [{:keys [dept-service]}]
  [["/dept"
    ["" {:get  {:perms dept-pickers :summary "部门列表（树形）" :description "查询所有部门树"
                :handler (partial dept/list-depts {:dept-service dept-service})}
         :post {:perms "system:dept:add" :summary "新增部门" :handler (partial dept/create-dept {:dept-service dept-service})}}]
    ["/export" {:get {:perms "system:dept:export" :summary "导出部门" :description "导出部门数据为CSV文件"
                      :handler (partial im/export-depts {:dept-service dept-service})}}]
    ["/tree" {:get {:summary "部门树选项" :description "获取部门树（用于选择）"
                    :handler (partial dept/dept-tree {:dept-service dept-service})}}]
    ["/:id" {:get    {:perms "system:dept:query" :summary "部门详情" :parameters {:path PathId}
                      :handler (partial dept/get-dept {:dept-service dept-service})}
             :put    {:perms "system:dept:edit" :summary "更新部门" :parameters {:path PathId}
                      :handler (partial dept/update-dept {:dept-service dept-service})}
             :delete {:perms "system:dept:remove" :summary "删除部门" :parameters {:path PathId}
                      :handler (partial dept/delete-dept {:dept-service dept-service})}}]]])

(defn- post-routes
  "岗位管理路由。"
  [{:keys [post-service]}]
  [["/post"
    ["" {:get  {:perms post-pickers :summary "岗位列表" :parameters {:query PagingQuery}
                :handler (partial post/list-posts {:post-service post-service})}
         :post {:perms "system:post:add" :summary "新增岗位" :handler (partial post/create-post {:post-service post-service})}}]
    ["/export" {:get {:perms "system:post:export" :summary "导出岗位" :description "导出岗位数据为CSV文件"
                      :handler (partial im/export-posts {:post-service post-service})}}]
    ["/:id" {:get    {:perms "system:post:query" :summary "岗位详情" :parameters {:path PathId}
                      :handler (partial post/get-post {:post-service post-service})}
             :put    {:perms "system:post:edit" :summary "更新岗位" :parameters {:path PathId}
                      :handler (partial post/update-post {:post-service post-service})}
             :delete {:perms "system:post:remove" :summary "删除岗位" :parameters {:path PathId}
                      :handler (partial post/delete-post {:post-service post-service})}}]]])

(defn- config-routes
  "参数配置路由。"
  [{:keys [config-service]}]
  [["/config"
    ["" {:get  {:perms "system:config:list" :summary "参数配置列表" :parameters {:query PagingQuery}
                :handler (partial config/list-configs {:config-service config-service})}
         :post {:perms "system:config:add" :summary "新增参数配置" :handler (partial config/create-config {:config-service config-service})}}]
    ["/export" {:get {:perms "system:config:export" :summary "导出参数" :description "导出参数配置数据为CSV文件"
                      :handler (partial im/export-configs {:config-service config-service})}}]
    ["/:id" {:get    {:perms "system:config:query" :summary "参数详情" :parameters {:path PathId}
                      :handler (partial config/get-config {:config-service config-service})}
             :put    {:perms "system:config:edit" :summary "更新参数" :parameters {:path PathId}
                      :handler (partial config/update-config {:config-service config-service})}
             :delete {:perms "system:config:remove" :summary "删除参数" :parameters {:path PathId}
                      :handler (partial config/delete-config {:config-service config-service})}}]]])

(defn- form-template-routes
  "表单模板路由。"
  [{:keys [form-template-service]}]
  [["/form-template"
    ["" {:get  {:perms "tool:build:list" :summary "表单模板列表" :parameters {:query PagingQuery}
                :handler (partial form-template/list-form-templates {:form-template-service form-template-service})}
         :post {:perms "tool:build:add" :summary "新增表单模板" :handler (partial form-template/create-form-template {:form-template-service form-template-service})}}]
    ["/:id" {:get    {:perms "tool:build:list" :summary "表单模板详情" :parameters {:path PathId}
                      :handler (partial form-template/get-form-template {:form-template-service form-template-service})}
             :put    {:perms "tool:build:edit" :summary "更新表单模板" :parameters {:path PathId}
                      :handler (partial form-template/update-form-template {:form-template-service form-template-service})}
             :delete {:perms "tool:build:remove" :summary "删除表单模板" :parameters {:path PathId}
                      :handler (partial form-template/delete-form-template {:form-template-service form-template-service})}}]]])

(defn- profile-routes
  "个人中心路由。"
  [{:keys [user-service upload-config]}]
  [["/profile"
    ["" {:get {:summary "个人信息" :description "获取当前登录用户信息"
               :handler (partial profile/get-profile {:user-service user-service})}
         :put {:summary "更新个人信息" :description "更新昵称/手机/邮箱/性别"
               :handler (partial profile/update-profile {:user-service user-service})}}]
    ["/password" {:put {:summary "修改密码" :description "修改当前用户登录密码"
                        :handler (partial profile/change-password {:user-service user-service})}}]
    ["/avatar"   {:post {:summary "上传头像" :description "上传用户头像文件"
                         :handler (partial profile/upload-avatar {:user-service user-service
                                                                  :upload-config upload-config})}}]]])

(defn- notice-routes
  "通知公告路由。"
  [{:keys [query-fn]}]
  [["/notice"
    ["" {:get  {:perms "system:notice:list" :summary "通知公告列表" :parameters {:query PagingQuery}
                :handler (partial notice/list-notices {:query-fn query-fn})}
         :post {:perms "system:notice:add" :summary "新增通知公告" :handler (partial notice/create-notice {:query-fn query-fn})}}]
    ;; 顶部铃铛:登录即可(放在 /:id 之前)
    ["/latest" {:get {:summary "最新通知与未读数" :handler (partial notice/latest-notices {:query-fn query-fn})}}]
    ["/read-all" {:put {:summary "全部标记为已读" :handler (partial notice/read-all-notices {:query-fn query-fn})}}]
    ["/:id" {:get    {:perms "system:notice:query" :summary "通知公告详情" :parameters {:path PathId}
                      :handler (partial notice/get-notice {:query-fn query-fn})}
             :put    {:perms "system:notice:edit" :summary "更新通知公告" :parameters {:path PathId}
                      :handler (partial notice/update-notice {:query-fn query-fn})}
             :delete {:perms "system:notice:remove" :summary "删除通知公告" :parameters {:path PathId}
                      :handler (partial notice/delete-notice {:query-fn query-fn})}}]]])

(defn- file-routes
  "文件管理路由。"
  [{:keys [upload-config]}]
  (let [cfg {:upload-config upload-config}]
    [["/file"
      ["" {:get  {:perms "system:file:list" :summary "文件列表" :description "查询上传文件列表"
                  :handler (partial file/list-files cfg)}
           :post {:perms "system:file:upload" :summary "上传文件"
                  :description "上传文件到服务器(类型、大小与目录见 :upload-config)"
                  :handler (partial file/upload-file cfg)}}]
      ["/:filename" {:get    {:perms "system:file:download" :summary "下载文件"
                              :description "下载指定文件"
                              :handler (partial file/download-file cfg)}
                     :delete {:perms "system:file:remove" :summary "删除文件"
                              :description "删除指定文件"
                              :handler (partial file/delete-file cfg)}}]]]))

(defn system-routes
  "系统管理路由聚合:整组要求登录(:auth? true),各接口用 :perms 声明按钮权限(见 web.middleware.auth)。"
  [opts]
  (into ["/system"
         {:auth? true
          :swagger {:tags ["系统管理"]}}]
        (mapcat #(% opts)
                [user-routes
                 role-routes
                 menu-routes
                 dict-type-routes
                 dict-data-routes
                 dept-routes
                 post-routes
                 config-routes
                 form-template-routes
                 profile-routes
                 notice-routes
                 file-routes])))
