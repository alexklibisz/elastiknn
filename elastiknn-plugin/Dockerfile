FROM docker.elastic.co/elasticsearch/elasticsearch:7.10.0-amd64
COPY build/distributions/*.zip .
RUN elasticsearch-plugin install -b file:$(ls elastiknn*zip | sort | tail -n1)
