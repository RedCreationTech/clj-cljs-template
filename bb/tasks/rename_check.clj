(ns tasks.rename-check
  "改名纯规则回归:不改写文件,不创建平台工程。"
  (:require [tasks.util :as u]))

(defn- ensure! [condition message]
  (when-not condition (throw (ex-info message {}))))

(defn- rewrite [old-name new-name text]
  (require 'tasks.rename)
  (let [ns-path (ns-resolve 'tasks.rename 'ns->path)
        old-ns (str "com.acme." old-name) new-ns (str "com.acme." new-name)
        replacer (ns-resolve 'tasks.rename 'replacer)]
    ((replacer {:old-ns old-ns :new-ns new-ns :old-path (ns-path old-ns)
                :new-path (ns-path new-ns) :old-name old-name :new-name new-name}) text)))

(defn- mobile-paths! []
  (ensure! (= "(ns field-kit.brand) [field-kit.main]"
              (rewrite "sample" "field-kit" "(ns sample.brand) [sample.main]"))
           "ClojureDart 命名空间保留项目名连字符")
  (ensure! (= "mobile/src/field_kit/brand.cljd mobile/test/field_kit/events_test.cljd src/field_kit/api.cljd"
              (rewrite "sample" "field-kit" "mobile/src/sample/brand.cljd mobile/test/sample/events_test.cljd src/sample/api.cljd"))
           "ClojureDart 源码和测试路径必须将连字符转成下划线")
  (ensure! (= "name: field_kit_mobile\nexport cljd-out/field-kit/main.dart"
              (rewrite "sample" "field-kit" "name: sample_mobile\nexport cljd-out/sample/main.dart"))
           "Flutter 包名用下划线,Dart生成路径保留ClojureDart命名空间")
  (ensure! (= "mobile/src/workbench/brand.cljd workbench.main workbench_mobile cljd-out/workbench/main.dart"
              (rewrite "field-kit" "workbench" "mobile/src/field_kit/brand.cljd field-kit.main field_kit_mobile cljd-out/field-kit/main.dart"))
           "二次从连字符项目改名必须同时更新源路径、命名空间和Dart入口"))

(defn- containing-old-name! []
  (ensure! (= "com.acme.sample-app.core com/acme/sample_app/core.clj sample-app.main sample_app_mobile"
              (rewrite "sample" "sample-app" "com.acme.sample.core com/acme/sample/core.clj sample.main sample_mobile"))
           "新项目名包含旧项目名时,不能重复替换新增的文本"))

(defn- generated-paths! []
  (let [skip? (ns-resolve 'tasks.rename 'skip?)]
    (doseq [path ["resources/public/mobile/index.html" "resources/public/mobile/main.dart.js"
                  "resources/public/mobile/assets/source.json" "mobile/build/web/main.dart.js"
                  "mobile/android/app/src/main/AndroidManifest.xml"]]
      (ensure! (skip? path) "Flutter生成产物不得参与改名"))
    (doseq [path ["mobile/src/sample/brand.cljd" "mobile/test/sample/events_test.cljd"
                  "mobile/lib/main.dart" "mobile/pubspec.yaml" "mobile/deps.edn"]]
      (ensure! (not (skip? path)) "受控移动源码与入口必须参与改名"))))

(defn- platform-generation! []
  (require 'tasks.mobile)
  (with-redefs [u/project (constantly {:name "field-kit"})]
    (ensure! (= "field_kit_mobile" ((ns-resolve 'tasks.mobile 'flutter-project-name)))
             "Flutter 平台工程包名必须将连字符转成下划线"))
  (doseq [[source expected] [["Verify that our counter starts at 0. MyApp Icons.add" 1]
                             ["custom widget test: MyApp Icons.add" 0]]]
    (let [deleted (atom 0)]
      (with-redefs-fn {(ns-resolve 'babashka.fs 'exists?) (constantly true)
                       #'clojure.core/slurp (constantly source)
                       (ns-resolve 'babashka.fs 'delete) (fn [_] (swap! deleted inc))}
        #((ns-resolve 'tasks.mobile 'drop-scaffold-test!)))
      (ensure! (= expected @deleted) "只删除生成计数器示例,必须保留自定义同名测试"))))

(defn check! []
  (mobile-paths!)
  (containing-old-name!)
  (generated-paths!)
  (platform-generation!)
  (u/info "改名纯规则回归通过(未改写文件)"))
