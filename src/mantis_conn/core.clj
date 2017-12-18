(ns mantis-conn.core
  (:require [clojure.data.xml :as xml]
            [clj-http.client :as client]
            [clojure.java.io :as io])
  (:gen-class))

(defn string->stream
  ([s] (string->stream s "UTF-8"))
  ([s encoding]
   (-> s
       (.getBytes encoding)
       (java.io.ByteArrayInputStream.))))

(def host "http://localhost:8989/api/soap/mantisconnect.php")

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

(defn exec []
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
     :status (first (val-by-tag (v :status) :name))
     :due (first (v :due_date))
     :creatorId (first (val-by-tag (v :reporter) :id))
     :handlerId (first (val-by-tag (v :handler) :id))
     :projectId (first (val-by-tag (v :project) :id))
     :relatedTasks (map rel-xf (v :relationships))}))

(defn transform-issues [data]
  (let [issues (-> data :content first :content first :content first :content)]
    (map issue-xf issues)))

(defn -main
  [& args]
  (clojure.pprint/pprint (transform-issues (exec))))
