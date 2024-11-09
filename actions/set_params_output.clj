#!/usr/bin/env bb
;; Run locally:
;; $ GITHUB_OUTPUT=ghoutput SC_VERSION=1 ./actions/set_params_output.clj
;; $ cat ghoutput
;; params='{"sc-version":"1", ...etc...}'
;; $

(ns actions.set-params-output
  (:require [cheshire.core :as json]))

(defn index-matrix [v] (into [] (map-indexed #(assoc %2 :id %1)) v))

(defn all-params []
  (println "[debug] SC_VERSION: " (pr-str (System/getenv "SC_VERSION")))
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
                     index-matrix)
   :macos-matrix (-> [{:job-name "arm64"
                       :os-version "15"
                       :xcode-version "16.0"
                       :qt-version "6.7.3" ; will use qt from aqtinstall
                       :qt-modules "qtwebengine qtwebchannel qtwebsockets qtpositioning"
                       :deployment-target "11"
                       :cmake-architectures "arm64"
                       :homebrew-packages "libsndfile readline fftw portaudio"
                       :vcpkg-packages ""
                       :vcpkg-triplet ""
                       :extra-cmake-flags "" ; -D SC_VERIFY_APP=ON # verify app doesn't seem to work with official qt6
                       :artifact-suffix "macOS-arm64"}
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
                       :artifact-suffix "macOS-x64"}
                      {:job-name "x64 legacy"
                       :os-version "13"
                       :xcode-version "14.1"
                       :qt-version "5.15.2" # will use qt from aqtinstall
                       :qt-modules 'qtwebengine'
                       :deployment-target "10.15"
                       :cmake-architectures x86_64
                       :homebrew-packages "readline portaudio"
                       :vcpkg-packages "libsndfile fftw3"
                       :homebrew-packages "" ; use this instead when cross-compiling for x86_64 on arm64
                       :homebrew-uninstall "readline" ; use this instead when cross-compiling for x86_64 on arm64
                       :vcpkg-packages "readline portaudio libsndfile fftw3" ; use this instead when cross-compiling for x86_64 on arm64
                       :vcpkg-triplet "x64-osx-release-supercollider" ; required for build-libsndfile
                       :extra-cmake-flags ""
                       ; set if needed - will trigger artifact upload
                       :artifact-suffix "macOS-x64-legacy"}
                      {:job-name "x64 use system libraries"
                       :os-version "13"
                       :xcode-version "15.2"
                       :deployment-target ""
                       :cmake-architectures "x86_64"
                       :homebrew-packages "qt@6 libsndfile readline fftw portaudio yaml-cpp boost"
                       :vcpkg-packages ""
                       :vcpkg-triplet ""
                       :extra-cmake-flags "-D SYSTEM_BOOST=ON -D SYSTEM_YAMLCPP=ON"}
                      {:job-name "x64 shared libscsynth"
                       :os-version "13"
                       :xcode-version "15.2"
                       :deployment-target ""
                       :cmake-architectures "x86_64"
                       :homebrew-packages "qt@6 libsndfile readline fftw portaudio"
                       :vcpkg-packages ""
                       :vcpkg-triplet ""
                       :extra-cmake-flags "-D LIBSCSYNTH=ON"}])})

(defn -main []
  (let [params (all-params)]
    (println "Setting build params:")
    (println (json/encode params {:pretty true}))
    (spit (or (System/getenv "GITHUB_OUTPUT") (throw (ex-info "Must set $GITHUB_OUTPUT")))
          (str "params=" (json/encode params) "\n")
          :append true)))

(when (= *file* (System/getProperty "babashka.file"))
  (println "[debug] *command-line-args*:" (pr-str *command-line-args*))
  (apply -main *command-line-args*))
