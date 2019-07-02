(ns jira-reporter.cache
  (:require [jira-reporter.utils :refer [def-]]
            [clojure.java.io :as io]
            [mount.core :refer [defstate]]
            [taoensso.nippy :as nippy]
            [taoensso.timbre :as timbre])
  (:import [java.io DataInputStream DataOutputStream]))

(timbre/refer-timbre)

(defn- file-exists? [fnam]
  (.exists (io/as-file fnam)))

(defn- write-object [fnam obj]
  (with-open [w (io/output-stream fnam)]
    (nippy/freeze-to-out! (DataOutputStream. w) obj)))

(defn- read-object [fnam]
  (with-open [r (io/input-stream fnam)]
    (nippy/thaw-from-in! (DataInputStream. r))))

(defn- load-cache [path]
  (if (file-exists? path)
    (do
      (info "Loading existing cached data from" path)
      (read-object path))
    (do
      (info "Creating new cache file" path)
      {})))

(def- cache-filename "cached-data.edn")

(def- cache-atom (atom nil))

(defstate cache
  :start (reset-vals! cache-atom (load-cache cache-filename))
  :stop  (reset-vals! cache-atom nil))

(defn with-cache
  "Retrieve the value associated at ks from the cache, or else generate it by calling f and cache the result."
  [ks f]
  (if-let [v (get-in @cache-atom ks)]
    v
    (let [v (f)]
      (swap! cache-atom assoc-in ks v)
      (write-object cache-filename @cache-atom)
      v)))

