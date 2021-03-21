FROM docker.elastic.co/elasticsearch/elasticsearch:7.7.1
COPY elastiknn-0.1.0-PRE45_es7.7.1.zip .
RUN elasticsearch-plugin install -b file:$(ls elastiknn*zip | sort | tail -n1)
