(ns iwaswhere-web.ui.search
  (:require [iwaswhere-web.helpers :as h]
            [iwaswhere-web.ui.utils :as u]
            [matthiasn.systems-toolbox-ui.reagent :as r]
            [clojure.string :as s]))

(defn search-view
  "Renders search component."
  [{:keys [observed local put-fn]}]
  (let [local-snapshot @local
        store-snapshot @observed
        hashtags (:hashtags (:cfg store-snapshot))
        mentions (:mentions (:cfg store-snapshot))
        location-timeout-fn (fn [search-text]
                              (.setTimeout js/window
                                           #(aset js/window "location" "hash" (js/encodeURIComponent search-text))
                                           5000))
        update-search-fn (fn [new-search]
                           (swap! local assoc-in [:temp-query] new-search)
                           (swap! local update-in [:set-location]
                                  (fn [prev] (when prev (.clearTimeout js/window prev))
                                    (location-timeout-fn (:search-text new-search))))
                           (put-fn [:state/get (merge new-search {:sort-by-upvotes (:sort-by-upvotes @observed)})]))
        on-input-fn #(update-search-fn (h/parse-search (.. % -target -innerText)))

        ; find incomplete tag or mention before cursor, show suggestions
        before-cursor (h/string-before-cursor (:search-text (:temp-query @local)))
        [curr-tag f-tags] (h/autocomplete-tags before-cursor "#" hashtags)
        [curr-mention f-mentions] (h/autocomplete-tags before-cursor "@" mentions)
        tag-replace-fn (fn [curr-tag tag]
                         (let [curr-tag-regex (js/RegExp (str curr-tag "(?!" h/tag-char-class ")") "i")
                               search-text (:search-text (:temp-query @local))
                               new-search (h/parse-search (s/replace search-text curr-tag-regex tag))]
                           (swap! local assoc-in [:current-query] new-search)
                           (update-search-fn new-search)))
        get-tags #(% (:current-query @local))
        on-keydown-fn (fn [ev]
                        (let [key-code (.. ev -keyCode)]
                          (when (= key-code 9)          ; TAB key pressed
                            (when (and curr-tag (seq f-tags))
                              (tag-replace-fn curr-tag (first f-tags)))
                            (when (and curr-mention (seq f-mentions))
                              (tag-replace-fn curr-mention (first f-mentions)))
                            (.setTimeout js/window (fn [] (u/focus-on-end (.-target ev))) 50)
                            (.preventDefault ev))))]
    [:div.search
     [:div.hashtags
      (for [tag (get-tags :tags)]
        ^{:key (str "search-" tag)} [:span.hashtag tag])
      (for [tag (get-tags :not-tags)]
        ^{:key (str "search-n" tag)} [:span.hashtag.not-tag tag])
      (for [tag (get-tags :mentions)]
        ^{:key (str "search-" tag)} [:span.mention tag])]
     [:div.search-field {:content-editable true
                         :on-input         on-input-fn
                         :on-key-down      on-keydown-fn}
      (:search-text (:current-query local-snapshot))]
     [u/suggestions "search" f-tags curr-tag tag-replace-fn "hashtag"]
     [u/suggestions "search" f-mentions curr-mention tag-replace-fn "mention"]]))

(defn init-fn
  "Initializes listener for location hash changes, which alters local component state with
  the latest query on change, plus sends query to backend."
  [{:keys [local put-fn]}]
  (let [hash-change-fn #(let [new-query (h/query-from-search-hash)]
                         (when (not= new-query (:temp-query @local))
                           (swap! local assoc-in [:current-query] new-query)
                           (put-fn [:state/get new-query])))]
    (aset js/window "onhashchange" hash-change-fn)
    (hash-change-fn)))

(defn cmp-map
  [cmp-id]
  (r/cmp-map {:cmp-id  cmp-id
              :init-fn init-fn
              :view-fn search-view
              :dom-id  "search"}))
