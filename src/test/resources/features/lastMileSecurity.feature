# SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
#
# SPDX-License-Identifier: Apache-2.0

@upstream @iris
Feature: Last Mile Security (OneToken)

  Scenario: Consumer calls an API with lastMileSecurity
    Given lastMileSecurity is activated
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API provider receives AccessToken
    And API consumer receives a 200 status code

  Scenario: Consumer calls an API with lastMileSecurity and realm header contains several values
    Given lastMileSecurity is activated
    And several realm fields are contained in the header
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API provider receives AccessToken
    And API consumer receives a 200 status code

  Scenario: Consumer calls an API with lastMileSecurity and Provider respond with a 401
    Given lastMileSecurity is activated
    And API provider set to respond with a 401 status code
    When consumer calls the proxy route
    Then API provider receives AccessToken
    And API consumer receives a 401 status code

  Scenario: Consumer calls an API with lastMileSecurity and Provider respond with a 503
    Given lastMileSecurity is activated
    And API provider set to respond with a 503 status code
    When consumer calls the proxy route
    Then API provider receives AccessToken
    And API consumer receives a 503 status code

  Scenario: Consumer calls an API with lastMileSecurity and Provider will have a timeout
    Given lastMileSecurity is activated
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route and runs into timeout
    Then API consumer receives a 504 status code

  Scenario: Consumer calls an API with lastMileSecurity and connection will be dropped
    Given lastMileSecurity is activated
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route and connection is dropped
    Then API consumer receives a 500 status code

  ################ configured aud claims ################
  Scenario: Consumer calls an API with lastMileSecurity and a configured literal aud claim, token carries the configured audience
    Given lastMileSecurity is activated
    And jumperConfig claims "aud literal" set
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API provider receives AccessToken
    And API Provider receives authorization OneTokenWithConfiguredAud
    And API consumer receives a 200 status code

  Scenario: Consumer calls an API with lastMileSecurity and a configured literal aud claim, configured audience replaces the consumer token audience
    Given lastMileSecurity is activated
    And authorization token with aud set
    And jumperConfig claims "aud literal" set
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API provider receives AccessToken
    And API Provider receives authorization OneTokenWithConfiguredAud
    And API consumer receives a 200 status code

  Scenario: Consumer calls an API with lastMileSecurity and a configured ConsumerClientId aud claim, token carries the consumer client id as audience
    Given lastMileSecurity is activated
    And jumperConfig claims "aud consumerClientId" set
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API provider receives AccessToken
    And API Provider receives authorization OneTokenWithConsumerClientIdAud
    And API consumer receives a 200 status code

  Scenario: Consumer calls an API with a configured non-aud claim, claim is ignored and consumer token audience is unchanged
    Given RealRoute headers are set
    And authorization token with aud set
    And jumperConfig claims "non-aud key" set
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives authorization OneTokenWithAud
    And API consumer receives a 200 status code
