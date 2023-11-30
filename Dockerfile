# SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
#
# SPDX-License-Identifier: Apache-2.0

FROM azul/zulu-openjdk-alpine:17

RUN apk add --update \
    curl \
    && rm -rf /var/cache/apk/*

RUN addgroup --gid 1000 jumper && adduser --uid 1000 -G jumper -D jumper --no-create-home

USER 1000:1000

EXPOSE 8080 8082

COPY target/*.jar /usr/share/jumper.jar

WORKDIR /usr/share/

CMD java $JVM_OPTS -jar /usr/share/jumper.jar

