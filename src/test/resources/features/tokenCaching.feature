# SPDX-FileCopyrightText: 2025 Deutsche Telekom AG
#
# SPDX-License-Identifier: Apache-2.0

@upstream @iris
Feature: Token caching for external OAuth tokens

  Scenario: Token without expires_in is cached and reused across multiple calls
    Given RealRoute headers are set
    And oauth tokenEndpoint set
    And jumperConfig oauth "consumer grant_type client_credentials" set
    And IDP set to provide token without expires_in
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives authorization ExternalConfigured
    And API consumer receives a 200 status code
    When consumer calls the proxy route again
    Then API Provider receives authorization ExternalConfigured
    And API consumer receives a 200 status code
    And IDP token endpoint was called exactly 1 times

  Scenario: Token is evicted from cache on 4xx upstream response
    Given RealRoute headers are set
    And oauth tokenEndpoint set
    And jumperConfig oauth "consumer grant_type client_credentials" set
    And IDP set to provide token without expires_in allowing multiple calls
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives authorization ExternalConfigured
    And API consumer receives a 200 status code
    And IDP token endpoint was called exactly 1 times
    Given API provider set to respond with a 401 status code
    When consumer calls the proxy route again
    Then API consumer receives a 401 status code
    Given API provider set to respond with a 200 status code
    When consumer calls the proxy route again
    Then API Provider receives authorization ExternalConfigured
    And API consumer receives a 200 status code
    And IDP token endpoint was called exactly 2 times

  Scenario: Token with expires_in behaves normally
    Given RealRoute headers are set
    And oauth tokenEndpoint set
    And jumperConfig oauth "consumer grant_type client_credentials" set
    And IDP set to provide externalBasicAuthCredentials token
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives authorization ExternalConfigured
    And API consumer receives a 200 status code
