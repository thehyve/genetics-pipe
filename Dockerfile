FROM eclipse-temurin:11-jdk as build_section

ENV SBT_VERSION 1.5.3

# Install sbt
RUN apt-get update && \
    apt-get install --yes git unzip && \
    wget https://github.com/sbt/sbt/releases/download/v$SBT_VERSION/sbt-$SBT_VERSION.zip && \
    unzip sbt-$SBT_VERSION.zip
ENV PATH=/sbt/bin:$PATH
RUN mkdir /sbt/install && cd /sbt/install && sbt sbtVersion

# Pull sbt and all dependencies first
COPY project /pipe/project
COPY build.sbt /pipe/
WORKDIR /pipe
RUN sbt update

# Assemble the jar file
COPY ./ /pipe/
RUN sbt scalafmtCheckAll && \
    sbt clean compile && \
    sbt assembly


FROM eclipse-temurin:11-jdk
COPY --from=build_section /pipe/target/scala-*/etl-genetics-*.jar /etl-genetics.jar
CMD ["java", "-jar", "etl-genetics.jar"]
