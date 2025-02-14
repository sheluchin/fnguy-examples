(ns com.fnguy.pathom-instrumentation
  (:require
   [com.wsscode.pathom3.connect.indexes :as pci]
   [com.wsscode.pathom3.connect.operation :as pco]
   [com.wsscode.pathom3.interface.eql :as p.eql]
   [com.wsscode.pathom3.connect.runner :as pcr]
   [com.wsscode.pathom3.connect.planner :as pcp]
   [com.wsscode.pathom3.plugin :as p.plugin]
   [taoensso.tufte :as tufte]))

(defn tufte-resolver-wrapper
  "Wrap a Pathom3 resolver call in `tufte/p`."
  [resolver]
  (fn [env input]
    (let [resolver-name (-> (get-in env [::pcp/node ::pco/op-name])
                            (name)
                            (keyword))
          identifier (str "resolver: " resolver-name)]
      (tufte/p identifier
               (resolver env input)))))

(defn tufte-process-wrapper
  "Wrap a Pathom3 process in `tufte/profile`."
  [process-ast]
  (fn [env ast]
    (tufte/profile {}
                   (process-ast env ast))))

(p.plugin/defplugin tufte-profile-plugin
  {::p.plugin/id `tufte-profile-plugin
   ::pcr/wrap-resolve tufte-resolver-wrapper
   ::p.eql/wrap-process-ast tufte-process-wrapper})

(tufte/add-basic-println-handler! {})

(pco/defresolver all-items
  "Takes no input and outputs `:all-items` with their `:id`."
  []
  {::pco/output [{:all-items [:id]}]}
  {:all-items
   [{:id 1}
    {:id 2}
    {:id 3}]})

(pco/defresolver fetch-v
  "Takes an `:id` and outputs its `:v`."
  [{:keys [id]}]
  (Thread/sleep 300)
  {:v (* 10 id)})

(pco/defresolver batch-fetch-v
  "Takes a _batch_ of `:id`s and outputs their `:v`."
  [items]
  {::pco/input  [:id]
   ::pco/output [:v]
   ::pco/batch? true}
  (Thread/sleep 300)
  (mapv #(hash-map :v (* 10 (:id %))) items))

(p.eql/process
 (-> (p.plugin/register tufte-profile-plugin)
     (pci/register [fetch-v
                    #_batch-fetch-v
                    all-items]))
 [{:all-items [:v]}])
; => {:all-items [{:v 10} {:v 20} {:v 30}]}

;; Again, but with the batch resolver.
(p.eql/process
 (-> (p.plugin/register tufte-profile-plugin)
     (pci/register [all-items
                    #_fetch-v
                    batch-fetch-v]))
 [{:all-items [:v]}])
; => {:all-items [{:v 10} {:v 20} {:v 30}]}
