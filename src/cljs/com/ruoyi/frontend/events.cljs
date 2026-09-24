(ns com.ruoyi.frontend.events
  "re-frame 事件处理器（按领域拆分为多个子命名空间，此处仅聚合加载副作用）。"
  (:require
   [com.ruoyi.frontend.events.auth]
   [com.ruoyi.frontend.events.common]
   [com.ruoyi.frontend.events.configs]
   [com.ruoyi.frontend.events.core]
   [com.ruoyi.frontend.events.depts]
   [com.ruoyi.frontend.events.dicts]
   [com.ruoyi.frontend.events.feedback]
   [com.ruoyi.frontend.events.formbuilder]
   [com.ruoyi.frontend.events.jobs]
   [com.ruoyi.frontend.events.logs]
   [com.ruoyi.frontend.events.menus]
   [com.ruoyi.frontend.events.monitor]
   [com.ruoyi.frontend.events.notices]
   [com.ruoyi.frontend.events.posts]
   [com.ruoyi.frontend.events.profile]
   [com.ruoyi.frontend.events.roles]
   [com.ruoyi.frontend.events.theme]
   [com.ruoyi.frontend.events.users]))
