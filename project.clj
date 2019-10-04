(defproject jira-reporter "0.1.6-SNAPSHOT"

  :description "A simple script to pull information from JIRA"

  :url "https://github.com/stubillwhite/jira-reporter.git"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :repl-options {:port 4555}

  :plugins []

  :main jira-reporter.app

  :dependencies [;; Core
                 [org.clojure/tools.nrepl "0.2.13"]
                 [org.clojure/clojure "1.10.0"]
                 [org.clojure/tools.trace "0.7.10"]

                 ;; Logging
                 [com.taoensso/timbre "4.10.0"]
                 
                 ;; Spec helpers
                 [expound "0.7.2"]

                 ;; Graphing
                 [metasoarous/oz "1.6.0-alpha3"]

                 ;; DI
                 [mount "0.1.16"]

                 ;; HTTP and JSON
                 [clj-http "3.9.1"]
                 [org.clojure/data.json "0.2.6"]

                 ;; Data manipulation
                 [com.rpl/specter "1.1.2"]

                 ;; CLI
                 [org.clojure/tools.cli "0.4.1"]
                 
                 ;; Persistence
                 [com.taoensso/nippy "2.14.0"]]

  :profiles {:uberjar {:aot :all}

             :dev {:dependencies   [[org.clojure/tools.namespace "0.2.10"]]
                   :resource-paths ["test-resources"]
                   :source-paths   ["dev"]}})
