#!/usr/bin/env bb
;; output build params to "params"

(ns build-params)

(defn linux-matrix []
  (-> []
      (into (map #(into {:os-version (if (<= % 12) "22.04" "24.04")
                         :c-compiler (str "gcc-" %)
                         :cxx-compiler (str "g++-" %)}
                        (case %
                          9 {:run-tests true}
                          13 {:job-name "use system libraries"
                              :use-syslibs true}
                          14 {:job-name "shared libscsynth"
                              :shared-libscsynth true}
                          {})))
        [9 11 12 13 14])
      (into (map #(do {:os-version (if (<= % 11) "22.04" "24.04")
                       :c-compiler (str "clang-" %)
                       :cxx-compiler (str "clang++-" %)}))
            [11 15 16 17 18])))

(defn all-params []
  {:sc-version (or (System/getenv "SC_VERSION")
                   (throw (ex-info "Must set $SC_VERSION" {})))
   :linux-matrix (linux-matrix)
   :macos-matrix []})

(defn -main [])

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
