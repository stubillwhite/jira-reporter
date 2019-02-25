(ns jira-reporter.utils)

(defmacro def-
  ([name & decls]
    (list* `def (with-meta name (assoc (meta name) :private true)) decls)))

(defmacro defmulti-
  [name & decls]
  (list* `defmulti (with-meta name (assoc (meta name) :private true)) decls))

(defmacro defmethod-
  [name & decls]
  (list* `defmethod (with-meta name (assoc (meta name) :private true)) decls))

(defn map-vals
  "Return the result of applying f to the values of the map kv."
  [f kv]
  (into {} (for [[k v] kv] [k (f v)])))

