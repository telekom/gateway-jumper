// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.util;

import static jumper.config.Config.*;
import static jumper.config.Config.CONSUMER;
import static jumper.model.config.JumperConfig.toBase64;

import java.util.HashMap;
import java.util.List;
import jumper.Constants;
import jumper.config.Config;
import jumper.model.config.*;

public class JumperConfigUtil {

  public static String getJcSecurity() {
    HashMap<String, OauthCredentials> oauth = new HashMap<>();
    OauthCredentials oc = new OauthCredentials();
    oc.setScopes(SCOPES);
    oauth.put(CONSUMER, oc);
    JumperConfig jc = new JumperConfig();
    jc.setOauth(oauth);
    return toBase64(jc);
  }

  public static String getJcBasicAuthConsumer(String id) {
    HashMap<String, BasicAuthCredentials> basicAuthCredentialsHashMap = new HashMap<>();
    BasicAuthCredentials ba = new BasicAuthCredentials();
    ba.setUsername(addIdSuffix(CONSUMER, id));
    ba.setPassword("password");
    basicAuthCredentialsHashMap.put(CONSUMER, ba);
    JumperConfig jc = new JumperConfig();
    jc.setBasicAuth(basicAuthCredentialsHashMap);
    return toBase64(jc);
  }

  public static String getJcBasicAuthProvider(String id) {
    HashMap<String, BasicAuthCredentials> basicAuthCredentialsHashMap = new HashMap<>();
    BasicAuthCredentials ba = new BasicAuthCredentials();
    ba.setUsername(addIdSuffix(CONSUMER_GATEWAY, id));
    ba.setPassword("geheim");
    basicAuthCredentialsHashMap.put(Constants.BASIC_AUTH_PROVIDER_KEY, ba);
    JumperConfig jc = new JumperConfig();
    jc.setBasicAuth(basicAuthCredentialsHashMap);
    return toBase64(jc);
  }

  public static String getJcBasicAuthConsumerAndProvider(String id) {
    HashMap<String, BasicAuthCredentials> basicAuthCredentialsHashMap = new HashMap<>();
    BasicAuthCredentials baConsumer = new BasicAuthCredentials();
    baConsumer.setUsername(addIdSuffix(CONSUMER, id));
    baConsumer.setPassword("password");
    BasicAuthCredentials baProvider = new BasicAuthCredentials();
    baProvider.setUsername(addIdSuffix(CONSUMER_GATEWAY, id));
    baProvider.setPassword("geheim");
    basicAuthCredentialsHashMap.put(CONSUMER, baConsumer);
    basicAuthCredentialsHashMap.put(Constants.BASIC_AUTH_PROVIDER_KEY, baProvider);
    JumperConfig jc = new JumperConfig();
    jc.setBasicAuth(basicAuthCredentialsHashMap);
    return toBase64(jc);
  }

  public static String getJcBasicAuthOtherConsumer(String id) {
    HashMap<String, BasicAuthCredentials> basicAuthCredentialsHashMap = new HashMap<>();
    BasicAuthCredentials ba = new BasicAuthCredentials();
    ba.setUsername(addIdSuffix(CONSUMER, id));
    ba.setPassword("password");
    basicAuthCredentialsHashMap.put(CONSUMER_GATEWAY, ba);
    JumperConfig jc = new JumperConfig();
    jc.setBasicAuth(basicAuthCredentialsHashMap);
    return toBase64(jc);
  }

  public static String getJcLoadBalancing() {
    LoadBalancing loadBalancing = new LoadBalancing();
    loadBalancing.setServers(
        List.of(
            new Server("http://localhost:1080", 50.0), new Server("http://localhost:1080", 50.0)));

    JumperConfig jc = new JumperConfig();
    jc.setLoadBalancing(loadBalancing);
    return toBase64(jc);
  }

  public static String getEmptyJcLoadBalancing() {
    LoadBalancing loadBalancing = new LoadBalancing();
    loadBalancing.setServers(List.of());

    JumperConfig jc = new JumperConfig();
    jc.setLoadBalancing(loadBalancing);
    return toBase64(jc);
  }

  public static String getJcRemoveHeaders(List<String> values) {
    JumperConfig jc = new JumperConfig();
    jc.setRemoveHeaders(values);
    return toBase64(jc);
  }

  public enum JcOauthConfig {
    CONSUMER,
    PROVIDER;

    private String clientKey = PRIVATE_RSA_KEY_SECURE_EXAMPLE;

    List<String> determineKeys() {
      return switch (this) {
        case CONSUMER -> List.of(Config.CONSUMER);
        case PROVIDER -> List.of(Constants.OAUTH_PROVIDER_KEY);
      };
    }

    public String getJcOauthGrantType(String id) {
      HashMap<String, OauthCredentials> oauth = new HashMap<>();
      OauthCredentials oc = new OauthCredentials();
      oc.setClientId(addIdSuffix(CONSUMER_EXTERNAL_CONFIGURED, id));
      oc.setClientSecret("secret");
      oc.setGrantType("client_credentials");
      determineKeys().forEach(key -> oauth.put(key, oc));
      JumperConfig jc = new JumperConfig();
      jc.setOauth(oauth);
      return toBase64(jc);
    }

    public String getJcOauthGrantTypePost(String id) {
      HashMap<String, OauthCredentials> oauth = new HashMap<>();
      OauthCredentials oc = new OauthCredentials();
      oc.setClientId(addIdSuffix(CONSUMER_EXTERNAL_CONFIGURED, id));
      oc.setClientSecret("secret");
      oc.setGrantType("client_credentials");
      oc.setTokenRequest("BODY");
      determineKeys().forEach(key -> oauth.put(key, oc));
      JumperConfig jc = new JumperConfig();
      jc.setOauth(oauth);
      return toBase64(jc);
    }

    public String getJcOauthGrantTypePassword(String id) {
      HashMap<String, OauthCredentials> oauth = new HashMap<>();
      OauthCredentials oc = new OauthCredentials();
      oc.setClientId(addIdSuffix(CONSUMER_EXTERNAL_CONFIGURED, id));
      oc.setClientSecret("secret");
      oc.setUsername("username");
      oc.setPassword("geheim");
      oc.setGrantType("password");
      determineKeys().forEach(key -> oauth.put(key, oc));
      JumperConfig jc = new JumperConfig();
      jc.setOauth(oauth);
      return toBase64(jc);
    }

    public String getJcOauthGrantTypePasswordOnly(String id) {
      HashMap<String, OauthCredentials> oauth = new HashMap<>();
      OauthCredentials oc = new OauthCredentials();
      oc.setUsername(addIdSuffix("username", id));
      oc.setPassword("geheim");
      oc.setGrantType("password");
      determineKeys().forEach(key -> oauth.put(key, oc));
      JumperConfig jc = new JumperConfig();
      jc.setOauth(oauth);
      return toBase64(jc);
    }

    public String getJcOauth(String id) {
      HashMap<String, OauthCredentials> oauth = new HashMap<>();
      OauthCredentials oc = new OauthCredentials();
      oc.setClientId(addIdSuffix(CONSUMER_EXTERNAL_CONFIGURED, id));
      oc.setClientSecret("secret");
      determineKeys().forEach(key -> oauth.put(key, oc));
      JumperConfig jc = new JumperConfig();
      jc.setOauth(oauth);
      return toBase64(jc);
    }

    public String getJcOauthWithScope(String id) {
      HashMap<String, OauthCredentials> oauth = new HashMap<>();
      OauthCredentials oc = new OauthCredentials();
      oc.setClientId(addIdSuffix(CONSUMER_EXTERNAL_CONFIGURED, id));
      oc.setClientSecret("secret");
      oc.setScopes(OAUTH_SCOPE_CONFIGURED);
      determineKeys().forEach(key -> oauth.put(key, oc));
      JumperConfig jc = new JumperConfig();
      jc.setOauth(oauth);
      return toBase64(jc);
    }

    public String getJcOauthGrantTypeWithKey(String id) {
      HashMap<String, OauthCredentials> oauth = new HashMap<>();
      OauthCredentials oc = new OauthCredentials();
      oc.setClientId(addIdSuffix(CONSUMER_EXTERNAL_CONFIGURED, id));
      oc.setClientKey(this.clientKey);
      oc.setGrantType("client_credentials");
      determineKeys().forEach(key -> oauth.put(key, oc));
      JumperConfig jc = new JumperConfig();
      jc.setOauth(oauth);
      return toBase64(jc);
    }

    public void setJcOauthKeyType(String clientKey) {
      this.clientKey = clientKey;
    }
  }

  public enum KeyType {
    WEAK,
    SECURE,
    INVALID,
    EMPTY;

    public String getKey() {
      return switch (this) {
        case WEAK -> PRIVATE_RSA_KEY_WEAK_EXAMPLE;
        case SECURE -> PRIVATE_RSA_KEY_SECURE_EXAMPLE;
        case INVALID -> "InvalidRSAKey";
        case EMPTY -> "";
      };
    }
  }

  public static String getJcRouteListener(String consumer) {
    HashMap<String, RouteListener> routeListenerHashMap = new HashMap<>();
    RouteListener rl = new RouteListener();
    rl.setIssue(LISTENER_ISSUE);
    rl.setServiceOwner(LISTENER_PROVIDER);
    routeListenerHashMap.put(consumer, rl);
    JumperConfig jc = new JumperConfig();
    jc.setRouteListener(routeListenerHashMap);
    GatewayClient gc = new GatewayClient();
    gc.setIssuer("realms/default");
    jc.setGatewayClient(gc);
    return toBase64(jc);
  }

  public static String addIdSuffix(String from, String id) {
    return from + "_" + id;
  }
}
