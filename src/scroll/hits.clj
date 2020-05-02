(ns scroll.hits)

(defn extract-hits [batch keywordize?]
  (get-in batch (if keywordize? [:hits :hits] ["hits" "hits"])))
