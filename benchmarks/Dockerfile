FROM openjdk:12
COPY build/libs/benchmarks-*-all.jar benchmarks.jar
ENTRYPOINT ["java", "-cp", "benchmarks.jar"]