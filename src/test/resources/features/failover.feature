# SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
#
# SPDX-License-Identifier: Apache-2.0

@upstream @iris @failover
Feature: request containing routing_config properly handled

  ################ secondary route ################
  Scenario: Consumer calls proxy route with secondary config, proxy called
    Given Secondary routing_config header set
    And IDP set to provide internal token
    And API provider set to respond on real path
    When consumer calls the proxy route without base path
    Then API Provider receives default bearer authorization headers
    Then API Provider receives authorization MeshToken

  Scenario: Consumer calls proxy route with secondary config and skip header, provider called
    Given Secondary routing_config header set
    And skip zone header set
    And API provider set to respond on provider path
    When consumer calls the proxy route without base path
    Then API Provider receives default bearer authorization headers
    Then API Provider receives authorization OneTokenSimple

  Scenario: Consumer calls proxy route with secondary config and zone is unhealthy, provider called
    Given Secondary routing_config header set
    And set zone state to unhealthy
    And API provider set to respond on provider path
    When consumer calls the proxy route without base path
    Then API Provider receives default bearer authorization headers
    Then API Provider receives authorization OneTokenSimple

################ proxy route ################
  Scenario: Consumer calls proxy route with proxy config, real proxy called
    Given Proxy routing_config header set
    And IDP set to provide internal token
    And API provider set to respond on real path
    When consumer calls the proxy route without base path
    Then API Provider receives default bearer authorization headers
    Then API Provider receives authorization MeshToken

  Scenario: Consumer calls proxy route with proxy config and skip header, failover proxy called
    Given Proxy routing_config header set
    And skip zone header set
    And IDP set to provide internal token
    And API provider set to respond on failover path
    When consumer calls the proxy route without base path
    Then API Provider receives default bearer authorization headers
    Then API Provider receives authorization MeshToken

  Scenario: Consumer calls proxy route with proxy config and zone is unhealthy, failover proxy called
    Given Proxy routing_config header set
    And set zone state to unhealthy
    And IDP set to provide internal token
    And API provider set to respond on failover path
    When consumer calls the proxy route without base path
    Then API Provider receives default bearer authorization headers
    Then API Provider receives authorization MeshToken

################ headers ################
  Scenario: Consumer calls proxy route with secondary config, check header stripped
    Given Secondary routing_config header set
    And IDP set to provide internal token
    And API provider set to respond on real path
    When consumer calls the proxy route without base path
    Then API Provider receives no failover headers
