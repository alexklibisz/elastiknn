FROM elasticsearch:7.4.0

# Run bundlePlugin to get this file.
COPY es74x/build/distributions/elastiknn-0.0.0-SNAPSHOT_es7.4.0.zip .
RUN elasticsearch-plugin install -b file:elastiknn-0.0.0-SNAPSHOT_es7.4.0.zip
