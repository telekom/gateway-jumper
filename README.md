<!--
SPDX-FileCopyrightText: 2023 Deutsche Telekom AG

SPDX-License-Identifier: CC0-1.0    
-->

# Jumper

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

## About

Jumper is a cloud-native scalable API Gateway expected to run as a sidecar of Kong API Gateway.
It is based on [Spring Cloud Gateway](https://spring.io/projects/spring-cloud-gateway).

Its purpose is mainly advanced token (OAuth 2.0) handling, enabling support for:

* Mesh functionality
* External authorization
* Gateway token generation
* Header customization
* Service event listening (creates events for issued traffic)

On the incoming side, it is called by Kong. On the outgoing side, it is the last component that calls the provider.
For its functionality, it relies on information provided by the Kong component using headers, while remaining stateless itself.

```mermaid
flowchart LR
    consumer((Consumer))
    iris[Iris]

    subgraph gateway [Gateway]
        direction TB
        kong[Kong]
        jumper[Jumper]
        issuerService[Issuer Service]
    end

    providerIdp[Provider IdP]
    provider((Provider))

    consumer -.->|request token| iris
    iris -.->|token| consumer
    consumer -->|request| kong
    kong --> jumper
    jumper -.->|optional token request| providerIdp
    jumper --> provider
    provider -.->|get public key| issuerService

    classDef gatewayNode fill:#f8d7da,stroke:#c0392b,stroke-width:1px,color:#111;
    classDef external fill:#f7f7f7,stroke:#666,stroke-width:1px,color:#111;
    class kong,jumper,issuerService gatewayNode;
    class consumer,iris,providerIdp,provider external;
```

## Getting Started

The easiest way to get started is to build your own Jumper image using [Jib](#jib-image-builds).

Once you have that, refer to the [Configuration](#configuration) section to find out how to use Jumper locally or deploy it using the [Stargate Helm Chart](https://github.com/telekom/gateway-kong-charts).

## Contributing

This project has adopted the [Contributor Covenant](https://www.contributor-covenant.org/) in version 2.1 as our code of conduct. Please see the details in our [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md). All contributors must abide by the code of conduct.

By participating in this project, you agree to abide by its Code of Conduct at all times.

## Licensing

This project follows the [REUSE standard for software licensing](https://reuse.software/).
Each file contains copyright and license information, and license texts can be found in the [./LICENSES](./LICENSES) folder. For more information visit <https://reuse.software/>.

## Building

### Packaging the Application

This project is built with [Maven](https://maven.apache.org/). It is validated to be compatible with version 3.9.x. To build the project, run:

```bash
./mvnw clean package
```

This will build the project and run all tests. The resulting artifacts will be placed in the `target` directory.

### OCI Image Builds

Container images are built using [Jib](https://github.com/GoogleContainerTools/jib), which creates optimized, layered OCI images directly from Maven without requiring a Docker daemon.

#### Build to Local Docker Daemon

```bash
./mvnw jib:dockerBuild
```

This builds the image and loads it into your local Docker daemon as `jumper`.

#### Customizing the Base Image

The default base image is `gcr.io/distroless/java21-debian12:nonroot`. To override it:

```bash
./mvnw jib:dockerBuild -Djib.from.image=<your-preferred-base-image>
```

#### Docker Builds (Deprecated)

The project still contains Dockerfiles, but these are deprecated. Prefer Jib for building container images.

```bash
docker build --platform linux/amd64 -t jumper .
```

Or using the self-contained multi-stage build (no local Maven needed):

```bash
docker build --platform linux/amd64 -t jumper -f Dockerfile.multi-stage .
```

## Configuration

Jumper is typically deployed as part of the Gateway Helm chart, which provides all necessary configuration parameters and sensible defaults.

### Helm Deployment

For production deployments, refer to the jumper section in the Gateway Helm chart's values.yaml file in the [official repository](https://github.com/telekom/gateway-kong-charts).

### Local Configuration

For local development and testing, Jumper uses Spring Boot's configuration mechanism with properties defined in [`application.yml`](src/main/resources/application.yml). The application can be configured through environment variables that are referenced in this configuration file.

For additional standard Spring Boot properties, refer to the [Spring Boot documentation](https://docs.spring.io/spring-boot/appendix/application-properties/index.html).

## Usage Scenarios

Jumper supports various token handling and routing scenarios.  
**Note: Scenarios may overlap across different perspectives.**

### Glossary

* **Gateway** - Set of Kong + Jumper + Issuer service
* **Spacegate** - Gateway accessible from/having access to (after firewall clearance) Internet
* **jumper_config** - Base64 encoded structure used to pass various information

### Token Handling Scenarios

The following describes different scenarios for token handling in Jumper.

"Required Headers" refers to headers coming from Kong to Jumper, while "Outgoing Headers" refers to headers that Jumper sends to the upstream service.

#### One Token

The most common scenario where Jumper creates a new OAuth token by combining information from the incoming token and headers.

**Required Headers:**
* `remote_api_url` - Target URL for request forwarding
* `api_base_path` - Base path of the Kong service in the initial zone. Passed as `requestPath` claim.
* `realm` - Used to set the correct issuer
* `environment` - Passed as `env` claim
* `access_token_forwarding` - Used to determine the scenario. Set to `false` in this case.

**Token Structure (One Token):**

```
{
  "kid": "<matching certificate available on Issuer service>",
  "typ": "JWT",
  "alg": "RS256"
}
```

```
{
  "sub": "<taken from incoming token>",
  "clientId": "<taken from incoming token>",   
  "azp": "stargate",
  "originZone": "<taken from incoming token>",
  "typ": "Bearer",
  "env": "<taken from header>",
  "operation": "<performed operation>",
  "requestPath": "<taken from header>",
  "originStargate": "<taken from incoming token>",
  "iss": "<composed value with issuer address for created token>",
  "exp": <taken from incoming token>,
  "iat": <taken from incoming token>
}
```

**Outgoing Headers:**
* `Authorization` - Contains the newly created token

#### Last Mile Security Token (Legacy)

A legacy scenario where Jumper forwards both the original token and a new LMS token (in an `X-Gateway-Token` header).

**Required Headers:**
* Same as One Token scenario, but with `access_token_forwarding` set to `true`

**Structure of LMS Token:**

```
{
  "kid": "<matching certificate available on Issuer service>",
  "typ": "JWT",
  "alg": "RS256"
}
```

```
{
  "sub": "<taken from incoming token>",
  "clientId": "<taken from incoming token>",
  "azp": "stargate",
  "originZone": "aws",
  "typ": "Bearer",
  "operation": "<performed operation>",
  "requestPath": "<taken from header>",
  "originStargate": "<taken from incoming token>",
  "iss": "<composed value with issuer address for created token>",
  "exp": <taken from incoming token>,
  "iat": <taken from incoming token>
}
```

**Outgoing Headers:**
* `Authorization` - Original incoming token
* `X-Gateway-Token` - New LMS token

#### Mesh LMS Token

Scenario with multiple Gateway instances involved.
For gateway-to-gateway calls, Jumper creates a short-lived, self-signed Mesh LMS token from the
incoming consumer token claims and sends it as the upstream `Authorization` header. The original
consumer token is not forwarded to the downstream gateway.

```mermaid
flowchart LR
    consumer((Consumer))
    idpA[Identity Provider<br/>Zone A]
    provider((Provider))

    subgraph zoneA [Zone A]
        direction TB
        kongA[Kong]
        jumperA[Jumper]
        issuerA[Issuer Service]
    end

    subgraph zoneB [Zone B]
        direction TB
        kongB[Kong]
        jumperB[Jumper]
        issuerB[Issuer Service]
    end

    consumer -.->|request token| idpA
    idpA -.->|token| consumer
    consumer --> kongA
    kongA --> jumperA
    jumperA -->|Mesh LMS token| kongB
    kongB --> jumperB
    jumperB -.->|validate Mesh LMS token| issuerA
    jumperB -->|provider LMS token| provider
    provider -.->|get public key| issuerB

    classDef gatewayNode fill:#f8d7da,stroke:#c0392b,stroke-width:1px,color:#111;
    classDef external fill:#f7f7f7,stroke:#666,stroke-width:1px,color:#111;
    class kongA,jumperA,issuerA,kongB,jumperB,issuerB gatewayNode;
    class consumer,idpA,provider external;
```

**Required Headers:**
* `remote_api_url` - URL (including service base path) of the other zone's Gateway, to which the request is forwarded
* `jumper_config` - Base64 encoded structure with `mesh` set to `true`
* `issuer` - Legacy fallback discriminator for pre-migration proxy routes

**Outgoing Headers:**
* `Authorization` - Mesh LMS token

#### External Authorization Token

Jumper forwards requests with tokens fetched from provider-defined identity providers (Spacegate only).

```mermaid
flowchart LR
    consumer((Consumer))
    idp[Identity Provider]
    extIdp[External Identity Provider]
    provider((Provider))

    subgraph gateway [Gateway]
        direction TB
        kong[Kong]
        jumper[Jumper]
        issuer[Issuer Service]
    end

    consumer -.->|request token| idp
    idp -.->|token| consumer
    consumer --> kong
    kong --> jumper
    jumper -.->|request token| extIdp
    extIdp -.->|token| jumper
    jumper --> provider

    classDef gatewayNode fill:#f8d7da,stroke:#c0392b,stroke-width:1px,color:#111;
    classDef external fill:#f7f7f7,stroke:#666,stroke-width:1px,color:#111;
    class kong,jumper,issuer gatewayNode;
    class consumer,idp,extIdp,provider external;
```

**Required Headers:**
* `remote_api_url` - Target URL
* `token_endpoint` - Endpoint of external identity provider
* `client_id` - Client ID for external identity provider
* `client_secret` - Client Secret for external IdP

If credentials differ per consumer, the following `jumper_config` can be used instead of `client_id` and `client_secret`:

```
{
  "oauth": {
  "<consumer matching the one from incoming token>": {
    "clientId": "<client id to query token from external idp>",
    "clientSecret": "<client secret to query token from external idp>"
    }
  }
}
```

**Resolution semantics:** Authentication configuration is atomic. A consumer entry carrying any authentication-related field (`clientId`, `clientSecret`, `clientKey`, `username`, `password`, `refreshToken`, `grantType`, `tokenRequest`) is used as-is — missing fields are never filled in from the provider `default` entry. The one supported partial shape is a **scopes-only** consumer entry: it uses the `default` entry's authentication configuration as a whole with only `scopes` replaced (never combined with the default scopes). Without a consumer entry, the `default` entry applies unchanged.

**Validation:** If the resolved configuration contains no usable client authentication (neither `clientId`+`clientSecret`, `clientKey`, `username`+`password`, nor `refreshToken`), Jumper rejects the request with `400 Bad Request` and a descriptive message instead of sending a credential-less token request to the external IdP (which would surface as an opaque 401). Configurations without a `grantType` use the legacy header-based flow, which supports `clientId`+`clientSecret` only — the alternative mechanisms require a `grantType` to be set, and the error message says so. Such rejections are counted in the `jumper_external_oauth_config_error_total` metric.

#### Basic Auth Token

Supports legacy systems requiring Basic Authorization (Spacegate only). Authorization can be defined globally for a provider, or on a per consumer basis.

**Required Headers:**
* `remote_api_url` - Target URL
* `jumper_config` - Contains Basic Auth configuration with the following format:

```
{
  "basicAuth": {
  "default/<consumer name>": {
    "username": "<username>",
    "password": "<password>"
    }
  }
}
```

#### X-Token-Exchange

Allows passing external provider-specific tokens via the `X-Token-Exchange` header (Spacegate only).

When a consumer sets the `X-Token-Exchange` header containing an external provider-specific token, Jumper will use this value as the `Authorization` header in the request forwarded to the provider.

### Additional Features

#### Spectre Event Listening

Spectre allows a third-party listener application to monitor communication between consumer and provider for specific APIs.

**Prerequisites:**
* Configured `jumper.horizon.publishEventUrl` in application properties
* Properly configured `jumper_config` header with listener settings

```json
{
  "routeListener": {
    "<consumer>": {
      "issue": "<API identifier>",
      "serviceOwner": "<service name>"
    }
  }
}
```

Horizon events are created for matching consumer/provider combinations.
The events contain request/response details including headers and payload.
The created event structure is:

```
{
"time" : "<timestamp>",
"id" : "<event id>",
"type" : "<particular listener event type>",
"source" : "<source name>",
"specversion" : "1.0",
"datacontenttype" : "application/json",
"data" : {
  "consumer" : "<consumer, value from incoming token>",
  "provider" : "service name, value from jumper config",
  "issue" : "<API, value from jumper config>",
  "kind" : "REQUEST/RESPONSE",
  "method" : "<method>",
  "header" : {
    <headers of processed request>
  },
  "payload" : <processed request body>
  }
}
```

#### Zone Failover

If enabled, Jumper can route requests to a failover zone when the primary zone fails.

The following diagram shows how Jumper processes requests in case of an active failover:

```mermaid
flowchart TD
    start([Jumper receives request])
    hasRoutingConfig{routing_config present?}
    normal[Use regular jumper_config processing]
    selectConfig[Take next jumper_config<br/>from routing_config]
    secondary{targetZone missing?}
    targetUnavailable{targetZone skipped<br/>or zone is down?}
    useConfig[Use selected jumper_config<br/>for routing]
    useSecondary[Use secondary/provider config]
    hasNext{another jumper_config exists?}
    unavailable[Respond with 503]
    done((done))

    start --> hasRoutingConfig
    hasRoutingConfig -->|no| normal
    hasRoutingConfig -->|yes| selectConfig
    selectConfig --> secondary
    secondary -->|yes| useSecondary
    secondary -->|no| targetUnavailable
    targetUnavailable -->|no| useConfig
    targetUnavailable -->|yes| hasNext
    hasNext -->|yes| selectConfig
    hasNext -->|no| unavailable
    normal --> done
    useSecondary --> done
    useConfig --> done
    unavailable --> done
```

#### Header Enhancement

Jumper enriches the request with additional headers, depending on the situation.

| Header | Purpose |
|--------|--------|
| X-Spacegate-Token | Copy of incoming token when Spacegate is involved |
| X-Forwarded-* | Adapted to avoid reporting Kong + Jumper as separate hops |
| X-Origin-Stargate | Shows which Gateway host was originally called |
| X-Origin-Zone | Shows which Gateway zone was originally called |

#### And more

* **Tracing**: [B3 Zipkin propagation](https://github.com/openzipkin/b3-propagation) support (requires `spring.zipkin.baseUrl` configuration)
* **Scope Handling**: If a `scopes` claim is present, scopes are passed to upstream in OneToken for fine-grained authorization
* **Horizon Integration**: `x-pubsub-publisher-id` and `x-pubsub-subscriber-id` headers are passed in OneToken

### Route Types

The following describes the different types of routes implemented in Jumper.

Routes are implemented using varying sets of filters. Here is a short overview:

Filters for standard processing:
* `RequestFilter` - Main processing logic
* `RemoveRequestHeaderFilter` - Removes headers used for passing information from Kong to Jumper
* `ResponseFilter` - Minor tracing adjustments

Spectre-specific filters:
* `RequestTransformationFilter` - Transforms request body
* `SpectreRequestFilter` - Creates Spectre request event (if configured for given consumer/provider combination)
* `ResponseTransformationFilter` - Transforms response body
* `SpectreResponseFilter` - Creates Spectre response event (if configured for given consumer/provider combination)
* `SpectreRoutingFilter` - Sets authorization header and adapts routing path to Horizon

To understand the filter chains per route, please refer to the route implementation in [RoutingConfiguration.java](src/main/java/jumper/config/RoutingConfiguration.java).
The diagrams below summarize the current route definitions. Response-side filters are shown on the
return path, even when their Gateway filter wraps the whole exchange internally.

#### Proxy Route (`jumper_route`)

The default route type that processes the majority of traffic. All token handling scenarios are supported.

```mermaid
sequenceDiagram
    participant call
    participant RequestFilter
    participant UpstreamOAuthFilter
    participant RemoveRequestHeaderFilter
    participant PlaintextValidationFilter
    participant ResponseFilter
    participant upstream

    call->>RequestFilter: request
    Note over RequestFilter,PlaintextValidationFilter: request path
    RequestFilter->>UpstreamOAuthFilter: next
    UpstreamOAuthFilter->>RemoveRequestHeaderFilter: next
    RemoveRequestHeaderFilter->>PlaintextValidationFilter: next
    PlaintextValidationFilter->>upstream: request
    Note over upstream,ResponseFilter: response path
    upstream-->>ResponseFilter: response
    ResponseFilter-->>call: response
```

#### Listener Route (`listener_route`)

Supports payload listening via *Spectre* in addition to the basic functionality of the proxy route.

```mermaid
sequenceDiagram
    participant call
    participant RequestFilter
    participant UpstreamOAuthFilter
    participant RemoveRequestHeaderFilter
    participant RequestTransformationFilter
    participant SpectreRequestFilter
    participant ResponseFilter
    participant ResponseTransformationFilter
    participant SpectreResponseFilter
    participant upstream

    call->>RequestFilter: request
    Note over RequestFilter,SpectreRequestFilter: request path
    RequestFilter->>UpstreamOAuthFilter: next
    UpstreamOAuthFilter->>RemoveRequestHeaderFilter: next
    RemoveRequestHeaderFilter->>RequestTransformationFilter: next
    RequestTransformationFilter->>SpectreRequestFilter: next
    SpectreRequestFilter->>upstream: request
    Note over upstream,ResponseFilter: response path
    upstream-->>ResponseTransformationFilter: response
    ResponseTransformationFilter-->>SpectreResponseFilter: cached response body
    SpectreResponseFilter-->>ResponseFilter: response event handled
    ResponseFilter-->>call: response
```

#### Spectre POST Route (`auto_event_route_post`)

Receives event callback from Horizon. Only required for *Spectre*.
The generic event type is modified to a listener specific one and forwarded to Horizon for further processing.

```mermaid
sequenceDiagram
    participant subscriber as Horizon subscriber
    participant ModifyRequestBody
    participant RemoveRequestParameter as removeRequestParameter
    participant SpectreRoutingFilter
    participant producer as Horizon producer

    subscriber->>ModifyRequestBody: callback
    ModifyRequestBody->>RemoveRequestParameter: SpectreBodyRewrite
    RemoveRequestParameter->>SpectreRoutingFilter: next
    SpectreRoutingFilter->>producer: publish event
```

#### Spectre HEAD Route (`auto_event_route_head`)

Because Jumper acts as a Horizon callback consumer, it has to support a HEAD request for possible healthchecks.
Only required for *Spectre*.

```mermaid
sequenceDiagram
    participant subscriber as Horizon subscriber
    participant RemoveRequestParameter as removeRequestParameter
    participant SpectreRoutingFilter
    participant producer as Horizon producer

    subscriber->>RemoveRequestParameter: healthcheck
    RemoveRequestParameter->>SpectreRoutingFilter: next
    SpectreRoutingFilter->>producer: healthcheck
```
