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


(defn render-build [build]
  [:li.build {:className (aget build "status")}
   [:div.title (aget build "name")]
   [:div.meta
    [:span.number (aget build "number")]
    [:ol.history
     (for [h (aget build "history")]
       ^{:key (aget h "id")} [:li (if (= (aget h "status") "success") "*" "!")])]]])

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
