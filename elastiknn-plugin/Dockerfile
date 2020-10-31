FROM docker.elastic.co/elasticsearch/elasticsearch:7.9.2-amd64

RUN yum -y install epel-release htop wget

RUN wget --no-check-certificate -c --header "Cookie: oraclelicense=accept-securebackup-cookie" https://download.oracle.com/otn-pub/java/jdk/15.0.1%2B9/51f4f36ad4ef43e39d0dfdbaf6549e32/jdk-15.0.1_linux-x64_bin.tar.gz
RUN tar xzvf jdk-15.0.1_linux-x64_bin.tar.gz
RUN rm -rf jdk
RUN mv jdk-15.0.1 jdk

COPY build/distributions/*.zip .
RUN elasticsearch-plugin install -b file:$(ls elastiknn*zip | sort | tail -n1)
