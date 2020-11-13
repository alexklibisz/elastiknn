FROM docker.elastic.co/elasticsearch/elasticsearch:7.9.3-amd64

RUN yum -y install epel-release htop

COPY build/distributions/*.zip .
RUN elasticsearch-plugin install -b file:$(ls elastiknn*zip | sort | tail -n1)
