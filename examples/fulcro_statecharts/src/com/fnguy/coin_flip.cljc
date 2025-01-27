(ns com.fnguy.coin-flip
  (:require
   [com.fnguy.statecharts :as fnsc]
   [com.fulcrologic.statecharts :as sc]
   [com.fulcrologic.statecharts.chart :refer [statechart]]
   [com.fulcrologic.statecharts.data-model.operations :as ops]
   [com.fulcrologic.statecharts.elements :as el :refer [assign data-model
                                                        final log on-entry
                                                        on-exit parallel
                                                        script script-fn state
                                                        transition]]
   [com.fulcrologic.statecharts.event-queue.core-async-event-loop :as loop]
   [com.fulcrologic.statecharts.protocols :as sp]
   [com.fulcrologic.statecharts.simple :as simple]
   [taoensso.timbre :as tlog]))

; (defn custom-output-fn
;   [{:keys [level ?err msg_]}]
;   (let [msg (force msg_)
;         ;; Extract the part after the dash
;         formatted-msg (second (clojure.string/split msg #"-" 2))]
;     (str msg)))

; ;; Configure Timbre to use the custom output function
; (log/merge-config!
;  {:output-fn custom-output-fn})

(defn random-flip [& _] (rand-nth [true false]))

(def coin-flips
  (let [heads :id/heads
        tails :id/tails
        pick-rand #(rand-nth [heads tails])]
    (statechart {}
      (state {:id heads}
        (transition {:event :flip
                     :target tails
                     ;; BUG! `pick-rand` will be called in each visited transition
                     :cond (fn [_ _] (= tails (pick-rand)))})
        (transition {:event :flip
                     :target heads
                     :cond (fn [_ _] (= heads (pick-rand)))}))
      (state {:id tails}
        (transition {:event :flip
                     :target heads
                     :cond (fn [_ _] (= heads (pick-rand)))})
        (transition {:event :flip
                     :target tails
                     :cond (fn [_ _] (= tails (pick-rand)))})))))

(def simpler-coin-flip
  (statechart {}
    (state {:id :state/coin}
      (transition {:id :transition/coin->heads
                   :target :state/heads
                   :event :event/flip
                   :cond random-flip})
      (transition {:id :transition/coin->tails
                   :target :state/tails
                   :event :event/flip})
      (state {:id :state/heads}
        (on-entry {}
          (log {:label "heads!"})))
      (state {:id :state/tails}
        (on-entry {}
          (log {:label "tails!"}))))))

(def flip-mem
  "Random flip with memory."
  (statechart {}
    (state {:id :state/coin}
      (transition {:id :transition/coin->heads
                   :target :state/heads
                   :event :event/flip
                   :cond (fn [& _] (rand-nth [true false]))}
        (script-fn [_env {:keys [heads] :as _data}]
          [(ops/assign :heads (inc (or heads 0)))]))
      (transition {:id :transition/coin->tails
                   :target :state/tails
                   :event :event/flip}
        (script-fn [_env {:keys [tails] :as _data}]
          [(ops/assign :tails (inc (or tails 0)))]))
      (state {:id :state/heads})
      (state {:id :state/tails}))))

(comment
  (reset! fnsc/sessions {})
  (fnsc/start-new-sc! 1 ::coin-flips flip-mem)
  (fnsc/send-event! 1 :event/flip)
  (get @fnsc/sessions 1))
