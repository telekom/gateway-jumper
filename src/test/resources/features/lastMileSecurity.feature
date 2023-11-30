@upstream @iris
Feature: Last Mile Security, legacy scenario with 2 tokens

  Scenario: Consumer calls an API with lastMileSecurity
    Given lastMileSecurity is activated
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API provider receives AccessToken and GatewayToken
    And API consumer receives a 200 status code

  Scenario: Consumer calls an API with lastMileSecurity and realm header contains several values
    Given lastMileSecurity is activated
    And several realm fields are contained in the header
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API provider receives AccessToken and GatewayToken
    And API consumer receives a 200 status code

  Scenario: Consumer calls an API with lastMileSecurity and Provider respond with a 401
    Given lastMileSecurity is activated
    And API provider set to respond with a 401 status code
    When consumer calls the proxy route
    Then API provider receives AccessToken and NO_GatewayToken
    And API consumer receives a 401 status code

  Scenario: Consumer calls an API with lastMileSecurity and Provider respond with a 503
    Given lastMileSecurity is activated
    And API provider set to respond with a 503 status code
    When consumer calls the proxy route
    Then API provider receives AccessToken and GatewayToken
    And API consumer receives a 503 status code

  Scenario: Consumer calls an API with lastMileSecurity and Provider will have a timeout
    Given lastMileSecurity is activated
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route and runs into timeout
    Then API consumer receives a 504 status code

  Scenario: Consumer calls an API with lastMileSecurity and connection will be dropped
    Given lastMileSecurity is activated
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route and connection is dropped
    Then API consumer receives a 500 status code
