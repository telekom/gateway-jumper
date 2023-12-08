# SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
#
# SPDX-License-Identifier: Apache-2.0

@upstream
Feature: expected upstream response is propagated

  Scenario: Consumer calls jumper with real route headers, provider responds 401
    Given RealRoute headers are set
    And several realm fields are contained in the header
    And API provider set to respond with a 401 status code
    When consumer calls the proxy route
    Then API Provider receives default bearer authorization headers
    Then API Provider receives authorization OneToken
    And API consumer receives a 401 status code

  Scenario: Consumer calls jumper with real route headers, provider responds 503
    Given RealRoute headers are set
    And API provider set to respond with a 503 status code
    When consumer calls the proxy route
    Then API Provider receives authorization OneToken
    And API consumer receives a 503 status code

  Scenario: Consumer calls jumper with real route headers and provider will have a timeout
    Given RealRoute headers are set
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route and runs into timeout
    Then API consumer receives a 504 status code

  Scenario: Consumer calls jumper with real route headers and connection will be dropped
    Given RealRoute headers are set
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route and connection is dropped
    Then API consumer receives a 500 status code
