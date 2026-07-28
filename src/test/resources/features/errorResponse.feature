# SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
#
# SPDX-License-Identifier: Apache-2.0

@upstream @iris
Feature: proper error message returned based on conditions

  Scenario: Consumer calls proxy route with no headers
    Given No headers are set
    When consumer calls the proxy route
    Then API consumer receives a 500 status code
    And error response contains msg "missing routing information remote_api_url / jc.loadBalancing" error "Internal Server Error" status 500

  Scenario: Consumer calls proxy route without Authorization token
    Given RealRoute headers without Authorization are set
    When consumer calls the proxy route
    Then API consumer receives a 500 status code
    And error response contains msg "Consumer token not provided, but expected" error "Internal Server Error" status 500

  Scenario: mesh IDP drops connection
    Given ProxyRoute headers are set
    And IDP set to drop connection
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    And API consumer receives a 401 status code
    And error response contains msg "Failed to connect to http://localhost:1081/auth/realms/default/protocol/openid-connect/token, cause: Connection prematurely closed BEFORE response" error "Unauthorized" status 401

  Scenario: Consumer calls proxy route with jc with oauth, oauth wrong credential headers set
    Given RealRoute headers are set
    And oauth tokenEndpoint set
    And spacegate oauth headers set
    And IDP set to provide externalInvalidAuth token
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    And API consumer receives a 401 status code
    And error response contains msg "Failed to retrieve token from http://localhost:1081/external, original status: 401 UNAUTHORIZED" error "Unauthorized" status 401

################ external IDP -  http codes ################
  Scenario Outline: external IDP answers with error http codes jc with oauth
    Given RealRoute headers are set
    And oauth tokenEndpoint set
    And jumperConfig oauth <jumper_config> set
    And IDP set to respond with <idp_response_code> status code
    And IDP set to provide <idp_token_type> token
    And API provider set to respond with a 204 status code
    When consumer calls the proxy route
    And API consumer receives a <api_receiver_code> status code
    And error response contains msg <msg_content> error <error> status <error_code>

    Examples:
    | jumper_config | idp_response_code | idp_token_type | api_receiver_code | msg_content | error | error_code |
    # default
    | "default" | 401 | external | 401 | "Failed to retrieve token from http://localhost:1081/external, original status: 401 UNAUTHORIZED" | "Unauthorized" | 401 |
    | "default" | 403 | external | 401 | "Failed to retrieve token from http://localhost:1081/external, original status: 403 FORBIDDEN" | "Unauthorized" | 401 |
    | "default" | 404 | external | 401 | "Failed to retrieve token from http://localhost:1081/external, original status: 404 NOT_FOUND" | "Unauthorized" | 401 |
    | "default" | 405 | external | 401 | "Failed to retrieve token from http://localhost:1081/external, original status: 405 METHOD_NOT_ALLOWED" | "Unauthorized" | 401 |
    | "default" | 406 | external | 401 | "Failed to retrieve token from http://localhost:1081/external, original status: 406 NOT_ACCEPTABLE" | "Unauthorized" | 401 |
    | "default" | 408 | external | 401 | "Failed to retrieve token from http://localhost:1081/external, original status: 408 REQUEST_TIMEOUT" | "Unauthorized" | 401 |
    | "default" | 502 | external | 401 | "Failed to retrieve token from http://localhost:1081/external, original status: 502 BAD_GATEWAY" | "Unauthorized" | 401 |
    | "default" | 503 | external | 401 | "Failed to retrieve token from http://localhost:1081/external, original status: 503 SERVICE_UNAVAILABLE" | "Unauthorized" | 401 |
    # scoped
    | "scoped" | 401 | externalScoped | 401 | "Failed to retrieve token from http://localhost:1081/external, original status: 401 UNAUTHORIZED" | "Unauthorized" | 401 |
    | "scoped" | 404 | externalScoped | 401 | "Failed to retrieve token from http://localhost:1081/external, original status: 404 NOT_FOUND" | "Unauthorized" | 401 |
    | "scoped" | 503 | externalScoped | 401 | "Failed to retrieve token from http://localhost:1081/external, original status: 503 SERVICE_UNAVAILABLE" | "Unauthorized" | 401 |
    # grant_type client_credentials
    | "consumer grant_type client_credentials" | 401 | externalBasicAuthCredentials | 401 | "Failed to retrieve token from http://localhost:1081/external, original status: 401 UNAUTHORIZED" | "Unauthorized" | 401 |
    | "consumer grant_type client_credentials" | 404 | externalBasicAuthCredentials | 401 | "Failed to retrieve token from http://localhost:1081/external, original status: 404 NOT_FOUND" | "Unauthorized" | 401 |
    | "consumer grant_type client_credentials" | 503 | externalBasicAuthCredentials | 401 | "Failed to retrieve token from http://localhost:1081/external, original status: 503 SERVICE_UNAVAILABLE" | "Unauthorized" | 401 |
    # grant_type password
    | "consumer grant_type password" | 401 | externalUsernamePasswordCredentials | 401 | "Failed to retrieve token from http://localhost:1081/external, original status: 401 UNAUTHORIZED" | "Unauthorized" | 401 |
    | "consumer grant_type password" | 404 | externalUsernamePasswordCredentials | 401 | "Failed to retrieve token from http://localhost:1081/external, original status: 404 NOT_FOUND" | "Unauthorized" | 401 |
    | "consumer grant_type password" | 503 | externalUsernamePasswordCredentials | 401 | "Failed to retrieve token from http://localhost:1081/external, original status: 503 SERVICE_UNAVAILABLE" | "Unauthorized" | 401 |
    # grant_type password only
    | "consumer grant_type password only" | 401 | externalUsernamePasswordCredentialsOnly | 401 | "Failed to retrieve token from http://localhost:1081/external, original status: 401 UNAUTHORIZED" | "Unauthorized" | 401 |
    | "consumer grant_type password only" | 404 | externalUsernamePasswordCredentialsOnly | 401 | "Failed to retrieve token from http://localhost:1081/external, original status: 404 NOT_FOUND" | "Unauthorized" | 401 |
    | "consumer grant_type password only" | 503 | externalUsernamePasswordCredentialsOnly | 401 | "Failed to retrieve token from http://localhost:1081/external, original status: 503 SERVICE_UNAVAILABLE" | "Unauthorized" | 401 |

  Scenario Outline:  external IDP answers with error http codes jc with oauth, spacegate
    Given RealRoute headers are set
    And oauth tokenEndpoint set
    And spacegate oauth headers set
    And jumperConfig oauth <jumper_config> set
    And IDP set to respond with <idp_response_code> status code
    And IDP set to provide <idp_token_type> token
    And API provider set to respond with a 204 status code
    When consumer calls the proxy route
    And API consumer receives a <api_receiver_code> status code
    And error response contains msg <msg_content> error <error> status <error_code>

    Examples:
      | jumper_config | idp_response_code | idp_token_type | api_receiver_code | msg_content | error | error_code |
    # Spacegate Default
      | "default" | 401 | externalHeader | 401 | "Failed to retrieve token from http://localhost:1081/external, original status: 401 UNAUTHORIZED" | "Unauthorized" | 401 |
      | "default" | 404 | externalHeader | 401 | "Failed to retrieve token from http://localhost:1081/external, original status: 404 NOT_FOUND" | "Unauthorized" | 401 |
      | "default" | 503 | externalHeader | 401 | "Failed to retrieve token from http://localhost:1081/external, original status: 503 SERVICE_UNAVAILABLE" | "Unauthorized" | 401 |

  Scenario Outline:  external IDP answers with error http codes jc with oauth, spacegate [scoped]
    Given RealRoute headers are set
    And oauth tokenEndpoint set
    And spacegate oauth scoped headers set
    And jumperConfig oauth <jumper_config> set
    And IDP set to respond with <idp_response_code> status code
    And IDP set to provide <idp_token_type> token
    And API provider set to respond with a 204 status code
    When consumer calls the proxy route
    And API consumer receives a <api_receiver_code> status code
    And error response contains msg <msg_content> error <error> status <error_code>

    Examples:
      | jumper_config | idp_response_code | idp_token_type | api_receiver_code | msg_content | error | error_code |
    # Spacegate with Scope
      | "scoped" | 401 | externalHeaderScoped | 401 | "Failed to retrieve token from http://localhost:1081/external, original status: 401 UNAUTHORIZED" | "Unauthorized" | 401 |
      | "scoped" | 404 | externalHeaderScoped | 401 | "Failed to retrieve token from http://localhost:1081/external, original status: 404 NOT_FOUND" | "Unauthorized" | 401 |
      | "scoped" | 503 | externalHeaderScoped | 401 | "Failed to retrieve token from http://localhost:1081/external, original status: 503 SERVICE_UNAVAILABLE" | "Unauthorized" | 401 |

################ external IDP -  empty responses ################
  Scenario Outline: external IDP answers with empty http response jc with oauth
    Given RealRoute headers are set
    And oauth tokenEndpoint set
    And jumperConfig oauth <jumper_config> set
    And external token IDP set to provide empty <http_element>
    And IDP set to respond with 200 status code
    And API provider set to respond with a 204 status code
    When consumer calls the proxy route
    And API consumer receives a <api_receiver_code> status code
    And error response contains msg <msg_content> error <error> status <error_code>

    Examples:
      | jumper_config | http_element  | api_receiver_code | msg_content | error | error_code |
    # default
      | "default" | body | 406 |  "Empty response while fetching token from http://localhost:1081/external" | "Not Acceptable" | 406 |
      | "default" | header | 406 | "Failed while fetching token from http://localhost:1081/external: Content type 'application/octet-stream' not supported for TokenInfo" | "Not Acceptable" | 406 |
      | "default" | both  | 406 | "Empty response while fetching token from http://localhost:1081/external" | "Not Acceptable" | 406 |

################ external IDP - timeout connection ################
  Scenario: external IDP timeout jc with oauth
    Given RealRoute headers are set
    And oauth tokenEndpoint set
    And jumperConfig oauth "default" set
    And external token IDP request set to timeout
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route with idp timeout
    And API consumer receives a 504 status code
    And error response contains msg "Timeout occurred while fetching token from http://localhost:1081/external" error "Gateway Timeout" status 504

################ external IDP - drop connection ################
  Scenario: external IDP drop connection
    Given RealRoute headers are set
    And oauth tokenEndpoint set
    And jumperConfig oauth "default" set
    And external IDP set to drop connection
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    And API consumer receives a 401 status code
    And error response contains msg "Failed to connect to http://localhost:1081/external, cause: Connection prematurely closed BEFORE response" error "Unauthorized" status 401

################ external IDP - jwt authorization ################
  Scenario: external IDP weak key configured
    Given RealRoute headers are set
    And oauth tokenEndpoint set
    And jumperConfig set with key type "weak"
    And jumperConfig oauth "provider grant_type key" set
    And IDP set to provide externalKey token
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    And API consumer receives a 401 status code
    And error response contains msg "Key is too weak: The JWT JWA Specification (RFC 7518, Section 3.3) states that keys used with RS256 MUST have a size >= 2048 bits." error "Unauthorized" status 401

  Scenario: external IDP invalid key scheme configures
    Given RealRoute headers are set
    And oauth tokenEndpoint set
    And jumperConfig set with key type "invalid"
    And jumperConfig oauth "provider grant_type key" set
    And IDP set to provide externalKey token
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    And API consumer receives a 401 status code
    And error response contains msg "Invalid key configuration: Last unit does not have enough valid bits" error "Unauthorized" status 401


  Scenario: external IDP empty key scheme configures
    Given RealRoute headers are set
    And oauth tokenEndpoint set
    And jumperConfig set with key type "empty"
    And jumperConfig oauth "provider grant_type key" set
    And IDP set to provide externalInvalidAuth token
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    And API consumer receives a 401 status code
    And error response contains msg "Failed to retrieve token from http://localhost:1081/external, original status: 404 NOT_FOUND" error "Unauthorized" status 401