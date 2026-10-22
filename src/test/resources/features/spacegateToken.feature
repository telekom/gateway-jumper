# SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
#
# SPDX-License-Identifier: Apache-2.0

@upstream @iris
Feature: X-Spacegate-Token header is added on mesh hops and counted when it reaches a provider

  Scenario: Consumer from internet facing zone calls proxy route, consumer token sent in X-Spacegate-Token, not counted
    Given ProxyRoute headers are set for a consumer from zone space
    And request header api_base_path is set to /eni/spacegate-token-mesh/v1
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives the consumer token in the X-Spacegate-Token header
    And API consumer receives a 200 status code
    And X-Spacegate-Token forwarding is not counted for base path /eni/spacegate-token-mesh/v1

  Scenario: Consumer from internal zone calls proxy route, no X-Spacegate-Token sent
    Given ProxyRoute headers are set
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives authorization MeshToken
    And API Provider does not receive header X-Spacegate-Token
    And API consumer receives a 200 status code

  Scenario: Real route forwards X-Spacegate-Token to provider, forwarding counted per API
    Given RealRoute headers are set
    And request header api_base_path is set to /eni/spacegate-token-forwarded/v1
    And A header X-Spacegate-Token is set with value dummy
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives header X-Spacegate-Token that matches regex dummy
    And API consumer receives a 200 status code
    And X-Spacegate-Token forwarding is counted 1 time for base path /eni/spacegate-token-forwarded/v1 and origin zone localZone

  Scenario: Real route removes X-Spacegate-Token via removeHeaders, forwarding not counted
    Given RealRoute headers are set
    And jumperConfig "X-Spacegate-Token" removeHeaders set
    And request header api_base_path is set to /eni/spacegate-token-removed/v1
    And A header X-Spacegate-Token is set with value dummy
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider does not receive header X-Spacegate-Token
    And API consumer receives a 200 status code
    And X-Spacegate-Token forwarding is not counted for base path /eni/spacegate-token-removed/v1
