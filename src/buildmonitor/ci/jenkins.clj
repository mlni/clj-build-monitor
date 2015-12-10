(ns buildmonitor.ci.jenkins
  (:require [clj-time.core :as t]
            [clj-time.coerce :as c]
            [buildmonitor.http :as http]
            [buildmonitor.util :as u]))

(defn- parse-status [build]
  (if (or (:building build) (nil? (:result build)))
    :running
    (get {"FAILURE"   :failed
          "SUCCESS"   :success
          "UNSTABLE"  :success
          "NOT_BUILD" :running
          "ABORTED"   :canceled} (:result build) :canceled)))

(defn- parse-start-time [build]
  (t/in-seconds (t/interval (c/from-long (:timestamp build)) (t/now))))

(defn- simplify-jenkins-build [build history build-config]
  {:id            (u/->id (str (:fullDisplayName build)))
   :name          (or (:title build-config)
                      (:name build))
   :number        (:number build)
   :status        (parse-status build)
   :seconds-since (parse-start-time build)
   :history       (map (fn [b]
                         {:id     (u/->id (str (:fullDisplayName b)))
                          :number (:number b)
                          :status (parse-status b)})
                       history)})

(defn- fetch-build [client build-config]
  (let [job-details (client (str "/job/" (:id build-config) "/api/json"))
        build-details (map (fn [build]
                             (client (str "/job/" (:id build-config) "/" (:number build) "/api/json")))
                           (take 10 (:builds job-details)))
        build (assoc (first build-details) :name (:displayName job-details))]
    (simplify-jenkins-build build (rest build-details) build-config)))

(defn fetch-service [service]
  (let [client (http/json-http-client (:url service)
                                     :username (:username service)
                                     :password (:apikey service))]
    (map #(fetch-build client %) (:builds service))))