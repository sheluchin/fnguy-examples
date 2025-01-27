(ns com.fnguy.statecharts
  (:require
   [com.fulcrologic.statecharts :as sc]
   ; [com.fulcrologic.statecharts.data-model.working-memory-data-model :refer [new-model]]
   [com.fulcrologic.statecharts.event-queue.core-async-event-loop :as loop]
   [com.fulcrologic.statecharts.protocols :as sp]
   [com.fulcrologic.statecharts.simple :as simple]))

(def sessions (atom {}))

(defn show-states [wmem]
  (println "showing state" {:wmem wmem}))

(defn create-env [wmem]
  ;; Create an environment with a custom working memory store
  (simple/simple-env
   {::sc/working-memory-store
    (reify sp/WorkingMemoryStore
      (get-working-memory [_ _ _] @wmem)
      (save-working-memory! [_ _ _ m] (reset! wmem m)))}))
     ;; Flat model is recommended for most cases!
     ; ::sc/data-model (new-model)}))

(defn start-new-sc!
  [session-id chart-key chart]
  (let [wmem (let [a (atom {})]
               (add-watch a :printer (fn [_ _ _ n] (show-states n)))
               a)
        env (create-env wmem)]
    (simple/register! env chart-key chart)
    (let [running? (loop/run-event-loop! env 100)]
      (simple/start! env chart-key session-id)
      (swap! sessions assoc session-id {:env env :running? running?}))))

(defn send-event!
  ([session-id event]
   (send-event! session-id event nil))
  ([session-id event data]
   (let [{:keys [env]} (get @sessions session-id)]
     (when env
       (simple/send! env {:target session-id :event event :data data})))))

(defn stop-sc!
  [session-id]
  (let [{:keys [running?]} (get @sessions session-id)]
    (when running?
      (reset! running? false))))

(comment
  ;; Example usage in the REPL
  ;; Start new statechart instances
  ; (start-new-sc! 1 ::flips coin-flips)
  ;; Send events to specific statechart instances
  (send-event! 1 :event/flip))
