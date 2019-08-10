(ns jira-reporter.app-test
  (:require [clojure.test :refer :all]
            [jira-reporter.app :as app]))

(deftest validate-args
  (testing "burndown"
    (testing "given valid options then passes validation"
      (is (= {:options {:burndown true :board-name "board" :sprint-name "sprint"} :ok? true}
             (app/validate-args ["--burndown" "--board-name" "board" "--sprint-name" "sprint"]))))
    (testing "given invalid options then fails validation"
      (is (= {:exit-message "Option burndown requires the following options to also be specified: board-name, sprint-name" :ok? false}
             (app/validate-args ["--burndown"])))))
  (testing "daily report"
    (testing "given valid options then passes validation"
      (is (= {:options {:daily-report true :board-name "board" :sprint-name "sprint"} :ok? true}
             (app/validate-args ["--daily-report" "--board-name" "board" "--sprint-name" "sprint"]))))
    (testing "given invalid options then fails validation"
      (is (= {:exit-message "Option daily-report requires the following options to also be specified: board-name, sprint-name" :ok? false}
             (app/validate-args ["--daily-report"])))))
  (testing "sprint report"
    (testing "given valid options then passes validation"
      (is (= {:options {:sprint-report true :board-name "board" :sprint-name "sprint"} :ok? true}
             (app/validate-args ["--sprint-report" "--board-name" "board" "--sprint-name" "sprint"]))))
    (testing "given invalid options then fails validation"
      (is (= {:exit-message "Option sprint-report requires the following options to also be specified: board-name, sprint-name" :ok? false}
             (app/validate-args ["--sprint-report"])))))
  (testing "list sprints"
    (testing "given valid options then passes validation"
      (is (= {:options {:list-sprints true :board-name "board"} :ok? true}
             (app/validate-args ["--list-sprints" "--board-name" "board"]))))))
