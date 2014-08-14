(ns influxdb-proxy.core
    (:require [clojure.data.json      :as json]
              [ring.middleware.reload :as reload]
              [compojure.route        :as route]
              [compojure.handler      :as handler]
              [capacitor.core         :as influx]
    )
    (:use [org.httpkit.server :only [run-server]]
          [compojure.core     :only [defroutes GET POST DELETE ANY context]]
    )
    (:gen-class))

(defn get-db-series [req]
    (let [
        db    (-> req :params :database)
        query (-> req :params :q)
        user  (-> req :params :u)
        pass  (-> req :params :p)
        
        client (influx/make-client {
            :host "influxdb.docker.bsdinternal.com"
            :port 80
            :username user
            :password pass
            :db db
        })
        
        influx-resp (influx/get-query-req client query)
        status (:status influx-resp)
        body (if (= 200 status) (json/read-str (:body influx-resp)))
    ]
        {:status status
         :body (json/write-str body)
         :headers {"Content-Type" "application/json"}}
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
    
    (prn "ready")
    (run-server
        (reload/wrap-reload (handler/site #'all-routes))
        {:port 8080})
)
