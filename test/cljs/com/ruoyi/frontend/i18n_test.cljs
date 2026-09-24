(ns com.ruoyi.frontend.i18n-test
  "界面多语言:译文查找、回退与占位参数。"
  (:require
   [cljs.test :refer [deftest is testing use-fixtures]]
   [com.ruoyi.frontend.i18n :as i18n]))

(use-fixtures :each {:after #(i18n/set-locale! i18n/default-locale)})

(deftest tr-test
  (testing "默认中文:原文返回"
    (i18n/set-locale! :zh-CN)
    (is (= "用户管理" (i18n/tr "用户管理")))
    (is (= "共 3 条" (i18n/tr "共 {0} 条" 3))))
  (testing "英文:有译文用译文,没有译文退回中文"
    (i18n/set-locale! :en-US)
    (is (= "Users" (i18n/tr "用户管理")))
    (is (= "Total 3" (i18n/tr "共 {0} 条" 3)))
    (is (= "还没翻译的文案" (i18n/tr "还没翻译的文案")))))

(deftest set-locale-test
  (is (= :en-US (i18n/set-locale! :en-US)))
  (is (= :zh-CN (i18n/set-locale! :fr-FR)) "不支持的语言退回默认"))
