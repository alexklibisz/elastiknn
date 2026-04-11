FROM docker.elastic.co/elasticsearch/elasticsearch:9.0.0
COPY elastiknn-plugin/target/elastiknn*.zip .
RUN elasticsearch-plugin install -b file:$(ls elastiknn*zip | sort | tail -n1)
