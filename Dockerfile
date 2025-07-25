# SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
#
# SPDX-License-Identifier: Apache-2.0

ARG BASE_IMAGE=eclipse-temurin:17-jre-alpine
FROM ${BASE_IMAGE}

EXPOSE 8080

COPY target/*.jar /usr/share/app.jar

CMD ["java", "-jar", "/usr/share/app.jar"]
