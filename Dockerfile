# SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
#
# SPDX-License-Identifier: Apache-2.0

FROM eclipse-temurin:17-jre-alpine

RUN apk add --update  \
  curl  \
  && rm -rf /var/cache/apk/*

RUN addgroup -g 1000 -S app
RUN adduser -u 1000 -D -H -S -G app app

USER 1000:1000

EXPOSE 8080 8082

COPY target/*.jar /usr/share/app.jar

WORKDIR /usr/share/

CMD java $JVM_OPTS -jar /usr/share/app.jar
