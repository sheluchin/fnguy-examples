(ns com.fnguy.rock-paper-scissors
  (:require
   [com.fnguy.statecharts :as fnsc]
   [com.fulcrologic.statecharts.chart :refer [statechart]]
   [com.fulcrologic.statecharts.data-model.operations :as ops]
   [com.fulcrologic.statecharts.elements :as el :refer [data-model log
                                                        on-entry on-exit
                                                        script-fn state
                                                        transition]]))

(defn random-throw [] (rand-nth [:rock :paper :scissors]))

(defn pick-winner
  [p1 p2]
  (let [rules {[:rock :rock] :draw
               [:rock :paper] :state/player2
               [:rock :scissors] :state/player1
               [:paper :rock] :state/player1
               [:paper :paper] :draw
               [:paper :scissors] :state/player2
               [:scissors :rock] :state/player2
               [:scissors :paper] :state/player1
               [:scissors :scissors] :draw}]
    (get rules [p1 p2])))

(defn throw->winner
  []
  (let [p1-throw (random-throw)
        p2-throw (random-throw)
        winner (pick-winner p1-throw p2-throw)]
    winner))

(defn data->round-winner
  [data]
  (get-in data [:_event :data :round/winner]))

(defn data->new-winner-score
  [data]
  (let [winner-id (data->round-winner data)]
    (inc (get-in data [winner-id :player/score] 0))))

(defn data->round-draw?
  [data]
  (let [winner-id (data->round-winner data)]
    (= winner-id :draw)))

(defn update-winner-score
  []
  (script-fn [_env data]
    (let [winner-id (data->round-winner data)
          new-score (data->new-winner-score data)]
      [(ops/assign [winner-id :player/score] new-score)])))

(defn ->tie-breaker?
  [_env {:state/keys [player1 player2], :as data}]
  (when-not (data->round-draw? data)
    (let [winner-id (data->round-winner data)
          new-winner-score (data->new-winner-score data)
          loser-score (:player/score
                       (if (= winner-id :state/player1)
                         player2 player1))]
      (= new-winner-score loser-score))))

(defn not-draw? [_env data] (not (data->round-draw? data)))

(defn ->game-over?
  [env data]
  (let [new-score (data->new-winner-score data)
        not-draw? (not-draw? env data)]
    (and not-draw? (= new-score 2))))

(defn log-game-winner
  [_ data]
  (let [winner-id (data->round-winner data)]
    (str "Game Over! 😁 " winner-id " won!!")))

(def rps
  (statechart {}
    (data-model {:expr {:state/player1 {:player/score 0}
                        :state/player2 {:player/score 0}}})
    (state {:id :state/game
            :initial :state/r1}
      (state {:id :state/r1}
        (transition {:id :transition/r1->r2
                     :event :event/throw
                     :target :state/r2
                     :cond not-draw?})
        (on-exit {} (update-winner-score)))
      (state {:id :state/r2}
        (transition {:id :transition/r2->tie-breaker
                     :event :event/throw
                     :target :state/tie-breaker
                     :cond ->tie-breaker?})
        (transition {:id :transition/r2->game-over
                     :event :event/throw
                     :target :state/game-over
                     :cond ->game-over?})
        (on-exit {} (update-winner-score)))
      (state {:id :state/tie-breaker}
        (transition {:id :transition/tie-breaker->game-over
                     :event :event/throw
                     :target :state/game-over
                     :cond not-draw?})
        (on-exit {} (update-winner-score)))
      (state {:id :state/game-over}
        (on-entry {}
          (log {:level :info
                :expr log-game-winner}))))))

(comment
  (do
    (reset! fnsc/sessions {})
    (fnsc/start-new-sc! ::1 ::rps rps)
    nil)
  (fnsc/send-event! ::1 :event/throw {:round/winner (throw->winner)}))
