(ns jira-reporter.utils)

(defmacro def-
  ([name & decls]
    (list* `def  (vary-meta name assoc :private true) decls)))

(defmacro defmulti-
  [name & decls]
  (list* `defmulti (vary-meta name assoc :private true) decls))

(defmacro defmethod-
  [name & decls]
  (list* `defmethod (vary-meta name assoc :private true) decls))

(defn map-vals
  "Return the result of applying f to the values of the map kv."
  [f kv]
  (into {} (for [[k v] kv] [k (f v)])))

(def any-pred
  "Takes a set of predicates and returns a function f that returns true
  if any of its composing predicates return a logical true value
  against all of its arguments, else it returns false."
  some-fn)
