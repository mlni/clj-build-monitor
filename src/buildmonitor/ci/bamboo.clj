(ns buildmonitor.ci.bamboo
  (:require [clj-time.core :as t]
            [clj-time.coerce :as c]
            [buildmonitor.http :as http]
            [buildmonitor.util :as u]
            [clj-time.format :as tf]))

(defn- parse-state [state]
  (get {"Successful" :success
        "Failed"     :failed} state :canceled))

(defn- parse-start-time [timestamp]
  ; 2019-07-12T17:00:17.275+03:00
  (let [start-time (tf/parse (tf/formatter "yyyy-MM-dd'T'HH:mm:ss.SSSZ") timestamp)
        now (if (t/after? start-time (t/now)) start-time (t/now))]
    (t/in-seconds (t/interval start-time now))))

(defn- fetch-build-history [client build-id]
  (let [build-history (client (str "/rest/api/latest/result/" build-id ".json?os_authType=basic&expand=results.result"))]
    (map (fn [result]
           {:id            (:buildResultKey result)
            :number        (:buildNumber result)
            :status        (parse-state (:state result))
            :seconds-since (parse-start-time (:buildStartedTime result))})
         (get-in build-history [:results :result]))))

(defn- map-by-key [coll key]
  (reduce (fn [result item] (assoc result (get item key) item))
          {} coll))

(defn- fetch-plan-details [client build-config]
  (if (:title build-config)
    {:id   (:id build-config)
     :name (:title build-config)}
    (let [response (client (str "/rest/api/latest/plan/" (:id build-config) ".json?os_authType=basic"))]
      {:id   (:id build-config)
       :name (:name response)})))

(defn- fetch-current-builds [client build-configs]
  (let [configured-builds-by-id (map-by-key build-configs :id)
        response (client "/build/admin/ajax/getDashboardSummary.action")
        known-builds (filter (fn [build] (and (contains? configured-builds-by-id (:planKey build))
                                              (= "BUILDING" (:status build))
                                              (not (:isBranch build))))
                             (:builds response))]
    (reduce (fn [result build]
              (assoc result (:planKey build)                ; assuming one build per plan
                            {:id     (:resultKey build)
                             :number (:buildNumber build)
                             :status :running}))
            {} known-builds)))

(defn- fetch-build [client build-config current-build]
  (let [build-id (:id build-config)
        details (fetch-plan-details client build-config)
        history (fetch-build-history client build-id)
        all-builds (sort-by :number > (filter identity (conj history current-build)))
        last-build (first all-builds)]
    (assoc details
      :status (get last-build :status :unknown)
      :number (get last-build :number 0)
      :seconds-since (get last-build :seconds-since 0)
      :history (rest all-builds))))

(defn fetch-service [service]
  (let [client (http/json-http-client (:url service)
                                      :username (:username service)
                                      :password (:password service))
        all-current-builds (fetch-current-builds client (:builds service))]
    (map #(fetch-build client %1 (get all-current-builds (:id %1))) (:builds service))))
