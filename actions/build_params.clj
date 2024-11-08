#!/usr/bin/env bb
;; Run locally:
;; $ GITHUB_OUTPUT=ghoutput SC_VERSION=1 ./actions/build_params.clj
;; $ cat ghoutput
;; params='{"sc-version":"1", ...etc...}'
;; $

(ns build-params
  (:require [cheshire.core :as json]))

(defn add-json-matrix [v] (into [] (mapv #(assoc % :json-matrix (json/encode %)) v)))

(defn all-params []
  {:sc-version (or (System/getenv "SC_VERSION") (throw (ex-info "Must set $SC_VERSION" {})))
   :linux-matrix (-> []
                     (into (map #(into {:os-version (if (<= % 12) "22.04" "24.04")
                                        :c-compiler (str "gcc-" %)
                                        :cxx-compiler (str "g++-" %)}
                                       (case %
                                         12 {:run-tests true}
                                         13 {:job-name "use system libraries"
                                             :use-syslibs true}
                                         14 {:job-name "shared libscsynth"
                                             :shared-libscsynth true}
                                         {})))
                           [9 11 12 13 14])
                     (into (map #(do {:os-version (if (<= % 11) "22.04" "24.04")
                                      :c-compiler (str "clang-" %)
                                      :cxx-compiler (str "clang++-" %)}))
                           [11 15 16 17 18])
                     add-json-matrix)
   :macos-matrix []})

(defn -main []
  (let [params (all-params)]
    (println "Setting build params:")
    (println (json/encode params {:pretty true}))
    (spit (or (System/getenv "GITHUB_OUTPUT") (throw (ex-info "Must set $GITHUB_OUTPUT")))
          (str "params='" (json/encode params) "'\n")
          :append true)))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
