(ns buildmonitor.main
  (:require [cljs.core.async :refer [chan <! >! put! close!]]
            [reagent.core :as r]
            [buildmonitor.socket :as ws])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

(defonce connected (r/atom false))
(defonce builds (r/atom {:builds [] :last-refresh (js/Date.)}))

(defn log [& msgs]
  (.log js/console (apply str msgs)))

(log "Starting up")

(def connection-channel (chan))
(def read-channel (chan))

(ws/open-socket "/ws" connection-channel read-channel)
(go-loop []
         (let [msg (<! read-channel)]
           (when msg
             (swap! builds assoc :builds (.parse js/JSON msg) :last-refresh (js/Date.))
             (recur))))

(go-loop []
         (let [state (<! connection-channel)]
           (when state
             (reset! connected (:connected state))
             (recur))))

(defn- render-time [seconds]
  (letfn [(select-unit [units]
            (or (first (filter (fn [[us _]] (pos? (quot seconds us))) units))
                (last units)))]
    (let [units [[(* 365 24 60 60) "y"]
                 [(* 7 24 60 60) "w"]
                 [(* 24 60 60) "d"]
                 [(* 60 60) "h"]
                 [60 "m"]
                 [1 "s"]]
          [unit-seconds unit] (select-unit units)]
      (str (quot seconds unit-seconds) unit))))

(defn- render-status [status]
  (condp = status "success" "*"
                  "failed" "!"
                  "?"))

(defn render-build [build]
  [:li.build {:className (aget build "status")}
   (when (= "running" (aget build "status"))
     [:div.progress [:div.spinner]])
   [:div.title (aget build "name")]
   [:div.meta
    [:span.time (render-time (aget build "seconds-since"))]
    [:ol.history
     (for [h (aget build "history")]
       ^{:key (aget h "id")} [:li (render-status (aget h "status"))])]]])

(defn- connection-status [connected]
  (when-not connected
    [:div.connection
     [:div.overlay-background]
     [:div.overlay-message
      "Disconnected"]]))

(defn- footer []
  (let [time-str (.toISOString (get @builds :last-refresh))]
    [:div.footer time-str]))

(defn build-results []
  [:div
   [:ol.builds
    (for [build (get @builds :builds)]
      ^{:key (aget build "id")}
      [render-build build])]
   [footer]
   [connection-status @connected]])

(r/render [build-results]
          (js/document.getElementById "app"))

(log "started up")
