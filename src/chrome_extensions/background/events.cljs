(ns chrome-extensions.background.events
  (:require [chrome-extensions.background.utils :refer [logging
                                                        error-handler
                                                        stringify]]))

(enable-console-print!)

(def constants (atom {:identifiers {:tab-ids "tabsIds"}
                      :url-mappings {:google "https://www.google.com"}}))

;; COMMANDS.
;;
;; Check for commands. If the user press any of the available keyboard
;; shortcuts, process accordingly.

(defn- get-current-tab
  [cb]
  (.query js/chrome.tabs
          #js {:active true :currentWindow true}
          (fn [tabs]
            (cb (first tabs)))))

(defn- get-and-update-tab-position
  [tab]
  (let [tab-ids-key (:tab-ids (:identifiers @constants))]
    (js/chrome.storage.local.get
      (clj->js {tab-ids-key {}})
      (fn [items]
        (let [tabs (js->clj (aget items tab-ids-key))
              curr-tab (get tabs (str (.-id tab)))]
          (logging "tabs" tabs)
          (logging "curr-tab" curr-tab)
          (if (nil? curr-tab)
            (do (js/chrome.windows.create #js {:tabId (.-id tab)})
                (js/chrome.storage.local.set (clj->js {tab-ids-key (assoc tabs (str (.-id tab)) tab)})))
            (do (js/chrome.tabs.move (get curr-tab "id")
                                     #js {:windowId (get curr-tab "windowId")
                                          :index (get curr-tab "index")}
                                     (fn [moved-tab]
                                       (js/chrome.tabs.update (.-id moved-tab)
                                                              #js {:active true})))
                (js/chrome.storage.local.set (clj->js {tab-ids-key (dissoc tabs (str (get curr-tab "id")))})))))))))

(defn- toggle-tab-to-window
  []
  (get-current-tab get-and-update-tab-position))

(defmulti command-selector
  (fn [command]
    (do
      (logging "command triggered" command)
      (keyword command))))

(defmethod command-selector :default
  [_]
  (logging "No command matched"))

(defmethod command-selector :toggle-tab-to-window
  [_]
  (toggle-tab-to-window))

(defmethod command-selector :print-storage
  [_]
  ;; Prints any element that is being saved to storage.
  (let [element-to-print "historyItems"]
    (.get js/chrome.storage.sync
          element-to-print
          (fn [items]
            (logging element-to-print (aget items element-to-print))))))

(defn command-selector-router
  [command]
  (command-selector command))

;; OMNIBOX.
;;
;; Check for mappings provided via omnibox.

(defn- fetch-url-for-text
  [text]
  ((keyword text) (:url-mappings @constants)))

(defmulti omnibox-url-selector
  (fn [text disposition]
    (do
      (logging "text" (stringify text))
      (logging "disposition" (stringify disposition))
      disposition)))

(defmethod omnibox-url-selector :default
  [_ _]
  (logging "No disposition matched"))

;; TODO: change the key here to "foregroundTab" once there is support for that.
;; Now it just creates a new tab next to the current one.
(defmethod omnibox-url-selector "currentTab"
  [text _]
  (get-current-tab
    (fn [tab]
      (when-let [url (fetch-url-for-text text)]
        (let [options {:url url}]
          (.create js/chrome.tabs
                   (clj->js (assoc options :active true :index (+ 1 (.-index tab))))))))))

;; TODO: uncomment this once the previous TODO is fixed.
;; Not supposed to get here. This is the desired behavior once there is
;; support for choosing either current, foreground or background tabs.
; (defmethod omnibox-url-selector "currentTab"
;   [text _]
;   (when-let [url (fetch-url-for-text text)]
;     (let [options {:url url}]
;       (.update js/chrome.tabs (clj->js options)))))

(defn omnibox-url-selector-router
  [text disposition]
  (omnibox-url-selector text disposition))
