(ns influxdb-proxy.core
    (:require [clojure.data.json      :as json]
              [ring.middleware.reload :as reload]
              [ring.middleware.cors   :as cors]
              [compojure.route        :as route]
              [compojure.handler      :as handler]
              [capacitor.core         :as influx]
              [riemann.streams        :as streams]
              riemann.logging
    )
    (:import (org.apache.log4j Level))
    (:use [org.httpkit.server :only [run-server]]
          [compojure.core     :only [defroutes GET POST DELETE ANY context]]
          clojure.tools.logging
    )
    (:gen-class))

(defn influx-point-to-event
    [point]
    
    {:time (first point) :metric (second point)}
)

(defn event-to-influx-point
    [evt]
    
    [(:time evt), (:metric evt)]
)

(defn ewma-series
    ;; {"name":"canaryio.us-east-1.seiumaster","columns":["time","mean"],"points":[[1408016910000,0.118625]]}
    [halflife series]
    
    (let [
        series-name  (str (get series "name") ".ewma")
        column-names (take 2 (get series "columns"))
        
        ;; sort points by time (*usually* the first column)
        time-sorted-points (sort #(< (first %1) (first %2)) (get series "points"))
        
        ;; store transformed values here; use a vector so conj adds to the end
        transformed-events (atom [])
        
        ;; this is a stream-like thing that appends received events to
        ;; transformed-events
        capturer (partial swap! transformed-events conj)
        
        ;; here we set up to pass events through the ewma stream function
        ewma-stream (streams/ewma-timeless halflife capturer)
        
        ;; only pass the first two points to ewma-point; should be "time"
        ;; and the value we want
        time-and-value-points (map (partial take 2) time-sorted-points)
        
        ;; convert points to events
        input-events (map influx-point-to-event time-and-value-points)
    ]
        ;; feed all events through stream
        (doseq [event input-events]
            (ewma-stream event)
        )
        
        ;; return this set
        {
            "name"    series-name
            "columns" column-names
            
            ;; convert transformed events to [time, metric] pairs
            "points"  (map event-to-influx-point @transformed-events)
        }
    )
)

(defn ewma-result
    ;; [{"name":"canaryio.us-east-1.seiumaster","columns":["time","mean"],"points":[[1408016910000,0.118625]]}]
    [halflife query-results]
    
    (map (partial ewma-series halflife) query-results)
)


(defn get-db-series [req]
    (info (get (:query-params req) "q")) ;; print influxdb query
    
    (let [
        db        (-> req :params :database)
        query     (-> req :params :q)
        user      (-> req :params :u)
        pass      (-> req :params :p)
        precision (:time_precision (:params req) "s")
        
        client (influx/make-client {
            :host "influxdb.example.com"
            :port 80
            :username user
            :password pass
            :db db
        })
        
        influx-resp (influx/get-query-req client precision query)
    ]
        ;; [{"name":"canaryio.us-east-1.seiumaster","columns":["time","mean"],"points":[[1408016910000,0.118625]]}]
        (if (= 200 (:status influx-resp))
            (let [
                query-results (json/read-str (:body influx-resp))
                ewma-results (ewma-result 1/20 query-results)
                combined-results (concat query-results ewma-results)
                body (json/write-str combined-results)
            ]
                ;; last statement; return value!
                {:status 200
                 :body body
                 :headers {"Content-Type" "application/json"}}
            )
            
            ;; else
            {:status (:status influx-resp)
             :body (:body influx-resp)
             :headers {"Content-Type" (-> req :headers "Content-Type")}}
        )
    )
)

(defroutes all-routes
    (context "/db/:database" []
        ;; /db/<database>/series?q=<query>&u=<user>&p=<pass>
        (GET "/series" [] get-db-series)
    ) 
    
    (route/not-found "I have no idea what you're talking about")
)

(defn -main
    "http server entry point"
    
    [& args]
    
    (riemann.logging/init {:console true})
    (riemann.logging/set-level "riemann" Level/DEBUG)
    (riemann.logging/set-level Level/INFO)

    (prn "ready")
    (run-server
        (cors/wrap-cors
            (reload/wrap-reload (handler/site #'all-routes))
            :access-control-allow-origin #".*"
            :access-control-allow-methods [:get])
        {:port 8080})
)
