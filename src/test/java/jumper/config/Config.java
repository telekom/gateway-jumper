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
  // io.jsonwebtoken.security.WeakKeyException: RSA keys must be at least 2048 bits long
  public static final String PRIVATE_RSA_KEY_WEAK_EXAMPLE =
      "MIIBOgIBAAJBAKj34GkxFhD90vcNLYLInFEX6Ppy1tPf9Cnzj4p4WGeKLs1Pt8QuKUpRKfFLfRYC9AIKjbJTWit+CqvjWYzvQwECAwEAAQJAIJLixBy2qpFoS4DSmoEmo3qGy0t6z09AIJtH+5OeRV1be+N4cDYJKffGzDa88vQENZiRm0GRq6a+HPGQMd2kTQIhAKMSvzIBnni7ot/OSie2TmJLY4SwTQAevXysE2RbFDYdAiEBCUEaRQnMnbp79mxDXDf6AU0cN/RPBjb9qSHDcWZHGzUCIG2Es59z8ugGrDY+pxLQnwfotadxd+Uyv/Ow5T0q5gIJAiEAyS4RaI9YG8EWx/2w0T67ZUVAw8eOMB6BIUg0Xcu+3okCIBOs/5OiPgoTdSy7bcF9IGpSE8ZgGKzgYQVZeN97YE00";
  public static final String PRIVATE_RSA_KEY_SECURE_EXAMPLE =
      "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC79syAYdys04N/yVSkPP6u+XNZXq7EJew7BHNCZSEb9N8wJTxna/JJeTZ1ywpeIumOW7pjZ1JYriAzSpOqa6lahUVSPc8GVy+p1k8UQOZyVGPPGdY2NY/njr8+2zYxujqyxa+vW7ZKUDNJzMyIUKqklA9mQJsWVU89rJwR5G4UWvG5p04ceTG4pHeGhvIrVOIZIQn3rxMNUP8f8RnBOuGBkaEpV8dyQjJT08a2nago3Nt34mlttRERKJCY1QolVfnH+ayDTDwL5yB9Hn5QS64Lm3FCOzlvVS6pwsATHXo6OfmGtaDXJPzL5Mewa6CJrp3CZxoljDE5Bhsf6to96WZlAgMBAAECggEACBvFnFTca1uKhNo0u7W35OjKQuo0jtigoAvOmb15bVZ7GagZkCVAzqObJ6oKeJ9374C3LITvW/eoWEjDewAUTl0zwtr7Zyd0B8HA0pEEKp1yWs5KBoz5TWtuhkci8jKO8q8C0lqZ+rW1U13cMECOmUqb1B+TBnvH+dNMPkdaP6gjxGhmk8NaocEsQBKV6USHHJv6pdohsCQHD6AuPv77CJdvhZsvJXWRCYPjwzlcVEX8IyiVmOCSHH8RUgxVcp9L565mN9/Rb+cMjMz+awYhh4A26WxH0xu2OqqXa/z240YSNEHcHLw6pkpKpexV2s1MZZjN6MuiQbcbCTwF0rk7IQKBgQDXIehh4ylj7gZjzZj2pIPkz8CamSbGd253wfNdDf/eBxpUlVOBsMWal413ETNBOZT5ZNYtsiK8iq4BimmCVgKngF/6gfMT66z94vcLxTvVZkdHRxsXLwEJLSgWazmaBfi4nZfHtmJZ+sUhn+Px4JTG/xZ3wmAh7rHaUbyH5n1K1QKBgQDfq6qrzDxBLCwx4izHymMl5qdjS878IydQPQ3pci4TzRDFadT3qJTM0g79djKeUvaIMMX9FzVGpmWB5qeUpErG7NtWjh69Jre/V78soxF8h4fkG/KUbgm+f0LtRj4DtVZxu4yOr+8qtzOTRWXKmQ9JdDtDko5Y2IFPKC6turxVUQKBgEJ4No2wG0TF8XF6v81NDXdv/UmHEmT118rmwSO6RJk8RpVlwfRrQtK+CraEOPrpKK9ZyZc61+K7UoIlWu4rVwyb7IvBBPLduYiETOJ1IUSRImrSfHtQSZilPCKZJKYDPFMGVjQdlQvKIIiAF3TPeAh4HmAITZ2OW6Nh58dxnrq1AoGBAL967gRfNuIwV7FoyB9OAu1KddhK6OrviVNmwUctyYaIEqh+fqR6PNDPr6eLDbB+o7FZ19VgjepqvxGjDanxsFZ2JRwHVQdnYvy6uN4Ux/6M5GgDCPvK7CqaNgh9DtAL6PI6tgzdTumJpuyYB5mWyQCAMdAaYiRrTOAgLT3rVBnRAoGBAIDbclld+LDtLWycl0DQ+YVYS8dWwNT5bWFt1Eh256yyXOiW5Z2qrBST0H6tY0xcQRq77DVkZxQm/rq6LdxVBoxcti1EjiPVFbOQZ3pwhUTj0RS0Nldgha+AAkPpRC2rYwe5lOaPFycNjky1+XUVDI4av1gphZAs/JZAch6QHfce";
}
