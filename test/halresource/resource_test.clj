(ns halresource.resource-test
  (:require [midje.sweet :refer :all]
            [halresource.resource :refer :all]
            [cheshire.core :as json]
            [clojure.data.xml :as xml]))

(def resource (new-resource "http://example.org"))

(fact "new-resource sets defaults"
      (:links resource) => []
      (:embedded resource) => []
      (:properties resource) => {})

(fact "add-link adds a link to the resource"
      (add-link resource :href "/data" :rel "data")
      => (contains {:links [{:href "/data" :rel "data"}]})

      (-> resource
          (add-link :href "/data" :rel "data")
          (add-link :href "/admin" :rel "admin")
          :links)
      => [{:href "/data" :rel "data"}
          {:href "/admin" :rel "admin"}])

(fact "add-resource adds an embedded resource"
      (add-resource resource "dataset" {:href "/data/1"})
      => (contains {:embedded [["dataset" {:href "/data/1"}]]})

      (-> resource
          (add-resource "dataset" {:foo "bar"})
          (add-resource "conjurer" {:bar "baz"})
          :embedded)
      => [["dataset" {:foo "bar"}]
          ["conjurer" {:bar "baz"}]])

(fact "add-property adds one or more properties"
      (add-property resource :size 200)
      => (contains {:properties {:size 200}})

      (add-property resource :size 200 :height 12)
      => (contains {:properties {:size 200 :height 12}})

      (-> resource
          (add-property :size 200)
          (add-property :height 12 :name "Julius")
          :properties)
      => {:size 200 :height 12 :name "Julius"})

(fact "add-properties adds a map of properties"
      (add-properties resource {:size 200 :height 12})
      => (contains {:properties {:size 200 :height 12}}))

(fact "add-curie adds a link to the resource"
      (add-curie resource :href "http://example.org/doc" :name "doc")
      => (contains {:curies [{:href "http://example.org/doc" :name "doc"}]})

      (-> resource
          (add-curie :href "http://example.org/doc1" :name "doc1")
          (add-curie :href "http://example.org/doc2" :name "doc2")
          :curies)
      => [{:href "http://example.org/doc1" :name "doc1"}
          {:href "http://example.org/doc2" :name "doc2"}])

(facts "about resource->representation"
       (fact "it transforms a simple resource into JSON"
             (json/parse-string (resource->representation resource :json))
             => {"_links" {"self" {"href" "http://example.org"}}})

       (fact "it transforms a simple resource into XML"
             (resource->representation resource :xml)
             => (xml/emit-str
                 (xml/element "resource" {:href "http://example.org"})))

       (fact "it transforms a resource with multiple links into JSON"
             (let [resource (-> resource
                                (add-link :href "/admin" :rel "admin")
                                (add-link :href "/?page=2" :rel "next"))]
               (json/parse-string (resource->representation resource :json))
               => {"_links" {"self" {"href" "http://example.org"}
                             "admin" {"href" "/admin"}
                             "next" {"href" "/?page=2"}}}))

       (fact "it transforms a resource with multiple links into XML"
             (let [resource (-> resource
                                (add-link :href "/admin" :rel "admin")
                                (add-link :href "/?page=2" :rel "next"))]
               (resource->representation resource :xml)
               => (xml/emit-str
                   (xml/sexp-as-element
                    ["resource" {:href "http://example.org"}
                     ["link" {:href "/admin" :rel "admin"}]
                     ["link" {:href "/?page=2" :rel "next"}]]))))

       (fact "it transforms a resource with multiple links with same key into JSON"
             (let [resource (-> resource
                                (add-link :href "/admin" :rel "admin")
                                (add-link :href "/?page=2" :rel "next")
                                (add-link :href "/?page=3" :rel "next"))]
               (json/parse-string (resource->representation resource :json))
               => {"_links" {"self" {"href" "http://example.org"}
                             "admin" {"href" "/admin"}
                             "next" [{"href" "/?page=2"} {"href" "/?page=3"}]}}))

       (fact "it transforms a resource with multiple links with same key into XML"
             (let [resource (-> resource
                                (add-link :href "/admin" :rel "admin")
                                (add-link :href "/?page=2" :rel "next")
                                (add-link :href "/?page=3" :rel "next"))]
               (resource->representation resource :xml)
               => (xml/emit-str
                   (xml/sexp-as-element
                    ["resource" {:href "http://example.org"}
                     ["link" {:href "/admin" :rel "admin"}]
                     ["link" {:href "/?page=2" :rel "next"}]
                     ["link" {:href "/?page=3" :rel "next"}]]))))

       (fact "it transforms a resource with properties into JSON"
             (let [resource (-> resource
                                (add-property :size 200 :height 12))]
               (json/parse-string (resource->representation resource :json))
               => {"_links" {"self" {"href" "http://example.org"}}
                   "size" 200
                   "height" 12}))

       (fact "it transforms a resource with properties into XML"
             (let [resource (-> resource
                                (add-property :size 200 :height 12))
                   xml-rep (xml/parse-str (resource->representation resource :xml))]
               (:content xml-rep)
               => (just [{:tag :size :attrs {} :content ["200"]}
                         {:tag :height :attrs {} :content ["12"]}] :in-any-order)))

       (fact "it transforms a resource with curies into JSON"
             (let [resource (-> resource
                                (add-curie :href "http://example.org/doc1" :name "doc1")
                                (add-curie :href "http://example.org/doc2" :name "doc2"))]
               (json/parse-string (resource->representation resource :json))
               => {"_links" {"self" {"href" "http://example.org"}
                             "curies" [{"name" "doc1" "href" "http://example.org/doc1"}
                                       {"name" "doc2" "href" "http://example.org/doc2"}]}}))

       (fact "it transforms a resource with nested properties into XML"
             (let [resource (-> resource
                                (add-property :name {:first "Baba" :name "O'Riley"}))
                   xml-rep (xml/parse-str (resource->representation resource :xml))
                   content (:content xml-rep)]

               (-> content first :content)
               => (contains [{:tag :first
                                     :attrs {}
                                     :content ["Baba"]}
                             {:tag :name
                              :attrs {}
                              :content ["O'Riley"]}]
                            :in-any-order)))

       (fact "it transforms a resource with plural properties into XML"
             (let [resource (-> resource
                                (add-property :sizes ["S" "M" "L"]))
                   xml-rep (xml/parse-str (resource->representation resource :xml))
                   content (:content xml-rep)]

               (-> content first)
               => (contains {:tag :sizes})

               (-> content first :content)
               => (just [{:tag :size :attrs {} :content ["S"]}
                         {:tag :size :attrs {} :content ["M"]}
                         {:tag :size :attrs {} :content ["L"]}])))

       (fact "it transforms a resource with more than one singular property into XML"
             (let [resource (-> resource
                                (add-property :size ["S" "M" "L"]))
                   xml-rep (xml/parse-str (resource->representation resource :xml))
                   content (:content xml-rep)]

               (-> content)
               => (contains [{:tag :size :attrs {} :content ["S"]}
                             {:tag :size :attrs {} :content ["M"]}
                             {:tag :size :attrs {} :content ["L"]}])))       

       (fact "it transforms a resource with embedded resources into JSON"
             (let [resource (-> resource
                                (add-resource "dataset" (new-resource "/data/1"))
                                (add-resource "dataset" (new-resource "/data/2")))]
               (json/parse-string (resource->representation resource :json))
               => {"_links" {"self" {"href" "http://example.org"}}
                   "_embedded" {"datasets"
                                [{"_links" {"self" {"href" "/data/1"}}}
                                 {"_links" {"self" {"href" "/data/2"}}}]}}))

       (fact "it transforms a resource with embedded resources into XML"
             (let [resource (-> resource
                                (add-resource "dataset" (new-resource "/data/1"))
                                (add-resource "dataset" (new-resource "/data/2")))
                   xml-rep (xml/parse-str (resource->representation resource :xml))]
               (:content xml-rep)
               => (contains [{:tag :resource :attrs {:rel "dataset" :href "/data/1"} :content []}
                             {:tag :resource :attrs {:rel "dataset" :href "/data/2"} :content []}])))

       (fact "it transforms a devilishly complex resource into JSON"
             (let [resource (-> resource
                                (add-link :href "/admin" :rel "admin")
                                (add-link :href "/?page=2" :rel "next")
                                (add-resource "dataset" (-> (new-resource "/data/1")
                                                            (add-property :name "Pete")))
                                (add-resource "dataset" (-> (new-resource "/data/2")
                                                            (add-link :href "/data/3" :rel "next")))
                                (add-property :size 200 :height 12))]
               (json/parse-string (resource->representation resource :json))
               => {"_links" {"self" {"href" "http://example.org"}
                             "admin" {"href" "/admin"}
                             "next" {"href" "/?page=2"}}
                   "_embedded" {"datasets"
                                [{"_links" {"self" {"href" "/data/1"}}
                                  "name" "Pete"}
                                 {"_links" {"self" {"href" "/data/2"}
                                            "next" {"href" "/data/3"}}}]}
                   "size" 200
                   "height" 12}))

       (fact "it transforms a devilishly complex resource into XML"
             (let [resource (-> resource
                                (add-link :href "/admin" :rel "admin")
                                (add-link :href "/?page=2" :rel "next")
                                (add-resource "dataset" (-> (new-resource "/data/1")
                                                            (add-property :name "Pete")))
                                (add-resource "dataset" (-> (new-resource "/data/2")
                                                            (add-link :href "/data/3" :rel "next")))
                                (add-property :size 200 :height 12))
                   xml-rep (xml/parse-str (resource->representation resource :xml))
                   content (:content xml-rep)]
               content => (contains [{:tag :size :attrs {} :content ["200"]}
                                     {:tag :height :attrs {} :content ["12"]}]
                                    :in-any-order)

               content => (contains [(fn [e]
                                       (and (= :resource (:tag e))
                                            (= {:rel "dataset" :href "/data/1"}
                                               (:attrs e))
                                            (= "Pete"
                                               (-> e
                                                   :content
                                                   first
                                                   :content
                                                   first))))])

               content => (contains [(fn [e]
                                       (and (= :resource (:tag e))
                                            (= {:rel "dataset" :href "/data/2"} (:attrs e))
                                            (= "/data/3"
                                               (-> e
                                                   :content
                                                   first
                                                   :attrs
                                                   :href))))]))))
