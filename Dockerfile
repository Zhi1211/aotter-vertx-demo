FROM gradle:latest AS build
USER root
ENV APP_HOME=/usr/app/
WORKDIR $APP_HOME
COPY build.gradle.kts settings.gradle.kts $APP_HOME
COPY for-test.json for-test.json $APP_HOME
COPY src $APP_HOME/src
ARG VERTICLE
RUN gradle clean assemble -Pargs=${VERTICLE} --stacktrace

FROM openjdk:11.0.9.1-jre
COPY --from=build /usr/app/build/libs/monitor-1.0.0-SNAPSHOT-fat.jar /srv/app.jar
ENTRYPOINT ["sh", "-c"]
CMD ["java -jar /srv/app.jar -cluster"]
