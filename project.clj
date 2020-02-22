(defproject elchache/fonda "0.0.3"
  :url "https://github.com/arichiardi/fonda"
  :description "An async pipeline approach to functional core - imperative shell."
  :license {:name "Apache License"
            :url  "http://www.apache.org/licenses/LICENSE-2.0"}
  :source-paths ["src"]
  :resource-paths []
  :compile-path nil
  :target-path nil
  :plugins [[lein-tools-deps "0.4.3"]]
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :lein-tools-deps/config {:config-files [:project]}
  :profiles {:dev {:test-paths             ["test"]
                   :lein-tools-deps/config {:config-files [:install :user :project]
                                            :aliases      [:dev :test]}}})