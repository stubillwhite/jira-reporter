(defproject jira-reporter "0.1.3-SNAPSHOT"

  :description "TODO"

  :url "TODO"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :repl-options {:port 4555}

  :plugins []

  :main jira-reporter.app

  :dependencies [[org.clojure/tools.nrepl "0.2.13"]
                 [org.clojure/clojure "1.9.0"]
                 [com.taoensso/timbre "4.10.0"]
                 [org.clojure/tools.trace "0.7.9"]
                 [com.rpl/specter "1.1.1"]
                 [mount "0.1.12"]
                 [clj-http "3.9.0"]
                 [org.clojure/data.json "0.2.6"]
                 [com.rpl/specter "1.1.1"]
                 [org.clojure/tools.cli "0.3.7"]]

  :profiles {:uberjar {:aot :all}

             :dev {:dependencies   [[org.clojure/tools.namespace "0.2.10"]]
                   :resource-paths ["test-resources"]
                   :source-paths   ["dev"]}})
