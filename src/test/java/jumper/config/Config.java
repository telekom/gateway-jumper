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
  public static final String NON_DEFAULT_REALM = "sit";
  public static final String BASE_PATH = "/eni/test/v1";
  public static final String LOCAL_ISSUER = "https://iris.local:1234/auth/realms/default";
  public static final String REMOTE_ISSUER = "https://iris.remote:1234/auth/realms/default";
  public static final String CALLBACK_SUFFIX = "/callback";
  public static final String PUBSUB_PUBLISHER = "testPublisher";
  public static final String PUBSUB_SUBSCRIBER = "testSubscriber";
  public static final String LISTENER_ISSUE = "issue";
  public static final String LISTENER_PROVIDER = "serviceOwner";
  public static final String FORWARDED_FOR = "192.0.2.1";
  public static final String FORWARDED_PATH = "/documented/path";
  public static final String CUSTOM_CONSUMER_HEADER = "X-Custom-Consumer-Header";
  public static final String CUSTOM_CONSUMER_HEADER_VALUE = "custom-value";
  public static final int REMOTE_HOST_PORT = 1080;
  public static final String REMOTE_BASE_PATH = "/real";
  public static final String REMOTE_FAILOVER_BASE_PATH = "/failover";
  public static final String REMOTE_PROVIDER_BASE_PATH = "/provider";
  public static final String REMOTE_HOST = "http://localhost:" + REMOTE_HOST_PORT;
  public static final String REMOTE_ZONE_NAME = "realZone";
  public static final String REMOTE_FAILOVER_ZONE_NAME = "failoverZone";
  // 1024-bit RSA key in PKCS#8 format: decodes successfully, but is < 2048 bits so RS256 signing
  // triggers io.jsonwebtoken.security.WeakKeyException ("RSA keys must be at least 2048 bits
  // long").
  // (A PKCS#1-encoded key would be rejected at decode time by stricter JDKs instead.)
  public static final String PRIVATE_RSA_KEY_WEAK_EXAMPLE =
      "MIICdQIBADANBgkqhkiG9w0BAQEFAASCAl8wggJbAgEAAoGBAMci+RT9eWJC+dO+UgXx2Phjoampdg49uDgP93dcQF/e1SAc9SahIbmM6BWcIkOSvSHzhwhjWg0Icm9NXkcIOjv13ehmNMKoO7xn7XNe2ZQspp+ACFk6gOnAjeu8+rtRUcpSkTvwc9orW6TbXxGOVccvlZn1cEDjvUlQo6X7lb9jAgMBAAECgYBs2whP2hOtjDEm94W87CyP816e8RywwjpeoxPCsrIZ9iSI7mtwV2qpFIaVsYjlaWRsF8R76FuEflaX0zIzICM8Rtw57G6rGaU96+FW2ot7PG0o3Yc11h+pHqhY0mGvPD0zPgXfPGr+KYI/RgaO7OVYUwj1hypdQER0k3iHJCOBQQJBAOIJtLVrHcDL8tQ5alwbHlA9hYhrZXNeA76XIVgfJkXciNyyAld1AuIrm2BjAznHMaEJhEMGurzz9aqPafPqkqECQQDhiGhO0i383VfviF5rMURkFCvDO8X00PDlSqL1Pj2SrtVgvaqOoA3l5KZfaPOv9SPMoq0H4mvxc7Eo6zSvuFeDAkA69Dcanh57e3YRHgx8i2IjoXgjdYdXSK0HV5mNx0oPLI7RqOftcYpX/PGgeRKNTkPGcZn6dVXdFG/9lTwYLxUhAkAXS1CCu6C2WmJHwk0GQ0tuDstKWfUjSSVoeWIFdI1FhjtRx6VDH/LviMNKXXu189rjuvWmN9OwV6O3tzt03tRjAkA8pGh3At9l7yiU7M7XytjjPws3InEZBUy9dEEVLjYN3i99mMbMSg3PHVAiyhC3ymaJ7p4EgItwouE9L1JoTxNZ";
  public static final String PRIVATE_RSA_KEY_SECURE_EXAMPLE =
      "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC79syAYdys04N/yVSkPP6u+XNZXq7EJew7BHNCZSEb9N8wJTxna/JJeTZ1ywpeIumOW7pjZ1JYriAzSpOqa6lahUVSPc8GVy+p1k8UQOZyVGPPGdY2NY/njr8+2zYxujqyxa+vW7ZKUDNJzMyIUKqklA9mQJsWVU89rJwR5G4UWvG5p04ceTG4pHeGhvIrVOIZIQn3rxMNUP8f8RnBOuGBkaEpV8dyQjJT08a2nago3Nt34mlttRERKJCY1QolVfnH+ayDTDwL5yB9Hn5QS64Lm3FCOzlvVS6pwsATHXo6OfmGtaDXJPzL5Mewa6CJrp3CZxoljDE5Bhsf6to96WZlAgMBAAECggEACBvFnFTca1uKhNo0u7W35OjKQuo0jtigoAvOmb15bVZ7GagZkCVAzqObJ6oKeJ9374C3LITvW/eoWEjDewAUTl0zwtr7Zyd0B8HA0pEEKp1yWs5KBoz5TWtuhkci8jKO8q8C0lqZ+rW1U13cMECOmUqb1B+TBnvH+dNMPkdaP6gjxGhmk8NaocEsQBKV6USHHJv6pdohsCQHD6AuPv77CJdvhZsvJXWRCYPjwzlcVEX8IyiVmOCSHH8RUgxVcp9L565mN9/Rb+cMjMz+awYhh4A26WxH0xu2OqqXa/z240YSNEHcHLw6pkpKpexV2s1MZZjN6MuiQbcbCTwF0rk7IQKBgQDXIehh4ylj7gZjzZj2pIPkz8CamSbGd253wfNdDf/eBxpUlVOBsMWal413ETNBOZT5ZNYtsiK8iq4BimmCVgKngF/6gfMT66z94vcLxTvVZkdHRxsXLwEJLSgWazmaBfi4nZfHtmJZ+sUhn+Px4JTG/xZ3wmAh7rHaUbyH5n1K1QKBgQDfq6qrzDxBLCwx4izHymMl5qdjS878IydQPQ3pci4TzRDFadT3qJTM0g79djKeUvaIMMX9FzVGpmWB5qeUpErG7NtWjh69Jre/V78soxF8h4fkG/KUbgm+f0LtRj4DtVZxu4yOr+8qtzOTRWXKmQ9JdDtDko5Y2IFPKC6turxVUQKBgEJ4No2wG0TF8XF6v81NDXdv/UmHEmT118rmwSO6RJk8RpVlwfRrQtK+CraEOPrpKK9ZyZc61+K7UoIlWu4rVwyb7IvBBPLduYiETOJ1IUSRImrSfHtQSZilPCKZJKYDPFMGVjQdlQvKIIiAF3TPeAh4HmAITZ2OW6Nh58dxnrq1AoGBAL967gRfNuIwV7FoyB9OAu1KddhK6OrviVNmwUctyYaIEqh+fqR6PNDPr6eLDbB+o7FZ19VgjepqvxGjDanxsFZ2JRwHVQdnYvy6uN4Ux/6M5GgDCPvK7CqaNgh9DtAL6PI6tgzdTumJpuyYB5mWyQCAMdAaYiRrTOAgLT3rVBnRAoGBAIDbclld+LDtLWycl0DQ+YVYS8dWwNT5bWFt1Eh256yyXOiW5Z2qrBST0H6tY0xcQRq77DVkZxQm/rq6LdxVBoxcti1EjiPVFbOQZ3pwhUTj0RS0Nldgha+AAkPpRC2rYwe5lOaPFycNjky1+XUVDI4av1gphZAs/JZAch6QHfce";
}
