FROM docker.elastic.co/elasticsearch/elasticsearch:7.17.8-amd64
COPY elastiknn-plugin/target/elastiknn*.zip .
RUN elasticsearch-plugin install -b file:$(ls elastiknn*zip | sort | tail -n1)
