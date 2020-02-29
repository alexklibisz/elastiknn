FROM docker.elastic.co/elasticsearch/elasticsearch:7.4.0

# TODO add visualvm options here.

COPY build/distributions/*.zip .
RUN elasticsearch-plugin install -b file:$(ls elastiknn*zip | sort | tail -n1)
