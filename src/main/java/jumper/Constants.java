// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Constants {

  public static final String HEADER_X_SPACEGATE_CLIENT_ID = "X-Spacegate-Client-ID";
  public static final String HEADER_X_SPACEGATE_CLIENT_SECRET = "X-Spacegate-Client-Secret";
  public static final String HEADER_X_SPACEGATE_SCOPE = "X-Spacegate-Scope";
  public static final String HEADER_JUMPER_CONFIG = "jumper_config";
  public static final String HEADER_ROUTING_CONFIG = "routing_config";

  public static final String HEADER_ISSUER = "issuer";
  public static final String HEADER_TOKEN_ENDPOINT = "token_endpoint";
  public static final String HEADER_CLIENT_ID = "client_id";
  public static final String HEADER_CLIENT_SECRET = "client_secret";
  public static final String HEADER_CLIENT_SCOPES = "scopes";
  public static final String HEADER_CONSUMER_TOKEN = "consumer-token";
  public static final String HEADER_GATEWAY_TOKEN = "gateway_token";
  public static final String HEADER_LASTMILE_SECURITY_TOKEN = "X-Gateway-Token";
  public static final String HEADER_SAP_LASTMILE_SECURITY_TOKEN = "X-SAP-Gateway-Token";
  public static final String HEADER_AUTHORIZATION = "Authorization";
  public static final String HEADER_REMOTE_API_URL = "remote_api_url";
  public static final String HEADER_ACCESS_TOKEN_FORWARDING = "access_token_forwarding";
  public static final String HEADER_REALM = "realm";
  public static final String HEADER_ENVIRONMENT = "environment";
  public static final String HEADER_DEBUG_RESPONSE_HEADER = "X-Tardis-Debug";
  public static final String HEADER_X_ORIGIN_STARGATE = "X-Origin-Stargate";
  public static final String HEADER_X_ORIGIN_ZONE = "X-Origin-Zone";
  public static final String HEADER_X_B3_TRACE_ID = "x-b3-traceid";
  public static final String HEADER_X_B3_SPAN_ID = "X-B3-SpanId";
  public static final String HEADER_X_B3_PARENT_SPAN_ID = "X-B3-ParentSpanId";
  public static final String HEADER_X_B3_SAMPLED = "X-B3-Sampled";
  public static final String HEADER_X_TARDIS_TRACE_ID = "x-tardis-traceid";
  public static final String HEADER_X_BUSINESS_CONTEXT = "x-business-context";
  public static final String HEADER_X_REQUEST_ID = "x-request-id";
  public static final String HEADER_X_CORRELATION_ID = "x-correlation-id";
  public static final String HEADER_X_PUBSUB_PUBLISHER_ID = "x-pubsub-publisher-id";
  public static final String HEADER_X_PUBSUB_SUBSCRIBER_ID = "x-pubsub-subscriber-id";
  public static final String HEADER_B3 = "b3";
  public static final String HEADER_X_SPACEGATE_TOKEN = "X-Spacegate-Token";
  public static final String HEADER_X_TOKEN_EXCHANGE = "X-Token-Exchange";
  public static final String HEADER_API_BASE_PATH = "api_base_path";
  public static final String HEADER_X_FORWARDED_HOST = "X-Forwarded-Host";
  public static final String HEADER_X_FORWARDED_PORT = "X-Forwarded-Port";
  public static final String HEADER_X_FORWARDED_PROTO = "X-Forwarded-Proto";
  public static final String HEADER_X_FORWARDED_PORT_PORT = "443";
  public static final String HEADER_X_FORWARDED_PROTO_HTTPS = "https";

  public static final String HEADER_X_SPECTRE_ISSUE = "x-spectre-issue";
  public static final String HEADER_X_SPECTRE_PROVIDER = "x-spectre-provider";
  public static final String HEADER_X_SPECTRE_CONSUMER = "x-spectre-consumer";

  public static final String HEADER_X_FAILOVER_SKIP_ZONE = "x-failover-skip-zone";

  public static final String QUERY_PARAM_LISTENER = "listener";
  public static final String LISTENER_ROOT_PATH_PREFIX = "/listener";
  public static final String PROXY_ROOT_PATH_PREFIX = "/proxy";
  public static final String AUTOEVENT_ROOT_PATH_PREFIX = "/autoevent";

  public static final String ISSUER_SUFFIX = "/protocol/openid-connect/token";
  public static final String LOCALHOST_ISSUER_SERVICE = "http://localhost:8081/api/v1";
  public static final String DEFAULT_REALM = "default";
  public static final String BEARER = "Bearer";
  public static final String BASIC = "Basic";

  public static final String TOKEN_REQUEST_PARAMETER_SCOPE = "scope";
  public static final String TOKEN_REQUEST_PARAMETER_CLIENT_ID = "client_id";
  public static final String TOKEN_REQUEST_PARAMETER_CLIENT_SECRET = "client_secret";
  public static final String TOKEN_REQUEST_PARAMETER_CLIENT_ASSERTION = "client_assertion";
  public static final String TOKEN_REQUEST_PARAMETER_CLIENT_ASSERTION_TYPE =
      "client_assertion_type";
  public static final String TOKEN_REQUEST_PARAMETER_CLIENT_ASSERTION_TYPE_JWT =
      "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";
  public static final String TOKEN_REQUEST_PARAMETER_GRANT_TYPE = "grant_type";
  public static final String TOKEN_REQUEST_PARAMETER_USERNAME = "username";
  public static final String TOKEN_REQUEST_PARAMETER_PASSWORD = "password";
  public static final String TOKEN_REQUEST_PARAMETER_REFRESH_TOKEN = "refresh_token";
  // client_secret_post
  public static final String TOKEN_REQUEST_METHOD_POST = "body";

  public static final String TOKEN_CLAIM_CLIENT_ID = "clientId";
  public static final String TOKEN_CLAIM_ORIGIN_STARGATE = "originStargate";
  public static final String TOKEN_CLAIM_ORIGIN_ZONE = "originZone";
  public static final String TOKEN_CLAIM_SCOPE = "scope";
  public static final String TOKEN_CLAIM_SUB = "sub";
  public static final String TOKEN_CLAIM_ISS = "iss";
  public static final String TOKEN_CLAIM_JTI = "jti";
  public static final String TOKEN_CLAIM_AUD = "aud";
  public static final String TOKEN_CLAIM_TYP = "typ";
  public static final String TOKEN_CLAIM_AZP = "azp";
  public static final String TOKEN_CLAIM_EXP = "exp";
  public static final String TOKEN_CLAIM_IAT = "iat";
  public static final String TOKEN_CLAIM_OPERATION = "operation";
  public static final String TOKEN_CLAIM_REQUEST_PATH = "requestPath";
  public static final String TOKEN_CLAIM_ACCESS_TOKEN_SIGNATURE = "accessTokenSignature";
  public static final String TOKEN_CLAIM_ACCESS_TOKEN_ENVIRONMENT = "env";
  public static final String TOKEN_CLAIM_ACCESS_TOKEN_PUBLISHER_ID = "publisherId";
  public static final String TOKEN_CLAIM_ACCESS_TOKEN_SUBSCRIBER_ID = "subscriberId";

  public static final String BASIC_AUTH_PROVIDER_KEY = "default";
  public static final String OAUTH_PROVIDER_KEY = "default";

  public static final String ENVIRONMENT_PLACEHOLDER = "ENVIRONMENT_PLACEHOLDER";
}
