FROM docker.elastic.co/elasticsearch/elasticsearch:8.17.7
COPY elastiknn-plugin/target/elastiknn*.zip .
RUN elasticsearch-plugin install -b file:$(ls elastiknn*zip | sort | tail -n1)
