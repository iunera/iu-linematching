FROM --platform=linux/amd64 maven:3.8.1-openjdk-11-slim as DEPS
WORKDIR /opt/app

COPY iu-transport-datatypes iu-transport-datatypes
COPY iu-flink-extensions iu-flink-extensions

RUN mvn clean install -f iu-transport-datatypes
RUN mvn clean install -f iu-flink-extensions


FROM --platform=linux/amd64 maven:3.8.1-openjdk-11-slim as BUILDER
COPY --from=DEPS /root/.m2 /root/.m2

# copy the main repo 
COPY ./iu-linematching .

# build for release
RUN mvn clean package -pl :iu-linematching
# -DskipTests=true

### Create Flink Jobmanager/Taskmanager image including the flinkjob
#FROM flink:1.9.1-scala_2.12-java8
FROM --platform=linux/amd64 flink:1.17.1-scala_2.12-java11
# FROM --platform=linux/amd64 flink:1.18.0-scala_2.12-java17
# 1.18.0-scala_2.12-java17, 
# 1.18-scala_2.12-java17, 
# scala_2.12-java17, 
# 1.18.0-java17, 
# 1.18-java17, 
# java17

RUN \
  apt-get update && \
  apt-get upgrade -y && \
  apt-get dist-upgrade -y && \
  apt-get autoclean && \
  apt-get autoremove && \
  rm -rf /var/lib/apt/lists/*

COPY --from=BUILDER target/ $FLINK_HOME/flink-web-upload/

CMD ["help"]
