package jumper.util;

import static jumper.config.Config.*;
import static jumper.model.config.JumperConfig.toBase64;

import java.util.HashMap;
import jumper.Constants;
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

  public static String getJcOauth(String id) {
    HashMap<String, OauthCredentials> oauth = new HashMap<>();
    OauthCredentials oc = new OauthCredentials();
    oc.setClientId(addIdSuffix(CONSUMER_EXTERNAL_CONFIGURED, id));
    oc.setClientSecret("secret");
    oauth.put(CONSUMER, oc);
    JumperConfig jc = new JumperConfig();
    jc.setOauth(oauth);
    return toBase64(jc);
  }

  public static String getJcOauthWithScope(String id) {
    HashMap<String, OauthCredentials> oauth = new HashMap<>();
    OauthCredentials oc = new OauthCredentials();
    oc.setClientId(addIdSuffix(CONSUMER_EXTERNAL_CONFIGURED, id));
    oc.setClientSecret("secret");
    oc.setScopes(OAUTH_SCOPE_CONFIGURED);
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

  public static String getJcOauthGrantType(String id) {
    HashMap<String, OauthCredentials> oauth = new HashMap<>();
    OauthCredentials oc = new OauthCredentials();
    oc.setClientId(addIdSuffix(CONSUMER_EXTERNAL_CONFIGURED, id));
    oc.setClientSecret("secret");
    oc.setGrantType("client_credentials");
    oauth.put(CONSUMER, oc);
    JumperConfig jc = new JumperConfig();
    jc.setOauth(oauth);
    return toBase64(jc);
  }

  public static String getJcOauthGrantTypePassword(String id) {
    HashMap<String, OauthCredentials> oauth = new HashMap<>();
    OauthCredentials oc = new OauthCredentials();
    oc.setClientId(addIdSuffix(CONSUMER_EXTERNAL_CONFIGURED, id));
    oc.setClientSecret("secret");
    oc.setUsername("username");
    oc.setPassword("geheim");
    oc.setGrantType("password");
    oauth.put(CONSUMER, oc);
    JumperConfig jc = new JumperConfig();
    jc.setOauth(oauth);
    return toBase64(jc);
  }

  public static String getJcOauthGrantTypePasswordOnly(String id) {
    HashMap<String, OauthCredentials> oauth = new HashMap<>();
    OauthCredentials oc = new OauthCredentials();
    oc.setUsername(addIdSuffix("username", id));
    oc.setPassword("geheim");
    oc.setGrantType("password");
    oauth.put(CONSUMER, oc);
    JumperConfig jc = new JumperConfig();
    jc.setOauth(oauth);
    return toBase64(jc);
  }

  public static String getJcRouteListener(String id, String consumer) {
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
