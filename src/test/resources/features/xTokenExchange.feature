# SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
#
# SPDX-License-Identifier: Apache-2.0

@upstream @iris @xtokenexchange
Feature: proper authorization token reaches provider endpoint if x-token-exchange header set

  Scenario Outline: Consumer calls proxy route with XtokenExchange Header and currentZone space || canis || aries
    Given RealRoute headers are set with x-token-exchange
    And current zone is "<zone>"
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives authorization XTokenExchangeHeader
    Examples:
    | zone |
    | canis |
    | aries |
    | space |
    
   Scenario Outline: Consumer calls proxy route with XtokenExchange Header and currentZone aws || cetus
    Given RealRoute headers are set with x-token-exchange
    And current zone is "<zone>"
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives authorization OneToken
    Examples:
    | zone |
    | aws |
    | cetus |
  
  
    
 Scenario Outline: Consumer calls proxy route with proxy route headers with xTokenExchange header, mesh token sent
    Given ProxyRoute headers are set with x-token-exchange
    And current zone is "<zone>"
    And IDP set to provide internal token
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives default bearer authorization headers
    Then API Provider receives authorization MeshToken
    And API consumer receives a 200 status code
    Examples:
    | zone |
    | canis |
    | aries |
    | space |
    | aws |
    | cetus |
    
 Scenario Outline: Consumer calls proxy route with real route headers with xTokenExchange header and currentZone space, jc with consumer and provider specific basic auth provided, xTokenExchange sent
    Given RealRoute headers are set with x-token-exchange
    And current zone is "<zone>"
    And API provider set to respond with a 200 status code
    And jumperConfig basic auth "consumer and provider" set
    When consumer calls the proxy route
    Then API Provider receives authorization XTokenExchangeHeader
    And API consumer receives a 200 status code
    Examples:
    | zone |
    | canis |
    | aries |
    | space |
    
  Scenario Outline: Consumer calls proxy route with real route headers with xTokenExchange header and currentZone space, jc with consumer and provider specific basic auth provided, xTokenExchange sent
    Given RealRoute headers are set with x-token-exchange
    And current zone is "<zone>"
    And API provider set to respond with a 200 status code
    And jumperConfig basic auth "consumer and provider" set
    When consumer calls the proxy route
    Then API Provider receives authorization BasicAuthConsumer
    And API consumer receives a 200 status code
    Examples:
    | zone |
    | aws |
    | cetus |