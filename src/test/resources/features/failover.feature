# SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
#
# SPDX-License-Identifier: Apache-2.0

@upstream @iris @failover
Feature: request containing routing_config properly handled

  ################ secondary route ################
  Scenario: Consumer calls proxy route with secondary config, proxy called
    Given Secondary routing_config header set
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
    Then API Provider receives authorization OneTokenWithoutRequestPathAssertion

  Scenario: Consumer calls proxy route with secondary config and conflicting legacy remoteApiUrl, selected routing_config target is used
    Given Secondary routing_config header set with conflicting remoteApiUrl
    And skip zone header set
    And API provider set to respond on provider path
    When consumer calls the proxy route without base path
    Then API Provider receives default bearer authorization headers
    Then API Provider receives authorization OneTokenWithoutRequestPathAssertion

  Scenario: Consumer calls proxy route with secondary config and zone is unhealthy, provider called
    Given Secondary routing_config header set
    And set zone state to unhealthy
    And API provider set to respond on provider path
    When consumer calls the proxy route without base path
    Then API Provider receives default bearer authorization headers
    Then API Provider receives authorization OneTokenWithoutRequestPathAssertion

################ proxy route ################
  Scenario: Consumer calls proxy route with proxy config, real proxy called
    Given Proxy routing_config header set
    And API provider set to respond on real path
    When consumer calls the proxy route without base path
    Then API Provider receives default bearer authorization headers
    Then API Provider receives authorization MeshToken

  Scenario: Consumer calls proxy route with proxy config and skip header, failover proxy called
    Given Proxy routing_config header set
    And skip zone header set
    And API provider set to respond on failover path
    When consumer calls the proxy route without base path
    Then API Provider receives default bearer authorization headers
    Then API Provider receives authorization MeshToken

  Scenario: Consumer calls proxy route with proxy config and zone is unhealthy, failover proxy called
    Given Proxy routing_config header set
    And set zone state to unhealthy
    And API provider set to respond on failover path
    When consumer calls the proxy route without base path
    Then API Provider receives default bearer authorization headers
    Then API Provider receives authorization MeshToken

  Scenario: Consumer calls proxy route with legacy issuer fallback, real proxy called
    Given Proxy routing_config header set with legacy issuer
    And API provider set to respond on real path
    When consumer calls the proxy route without base path
    Then API Provider receives default bearer authorization headers
    Then API Provider receives authorization MeshToken

  Scenario: Consumer calls proxy route with realm in routing_config, mesh LMS token issuer uses realm
    Given Proxy routing_config header set with routing_config realm
    And API provider set to respond on real path
    When consumer calls the proxy route without base path
    Then API Provider receives default bearer authorization headers
    Then API Provider receives authorization MeshTokenWithNonDefaultRealm

  # TODO: remove after CP phase 2 completes and the legacy realm header fallback is deleted.
  Scenario: Consumer calls proxy route with legacy non-default realm header, mesh LMS token issuer uses realm
    Given Proxy routing_config header set with legacy realm header
    And API provider set to respond on real path
    When consumer calls the proxy route without base path
    Then API Provider receives default bearer authorization headers
    Then API Provider receives authorization MeshTokenWithNonDefaultRealm

  # TODO: remove after CP phase 2 completes and the legacy issuer realm fallback is deleted.
  Scenario: Consumer calls proxy route with legacy issuer header for non-default realm, mesh LMS token issuer uses realm
    Given Proxy routing_config header set with legacy issuer for non-default realm
    And API provider set to respond on real path
    When consumer calls the proxy route without base path
    Then API Provider receives default bearer authorization headers
    Then API Provider receives authorization MeshTokenWithNonDefaultRealm

################ headers ################
  Scenario: Consumer calls proxy route with secondary config, check header stripped
    Given Secondary routing_config header set
    And API provider set to respond on real path
    When consumer calls the proxy route without base path
    Then API Provider receives no failover headers
