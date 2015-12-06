(ns buildmonitor.socket
  (:require [cljs.core.async :refer [chan <! >! put! close!]]))

(defn- prepare-url [path]
  (let [loc (.-location js/window)]
    (str (if (= "https" (.-protocol loc))
           "wss" "ws")
         "://"
         (.-host loc)
         path)))

(defn- reconnect [url connection-channel read-channel]
  (js/console.log "attempting reconnect")
  (let [con (js/WebSocket. url)]
    (set! (.-onopen con)
          (fn []
            (put! connection-channel {:connected true})))
    (set! (.-onmessage con)
          (fn [e]
            (put! read-channel (.-data e))))
    (set! (.-onclose con)
          (fn []
            (put! connection-channel {:connected false})
            (js/setTimeout (fn [] (reconnect url connection-channel read-channel)) 5000)))))

(defn open-socket [path connection-channel read-channel]
  (let [url (prepare-url path)]
    (reconnect url connection-channel read-channel)))
