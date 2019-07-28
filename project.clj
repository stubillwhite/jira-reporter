(defproject jira-reporter "0.1.4-SNAPSHOT"

  :description "TODO"

  :url "TODO"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :repl-options {:port 4555}

  :plugins []

  :main jira-reporter.app

  :dependencies [[org.clojure/tools.nrepl "0.2.13"]
                 [org.clojure/clojure "1.10.0"]
                 [com.taoensso/timbre "4.10.0"]
                 [org.clojure/tools.trace "0.7.10"]
                 [com.rpl/specter "1.1.2"]
                 [metasoarous/oz "1.6.0-alpha3"]
                 [mount "0.1.16"]
                 [clj-http "3.9.1"]
                 [org.clojure/data.json "0.2.6"]
                 [com.rpl/specter "1.1.2"]
                 [org.clojure/tools.cli "0.4.1"]
                 [com.taoensso/nippy "2.14.0"]]

  :profiles {:uberjar {:aot :all}

             :dev {:dependencies   [[org.clojure/tools.namespace "0.2.10"]]
                   :resource-paths ["test-resources"]
                   :source-paths   ["dev"]}})
