# SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
#
# SPDX-License-Identifier: Apache-2.0

ARG BASE_IMAGE=gcr.io/distroless/java17-debian12
FROM ${BASE_IMAGE}

EXPOSE 8080

COPY target/*.jar /usr/share/app.jar

ENTRYPOINT ["/usr/bin/java", "-jar"]
CMD ["/usr/share/app.jar"]
