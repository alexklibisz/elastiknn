#!/bin/bash

while true;
do
  for c in $(docker ps -q);
  do
    echo "$c"
    mkdir -p /tmp/ann-benchmarks-$c
    for f in $(docker exec $c ls /var/log/elasticsearch);
    do
      docker exec $c cat /var/log/elasticsearch/$f > /tmp/ann-benchmarks-$c/$f
    done
  done
  sleep 1
done