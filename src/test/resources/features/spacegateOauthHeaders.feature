# SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
#
# SPDX-License-Identifier: Apache-2.0

@upstream @iris
Feature: X-Spacegate OAuth headers are counted when the legacy external IdP flow uses them

  Scenario: Legacy external IdP flow uses client credential headers, usage counted per API and consumer
    Given RealRoute headers are set
    And request header api_base_path is set to /eni/spacegate-oauth-credentials/v1
    And oauth tokenEndpoint set
    And spacegate oauth headers set
    And IDP set to provide externalHeader token
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives authorization ExternalHeader
    And API Provider does not receive header X-Spacegate-Client-ID
    And API Provider does not receive header X-Spacegate-Client-Secret
    And API consumer receives a 200 status code
    And X-Spacegate OAuth headers client_id,client_secret are counted 1 time for base path /eni/spacegate-oauth-credentials/v1 and consumer eni--local-team--local-app

  Scenario: Legacy external IdP flow uses client credential and scope headers, scope included in usage
    Given RealRoute headers are set
    And request header api_base_path is set to /eni/spacegate-oauth-scoped/v1
    And oauth tokenEndpoint set
    And jumperConfig oauth "scoped" set
    And spacegate oauth scoped headers set
    And IDP set to provide externalHeaderScoped token
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives authorization ExternalHeader
    And API Provider does not receive header X-Spacegate-Scope
    And API consumer receives a 200 status code
    And X-Spacegate OAuth headers client_id,client_secret,scope are counted 1 time for base path /eni/spacegate-oauth-scoped/v1 and consumer eni--local-team--local-app

  Scenario: Legacy external IdP flow without X-Spacegate OAuth headers, not counted
    Given RealRoute headers are set
    And request header api_base_path is set to /eni/spacegate-oauth-none/v1
    And oauth tokenEndpoint set
    And jumperConfig oauth "default" set
    And IDP set to provide external token
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives authorization ExternalConfigured
    And API consumer receives a 200 status code
    And X-Spacegate OAuth headers are not counted for base path /eni/spacegate-oauth-none/v1

  Scenario: Legacy external IdP flow with blank X-Spacegate OAuth headers, headers ignored and not counted
    Given RealRoute headers are set
    And request header api_base_path is set to /eni/spacegate-oauth-blank/v1
    And oauth tokenEndpoint set
    And jumperConfig oauth "default" set
    And A header X-Spacegate-Client-ID is set to a blank value
    And A header X-Spacegate-Client-Secret is set to a blank value
    And A header X-Spacegate-Scope is set to a blank value
    And IDP set to provide external token
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives authorization ExternalConfigured
    And API consumer receives a 200 status code
    And X-Spacegate OAuth headers are not counted for base path /eni/spacegate-oauth-blank/v1

  Scenario: External IdP flow with grant type ignores X-Spacegate OAuth headers, not counted
    Given RealRoute headers are set
    And request header api_base_path is set to /eni/spacegate-oauth-grant-type/v1
    And oauth tokenEndpoint set
    And jumperConfig oauth "provider grant_type client_credentials" set
    And spacegate oauth headers set
    And IDP set to provide externalBasicAuthCredentials token
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives authorization ExternalConfigured
    And API consumer receives a 200 status code
    And X-Spacegate OAuth headers are not counted for base path /eni/spacegate-oauth-grant-type/v1

  Scenario: Mesh hop forwards X-Spacegate OAuth headers to the provider zone, not counted
    Given ProxyRoute headers are set
    And request header api_base_path is set to /eni/spacegate-oauth-mesh/v1
    And IDP set to provide internal token
    And spacegate oauth headers set
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives authorization MeshToken
    And API consumer receives a 200 status code
    And X-Spacegate OAuth headers are not counted for base path /eni/spacegate-oauth-mesh/v1
