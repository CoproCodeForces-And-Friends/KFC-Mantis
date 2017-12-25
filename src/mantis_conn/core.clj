(ns mantis-conn.core
  (:require [clojure.data.xml :as xml]
            [environ.core :refer [env]]
            [clj-http.client :as client]
            [clj-time.core :as t]
            [clj-time.local :as tl]
            [clj-time.format :as tf]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log])
  (:import (java.util.concurrent Executors TimeUnit))
  (:gen-class))

(defonce last-sync (atom nil))

(defn string->stream
  ([s] (string->stream s "UTF-8"))
  ([s encoding]
   (-> s
       (.getBytes encoding)
       (java.io.ByteArrayInputStream.))))

(defn build-xml [method params]
  (xml/element
   :SOAP-ENV:Envelope
   {:xmlns:SOAP-ENV "http://schemas.xmlsoap.org/soap/envelope/"}
   (xml/element
    :SOAP-ENV:Body {}
    (xml/element
     (keyword (str "SOAP-ENV:" method)) {}
     (doall
      (for [[param value] params]
        (xml/element (keyword (str "SOAP-ENV:" (name param))) {} value)))))))

(defn get-data [host method params]
  (client/post host
               {:content-type "application/soap+xml"
                :body (xml/emit-str (build-xml method params))}))

(defn convert-html [http]
  (-> http :body string->stream xml/parse))

(defn fetch-mantis [host _]
  (log/info "Fetching data...")
  (convert-html
   (get-data host "mc_filter_search_issues" {:username "administrator"
                                             :password "root"
                                             :per-page -1})))

(defn val-by-tag [data tg]
  (:content (first (filter #(= (:tag %) tg) data))))

(defn rel-xf [r]
  (let [content (:content r)]
    {:id (val-by-tag content :id)}))

(defn issue-xf [issue]
  (let [content (:content issue)
        v (partial val-by-tag content)]
    {:id (Integer/parseInt (first (v :id)))
     :name (first (v :summary))
     :description (first (v :description))
     :creationDate (first (v :date_submitted))
     :lastUpdated (first (v :last_updated))
     :status (first (val-by-tag (v :status) :name))
     :due (first (v :due_date))
     :creatorId (first (val-by-tag (v :reporter) :id))
     :handlerId (first (val-by-tag (v :handler) :id))
     :projectId (first (val-by-tag (v :project) :id))
     :relatedTasks (map rel-xf (v :relationships))}))

(defn transform-issues [data]
  (let [issues (-> data :content first :content first :content first :content)]
    (log/info "Transforming" (count issues) "issues from Mantis")
    (map issue-xf issues)))

(defn send-data [host data]
  (log/info "sending data..." data)
  (client/post host
               {:content-type :json
                :form-params data}))

(defn crop-date [issue]
  (let [lu-str (:lastUpdated issue)
        lu (tf/parse lu-str)
        ls @last-sync]
    (or (nil? ls) (t/after? lu ls))))

(defn log-empty [lst]
  (when (empty? lst) (log/info "No updates"))
  lst)

(defn send-one-by-one [host data]
  (doall
   (->> data
        (filter crop-date)
        log-empty
        (map (partial send-data host)))))

(defn agent-error-handler [name]
  (fn [_ ex]
    (log/error "Agent" name "failed:" ex)))

(defonce job-agent (agent [] :error-handler
                          (agent-error-handler "job-agent")))

(defn start-job [interval callback]
  (let [pool (Executors/newScheduledThreadPool 1)]
    (.scheduleAtFixedRate pool
                          #(send-off job-agent callback)
                          0 interval TimeUnit/SECONDS)))

(defn set-last-sync [_]
  (reset! last-sync (tl/local-now)))

(defn -main
  [& args]
  (let [sender (partial send-one-by-one (env :storage-api))
        interval (or (env :interval) 10)
        fetcher (partial fetch-mantis (env :mantis-api))]
    (log/info "Storage:" (env :storage-api))
    (log/info "Mantis:" (env :mantis-api))
    (log/info "Starting fetch job with" interval "minute(s) interval")
    (start-job interval (comp set-last-sync sender transform-issues fetcher))))
