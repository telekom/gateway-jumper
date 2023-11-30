# SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
#
# SPDX-License-Identifier: Apache-2.0

@upstream @iris
Feature: proper authorization token reaches provider endpoint

  ################ one token ################
  Scenario: Consumer calls proxy route with real route headers, OneToken sent
    Given RealRoute headers are set
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives default bearer authorization headers
    Then API Provider receives authorization OneToken
    And API consumer receives a 200 status code

  Scenario: Consumer calls proxy route with real route headers, jc with BasicAuth for other consumer present, OneToken sent
    Given RealRoute headers are set
    And jumperConfig basic auth "other consumer present" set
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives default bearer authorization headers
    Then API Provider receives authorization OneToken
    And API consumer receives a 200 status code

  Scenario: Horizon calls proxy route with pub/sub info, OneToken contains pub/sub info
    Given RealRoute headers are set
    And pub sub contained in the header
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives authorization OneTokenWithPubSub
    And API consumer receives a 200 status code

  Scenario: Consumer calls proxy route with jc with security scopes, OneToken contains scopes
    Given RealRoute headers are set
    And jumperConfig with scopes set
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives authorization OneTokenWithScopes
    And API consumer receives a 200 status code

  Scenario: Consumer calls proxy route with iris token containing aud, OneToken contains audience claim
    Given RealRoute headers are set
    And authorization token with aud set
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives authorization OneTokenWithAud
    And API consumer receives a 200 status code

  Scenario: Consumer calls proxy route and realm header contains several values, correct issuer set in OneToken
    Given RealRoute headers are set
    And several realm fields are contained in the header
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives default bearer authorization headers
    Then API Provider receives authorization OneToken
    And API consumer receives a 200 status code

  ################ mesh ################
  Scenario: Consumer calls proxy route with proxy route headers, mesh token sent
    Given ProxyRoute headers are set
    And IDP set to provide internal token
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives default bearer authorization headers
    Then API Provider receives authorization MeshToken
    And API consumer receives a 200 status code

  ################ external ################
  Scenario: Consumer calls proxy route with jc with oauth, but client credentials not defined, consumer receives 401
    Given RealRoute headers are set
    And oauth tokenEndpoint set
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    And API consumer receives a 401 status code

  Scenario: Consumer calls proxy route with jc with oauth, external authorization token sent
    Given RealRoute headers are set
    And oauth tokenEndpoint set
    And jumperConfig oauth "default" set
    And IDP set to provide external token
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives authorization ExternalConfigured
    And API consumer receives a 200 status code

  Scenario: Consumer calls proxy route with jc with oauth, oauth credential headers set, external authorization token request uses credential from headers
    Given RealRoute headers are set
    And oauth tokenEndpoint set
    And spacegate oauth headers set
    And IDP set to provide externalHeader token
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives authorization ExternalHeader
    And API consumer receives a 200 status code

  Scenario: Consumer calls proxy route with jc with configured oauth scope, external authorization token request includes scope
    Given RealRoute headers are set
    And oauth tokenEndpoint set
    And jumperConfig oauth "scoped" set
    And IDP set to provide externalScoped token
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives authorization ExternalConfigured
    And API consumer receives a 200 status code

  Scenario: Consumer calls proxy route with jc with configured scoped oauth, oauth scoped credential headers set, external authorization token request uses scoped credentials from header
    Given RealRoute headers are set
    And oauth tokenEndpoint set
    And jumperConfig oauth "scoped" set
    And spacegate oauth scoped headers set
    And IDP set to provide externalHeaderScoped token
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives authorization ExternalHeader
    And API consumer receives a 200 status code

  Scenario: Consumer calls proxy route with jc with configured client_credentials grant type, external authorization token received with credentials provided via basic auth
    Given RealRoute headers are set
    And oauth tokenEndpoint set
    And jumperConfig oauth "grant_type client_credentials" set
    And IDP set to provide externalBasicAuthCredentials token
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives authorization ExternalConfigured
    And API consumer receives a 200 status code

  Scenario: Consumer calls proxy route with jc with configured password grant type, external authorization token received using username/password
    Given RealRoute headers are set
    And oauth tokenEndpoint set
    And jumperConfig oauth "grant_type password" set
    And IDP set to provide externalUsernamePasswordCredentials token
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives authorization ExternalConfigured
    And API consumer receives a 200 status code

  Scenario: Consumer calls proxy route with jc with configured password grant type, no client credentials provided, external authorization token received using username/password only
    Given RealRoute headers are set
    And oauth tokenEndpoint set
    And jumperConfig oauth "grant_type password only" set
    And IDP set to provide externalUsernamePasswordCredentialsOnly token
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives authorization ExternalConfigured
    And API consumer receives a 200 status code

    ################ basic auth ################
  Scenario: Consumer calls proxy route with real route headers, jc with consumer specific basic auth provided, consumer specific basic auth authorization sent
    Given RealRoute headers are set
    And API provider set to respond with a 200 status code
    And jumperConfig basic auth "consumer key only" set
    When consumer calls the proxy route
    Then API Provider receives default basic authorization headers
    Then API Provider receives authorization BasicAuthConsumer
    And API consumer receives a 200 status code

  Scenario: Consumer calls proxy route with real route headers, jc with provider specific basic auth provided, provider specific basic auth authorization sent
    Given RealRoute headers are set
    And API provider set to respond with a 200 status code
    And jumperConfig basic auth "provider key only" set
    When consumer calls the proxy route
    Then API Provider receives default basic authorization headers
    Then API Provider receives authorization BasicAuthProvider
    And API consumer receives a 200 status code

  Scenario: Consumer calls proxy route with real route headers, jc with consumer and provider specific basic auth provided, consumer specific basic auth authorization sent
    Given RealRoute headers are set
    And API provider set to respond with a 200 status code
    And jumperConfig basic auth "consumer and provider" set
    When consumer calls the proxy route
    Then API Provider receives default basic authorization headers
    Then API Provider receives authorization BasicAuthConsumer
    And API consumer receives a 200 status code