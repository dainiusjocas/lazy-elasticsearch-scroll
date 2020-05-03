(ns scroll.batch)

(def default-size 1000)

(defn set-batch-size [query opts]
  (assoc query :size (or (:size opts) (:size query) default-size)))
