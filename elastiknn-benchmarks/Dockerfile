FROM adoptopenjdk:openj9
COPY build/libs/benchmarks-*-all.jar benchmarks.jar
ENTRYPOINT ["java", "-cp", "benchmarks.jar"]
