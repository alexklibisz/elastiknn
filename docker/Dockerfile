FROM docker.elastic.co/elasticsearch/elasticsearch:9.3.0
COPY elastiknn-plugin/target/elastiknn*.zip .
RUN PLUGIN=$(ls elastiknn*.zip | sort | tail -n1) && elasticsearch-plugin install -b "file://$PWD/$PLUGIN"
