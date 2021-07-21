FROM openjdk:11-jdk-stretch as build_section

ENV SBT_VERSION 1.5.3

# Install sbt
RUN wget https://github.com/sbt/sbt/releases/download/v$SBT_VERSION/sbt-$SBT_VERSION.zip && \
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
RUN sbt assembly

FROM openjdk:11-jdk-stretch

# Create non-root user
ARG UID
ARG GID
RUN groupadd -g $GID -o otg
RUN useradd -m -u $UID -g $GID -o -s /bin/bash otg

# switch to otg user
USER otg

# copy assembled workflow
COPY --from=build_section /pipe/target/scala-*/ot-geckopipe-assembly-*.jar /ot-geckopipe.jar

# default command
CMD ["java", "-jar", "ot-geckopipe.jar"]
