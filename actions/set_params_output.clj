#!/usr/bin/env bb
;; Run locally:
;; $ GITHUB_OUTPUT=ghoutput GITHUB_REF=refs/tags/v1.2.3 GITHUB_SHA=12345 ./actions/set_params_output.clj
;; $ cat ghoutput
;; params<<EOF
;; {
;;   "sc-version" : "v1.2.3",
;; ...
;; }
;; EOF
;; $ GITHUB_OUTPUT=ghoutput GITHUB_REF=develop GITHUB_SHA=12345 ./actions/set_params_output.clj
;; params<<EOF
;; {
;;   "sc-version" : "12345",
;; ...
;; }
;; EOF

(ns actions.set-params-output
  (:require [clojure.string :as str]
            [cheshire.core :as json]))

;; number of jobs to split unit tests for each platform
(def default-test-splits 4)

(defn getenv [s] (or (System/getenv s) (throw (ex-info (str "Must set $" s) {}))))
;; add an :id field in order to perform some acrobatics (look for matrix.id in .github/workflows/actions.yml)
(defn index-matrix [v] (into [] (map-indexed #(assoc %2 :id %1)) v))
;; if :run-tests true then add the fields to split tests
(defn expand-splits [v] (into [] (map #(cond-> %
                                         (:run-tests %) (assoc :splits-matrix (range default-test-splits)
                                                               :test-splits default-test-splits)))
                              v))

(defn sc-version []
  (if-some [[_ tag] (re-matches #"refs/tags/(.*)" (getenv "GITHUB_REF"))]
    tag
    (getenv "GITHUB_SHA")))

(defn all-params []
  {:sc-version (sc-version)
   :actions-cache-version 1 ;;TODO prefix all caches
   :lint false
   :linux-matrix (-> []
                     (into (map #(into {:os-version (if (<= % 12) "22.04" "24.04")
                                        :c-compiler (str "gcc-" %)
                                        :cxx-compiler (str "g++-" %)
                                        :use-syslibs (= % 13)
                                        :shared-libscsynth (= % 14)}
                                       (case %
                                         12 {:run-tests true}
                                         13 {:job-name "use system libraries"}
                                         14 {:job-name "shared libscsynth"}
                                         {})))
                           [9 11 12 13 14])
                     (into (map #(do {:os-version (if (<= % 11) "22.04" "24.04")
                                      :c-compiler (str "clang-" %)
                                      :cxx-compiler (str "clang++-" %)
                                      :use-syslibs false
                                      :shared-libscsynth false}))
                           [11 15 16 17 18])
                     index-matrix
                     expand-splits
                     ;;FIXME temporary
                     (->> (into [] (filter #(= "gcc-9" (:c-compiler %))))))
   ;;FIXME temporary
   :macos-matrix [] #_(-> [{:job-name "arm64"
                       :os-version "15"
                       :xcode-version "16.0"
                       :qt-version "6.7.3" ; will use qt from aqtinstall
                       :qt-modules "qtwebengine qtwebchannel qtwebsockets qtpositioning"
                       :deployment-target "11"
                       :cmake-architectures "arm64"
                       :homebrew-packages "libsndfile readline fftw portaudio"
                       :vcpkg-packages ""
                       :vcpkg-triplet ""
                       :extra-cmake-flags "" ; "-D SC_VERIFY_APP=ON" # verify app doesn't seem to work with official qt6
                       :artifact-suffix "macOS-arm64"
                       :run-tests true}
                      {:job-name "x64"
                       :os-version "13"
                       :xcode-version "15.2"
                       :qt-version "6.7.3" ; will use qt from aqtinstall
                       :qt-modules "qtwebengine qtwebchannel qtwebsockets qtpositioning"
                       :deployment-target "11"
                       :cmake-architectures "x86_64"
                       :homebrew-packages "libsndfile readline fftw portaudio"
                       :vcpkg-packages ""
                       :vcpkg-triplet ""
                       :extra-cmake-flags "" ; -D SC_VERIFY_APP=ON # verify app doesn't seem to work with official qt6
                       :artifact-suffix "macOS-x64"
                       :run-tests true}
                      {:job-name "x64 legacy"
                       :os-version "13"
                       :xcode-version "14.1"
                       :qt-version "5.15.2" ; will use qt from aqtinstall
                       :qt-modules "qtwebengine"
                       :deployment-target "10.15"
                       :cmake-architectures "x86_64"
                       :homebrew-packages "readline portaudio"
                       :vcpkg-packages "libsndfile fftw3"
                       ;:homebrew-packages "" ; use this instead when cross-compiling for x86_64 on arm64
                       ;:homebrew-uninstall "readline" ; use this instead when cross-compiling for x86_64 on arm64
                       ;:vcpkg-packages "readline portaudio libsndfile fftw3" ; use this instead when cross-compiling for x86_64 on arm64
                       :vcpkg-triplet "x64-osx-release-supercollider" ; required for build-libsndfile
                       :extra-cmake-flags ""
                       ; set if needed - will trigger artifact upload
                       :artifact-suffix "macOS-x64-legacy"
                       :run-tests true}]
                     (into (map #(into {:os-version "13"
                                        :xcode-version "15.2"
                                        :deployment-target ""
                                        :cmake-architectures "x86_64"
                                        :homebrew-packages "qt@6 libsndfile readline fftw portaudio"
                                        :vcpkg-packages ""
                                        :vcpkg-triplet ""
                                        :extra-cmake-flags "-D LIBSCSYNTH=ON"}
                                       %))
                           [{:job-name "x64 use system libraries"
                             :homebrew-packages "qt@6 libsndfile readline fftw portaudio yaml-cpp boost"
                             :extra-cmake-flags "-D SYSTEM_BOOST=ON -D SYSTEM_YAMLCPP=ON"}
                            {:job-name "x64 shared libscsynth"
                             :homebrew-packages "qt@6 libsndfile readline fftw portaudio"
                             :extra-cmake-flags "-D LIBSCSYNTH=ON"}])
                     index-matrix
                     expand-splits)})

(defn -main []
  (let [params (all-params)
        s (str/trim (json/encode params {:pretty true}))
        delim (if (str/includes? s "EOF") (str (random-uuid)) "EOF")
        _ (assert (not (str/includes? s delim)))
        ;; https://docs.github.com/en/actions/writing-workflows/choosing-what-your-workflow-does/workflow-commands-for-github-actions#multiline-strings
        params-setter (str "params<<" delim "\n" s "\n" delim "\n")]
    (print params-setter)
    (flush)
    (spit (getenv "GITHUB_OUTPUT") params-setter :append true)))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
