# SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
#
# SPDX-License-Identifier: Apache-2.0

@upstream @iris @horizon
Feature: spectre events created

  ################ spectre ################
  Scenario: Consumer calls listener route with real route headers, jc with matching route listener configured, 2 spectre events created
    Given RealRoute headers are set
    And jumperConfig with consumer route listener set
    And API provider set to respond with a 200 status code
    And horizon set to receive events
    When consumer calls the listener route
    Then API Provider receives default bearer authorization headers
    Then API Provider receives authorization OneToken
    And API consumer receives a 200 status code
    And verify 2 horizon events received
    And verify received horizon events structure for method GET

  Scenario Outline: Consumer calls listener route with real route headers and specific json contenttype, jc with matching route listener configured, 2 spectre events created
    Given RealRoute headers are set
    And jumperConfig with consumer route listener set
    And API provider set to respond with a 200 status code
    And horizon set to receive events
    And request header Content-Type is set to <content-type>
    When consumer calls the listener route with JSON body
    Then API Provider receives default bearer authorization headers
    And API consumer receives a 200 status code
    And verify 2 horizon events received
    And verify received horizon events structure for method POST
    And verify received horizon events payload
    Examples:
      | content-type                      |
      | application/json                  |
      | application/merge-patch+json      |
      | application/json-patch+json       |
      | application/json-patch-query+json |
      | application/json                  |
      | application/problem+json          |

  Scenario: Consumer calls listener route with real route headers and unspecific non-text contenttype, 2 spectre events created, spectre payload base64 encoded
    Given RealRoute headers are set
    And jumperConfig with consumer route listener set
    And API provider set to respond with a 200 status code
    And horizon set to receive events
    And request header Content-Type is set to application/unknownorbinary
    When consumer calls the listener route with JSON body
    Then API Provider receives default bearer authorization headers
    And API consumer receives a 200 status code
    And verify 2 horizon events received
    And verify received horizon events structure for method POST
    And verify received horizon events payload to be base64 encoded

  Scenario: Consumer calls listener route with real route headers, jc with not matching route listener configured, 0 spectre events created
    Given RealRoute headers are set
    And jumperConfig with otherConsumer route listener set
    And API provider set to respond with a 200 status code
    And horizon set to receive events
    When consumer calls the listener route
    Then API Provider receives default bearer authorization headers
    Then API Provider receives authorization OneToken
    And API consumer receives a 200 status code
    And verify 0 horizon events received

  Scenario: Consumer calls listener route with request containing json body, jc with matching route listener configured, 2 spectre events keep json structure
    Given RealRoute headers are set
    And jumperConfig with consumer route listener set
    And API provider set to respond with a 200 status code
    And horizon set to receive events
    And request header Content-Type is set to application/json
    When consumer calls the listener route with JSON body
    And API consumer receives a 200 status code
    And verify 2 horizon events received
    And verify received horizon events structure for method POST
    And verify received horizon events payload

  Scenario: Consumer calls listener route with real route headers, horizon not available, request processing not affected
    Given RealRoute headers are set
    And jumperConfig with consumer route listener set
    And API provider set to respond with a 200 status code
    When consumer calls the listener route
    Then API Provider receives default bearer authorization headers
    Then API Provider receives authorization OneToken
    And API consumer receives a 200 status code

  Scenario: POST request to Spectre endpoint, adjusted event on horizon endpoint
    Given Event provider set to respond with a 201 status code
    When horizon calls the spectre route with POST
    Then horizon receives a 201 status code
    And verify adjusted horizon event

  Scenario: HEAD request to Spectre endpoint
    Given Event provider set to respond with a 201 status code
    When horizon calls the spectre route with HEAD
    Then horizon receives a 201 status code
