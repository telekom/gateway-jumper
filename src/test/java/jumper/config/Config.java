// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.config;

public class Config {
  public static final String CONSUMER = "eni--local-team--local-app";
  public static final String CONSUMER_GATEWAY = "gateway";
  public static final String CONSUMER_EXTERNAL_CONFIGURED = "external_configured";
  public static final String CONSUMER_EXTERNAL_HEADER = "external_header";
  public static final String SCOPES = "scope1 scope2";
  public static final String OAUTH_SCOPE_CONFIGURED = "scope_configured";
  public static final String OAUTH_SCOPE_HEADER = "scope_header";
  public static final String ORIGIN_STARGATE = "https://zone.local.de";
  public static final String ORIGIN_STARGATE_REMOTE = "https://zone.remote.de";
  public static final String ORIGIN_ZONE = "localZone";
  public static final String ORIGIN_ZONE_REMOTE = "remoteZone";
  public static final String ENVIRONMENT = "localEnv";
  public static final String ENVIRONMENT_REMOTE = "remoteEnv";
  public static final String REALM = "default";
  public static final String BASE_PATH = "/eni/test/v1";
  public static final String LOCAL_ISSUER = "https://iris.local:1234/auth/realms/default";
  public static final String REMOTE_ISSUER = "https://iris.remote:1234/auth/realms/default";
  public static final String CALLBACK_SUFFIX = "/callback";
  public static final String PUBSUB_PUBLISHER = "testPublisher";
  public static final String PUBSUB_SUBSCRIBER = "testSubscriber";
  public static final String LISTENER_ISSUE = "issue";
  public static final String LISTENER_PROVIDER = "serviceOwner";
  public static final int REMOTE_HOST_PORT = 1080;
  public static final String REMOTE_BASE_PATH = "/real";
  public static final String REMOTE_FAILOVER_BASE_PATH = "/failover";
  public static final String REMOTE_PROVIDER_BASE_PATH = "/provider";
  public static final String REMOTE_HOST = "http://localhost:" + REMOTE_HOST_PORT;
  public static final String REMOTE_ZONE_NAME = "realZone";
  public static final String REMOTE_FAILOVER_ZONE_NAME = "failoverZone";
}
