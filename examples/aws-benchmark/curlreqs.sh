#!/bin/sh
set -e

ES=localhost:9200
IX=odknn-hello-world

curl -XDELETE $ES/$IX?pretty

curl -XPUT $ES/$IX?pretty -H 'Content-Type: application/json' -d'
{
  "settings": {
    "index": {
      "knn": true
    }
  },
  "mappings": {
    "properties": {
      "myvec": {
        "type": "knn_vector",
	"dimension": 4
      }
    }
  }
}
'

curl -XPOST $ES/$IX/_doc?refresh=true\&pretty -H 'Content-Type: application/json' -d'
{
  "myvec": [1,2,3,4]
}
'

curl -XPOST $ES/$IX/_doc?refresh=true\&pretty -H 'Content-Type: application/json' -d'
{
  "myvec": [1,2,3,5]
}
'

curl -XGET $ES/$IX/_search?pretty

curl -XGET $ES/$IX/_search?pretty -H 'Content-Type: application/json' -d'
{
  "size": 10,
  "query": {
    "knn": {
      "myvec": {
        "vector": [0,1,2,3], 
	"k": 2
      }
    }
  }
}
'
