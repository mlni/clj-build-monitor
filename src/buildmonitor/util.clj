(ns buildmonitor.util)

(defn ->id
  "Calculate a brief hash from string for use as an id"
  [string]
  (str (.hashCode string)))
