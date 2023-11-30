@upstream
Feature: proper routingPath and token requestPath is used for provider call

  Scenario: Consumer calls proxy route with remoteNo PathNo TrailingNo, upstream path /
    Given RealRoute headers are set
    When consumer calls the proxy route with remoteNoPathNoTrailingNo
    And API consumer receives a 200 status code

  Scenario: Consumer calls proxy route with remoteNo PathNo TrailingYes, upstream path /
    Given RealRoute headers are set
    When consumer calls the proxy route with remoteNoPathNoTrailingYes
    And API consumer receives a 200 status code

  Scenario: Consumer calls proxy route with remoteYes PathNo TrailingNo, upstream path /base
    Given RealRoute headers are set
    When consumer calls the proxy route with remoteYesPathNoTrailingNo
    And API consumer receives a 200 status code

  Scenario: Consumer calls proxy route with remoteYes PathNo TrailingYes, upstream path /base/
    Given RealRoute headers are set
    When consumer calls the proxy route with remoteYesPathNoTrailingYes
    And API consumer receives a 200 status code

  Scenario: Consumer calls proxy route with remoteYes PathYes TrailingNo, upstream path /base/path
    Given RealRoute headers are set
    When consumer calls the proxy route with remoteYesPathYesTrailingNo
    And API consumer receives a 200 status code

  Scenario: Consumer calls proxy route with remoteYes PathYes TrailingYes, upstream path /base/path/
    Given RealRoute headers are set
    When consumer calls the proxy route with remoteYesPathYesTrailingYes
    And API consumer receives a 200 status code

  Scenario: Consumer calls proxy route with remoteTrailing PathYes TrailingNo, upstream path /base/path
    Given RealRoute headers are set
    When consumer calls the proxy route with remoteTrailingPathYesTrailingNo
    And API consumer receives a 200 status code

  Scenario: Consumer calls proxy route with remoteTrailing PathYes TrailingYes, upstream path /base/path/
    Given RealRoute headers are set
    When consumer calls the proxy route with remoteTrailingPathYesTrailingYes
    And API consumer receives a 200 status code


  Scenario: Consumer calls proxy route with baseNo PathNo TrailingNo, token requestPath /
    Given RealRoute headers are set
    When consumer calls the proxy route with baseNoPathNoTrailingNo
    And API consumer receives a 200 status code
    And verify token requestPath value /

  Scenario: Consumer calls proxy route with baseNo PathNo TrailingYes, token requestPath /
    Given RealRoute headers are set
    When consumer calls the proxy route with baseNoPathNoTrailingYes
    And API consumer receives a 200 status code
    And verify token requestPath value //

  Scenario: Consumer calls proxy route with baseYes PathNo TrailingNo, token requestPath
    Given RealRoute headers are set
    When consumer calls the proxy route with baseYesPathNoTrailingNo
    And API consumer receives a 200 status code
    And verify token requestPath value /base

  Scenario: Consumer calls proxy route with baseYes PathNo TrailingYes, token requestPath
    Given RealRoute headers are set
    When consumer calls the proxy route with baseYesPathNoTrailingYes
    And API consumer receives a 200 status code
    And verify token requestPath value /base/

  Scenario: Consumer calls proxy route with baseYes PathYes TrailingNo, token requestPath
    Given RealRoute headers are set
    When consumer calls the proxy route with baseYesPathYesTrailingNo
    And API consumer receives a 200 status code
    And verify token requestPath value /base/path

  Scenario: Consumer calls proxy route with baseYes PathYes TrailingYes, token requestPath
    Given RealRoute headers are set
    When consumer calls the proxy route with baseYesPathYesTrailingYes
    And API consumer receives a 200 status code
    And verify token requestPath value /base/path/

  Scenario: Consumer calls proxy route with encoded query param, param not manipulated for provider
    Given RealRoute headers are set
    When consumer calls the proxy route with encodedQueryParam
    And API consumer receives a 200 status code
    And verify token requestPath value /base/path
    And verify query param validAt for value 2020-11-30T23%3A00%3A00%2B01%3A00