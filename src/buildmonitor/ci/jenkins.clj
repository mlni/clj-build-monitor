(ns buildmonitor.ci.jenkins
  (:require [clojure.data.json :as json]
            [org.httpkit.client :as http]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [clojure.tools.logging :as log]))

(defn- ->id [string]
  (str (.hashCode string)))

(defn- parse-status [build]
  (if (or (:building build) (nil? (:result build)))
    :running
    (get {"FAILURE"   :failed
          "SUCCESS"   :success
          "UNSTABLE"  :success
          "NOT_BUILD" :running
          "ABORTED"   :canceled} (:result build) :canceled)))

(defn- parse-start-time [build]
  (t/in-secs (t/interval (c/from-long (:timestamp build)) (t/now))))

(defn- simplify-jenkins-build [build history build-config]
  {:id            (->id (str (:fullDisplayName build)))
   :name          (or (:title build-config)
                      (:name build))
   :number        (:number build)
   :status        (parse-status build)
   :seconds-since (parse-start-time build)
   :history       (map (fn [b]
                         {:id     (->id (str (:fullDisplayName b)))
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

(defn- make-client [service]
  (let [base-url (:url service)
        options {:basic-auth [(:username service)
                              (:apikey service)]
                 :headers    {"Accept" "application/json"}
                 :as         :text}]
    (fn [href]
      (let [url (str base-url href)]
        (log/info "Fetching " url)
        (let [promise (http/get url options)
              response @promise]
          (json/read-str (:body response) :key-fn keyword))))))

(defn fetch-service [service]
  (let [client (make-client service)]
    (map #(fetch-build client %) (:builds service))))