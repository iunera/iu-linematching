FROM --platform=linux/amd64 maven:3.8.1-openjdk-11-slim as BUILDER
WORKDIR /opt/app

COPY . .
RUN mvn clean package

### Create Flink Jobmanager/Taskmanager image including the flinkjob
FROM --platform=linux/amd64 flink:1.17.1-scala_2.12-java11

RUN \
  apt-get update && \
  apt-get upgrade -y && \
  apt-get dist-upgrade -y && \
  apt-get autoclean && \
  apt-get autoremove && \
  rm -rf /var/lib/apt/lists/*

COPY --from=BUILDER target/ $FLINK_HOME/flink-web-upload/

CMD ["help"]
