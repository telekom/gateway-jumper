# SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
#
# SPDX-License-Identifier: Apache-2.0

@upstream @iris
Feature: request correctly routed to loadbalancing upstream

  ################ valid loadbalancing ################
  Scenario: Consumer calls proxy route with Loadbalancing structure
    Given RealRoute headers without remoteApiUrl are set
    And jumperConfig valid loadbalancing set
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives default bearer authorization headers
    Then API Provider receives authorization OneToken

  Scenario: Consumer calls proxy route with secondary config and skip header, provider called
    Given Secondary routing_config with loadbalancing header set
    And skip zone header set
    And API provider set to respond on provider path
    When consumer calls the proxy route without base path
    Then API Provider receives default bearer authorization headers
    Then API Provider receives authorization OneTokenSimple

  ################ invalid loadbalancing ################
  Scenario: Consumer calls proxy route with empty Loadbalancing structure
    Given RealRoute headers without remoteApiUrl are set
    And jumperConfig empty loadbalancing set
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API consumer receives a 500 status code
    And error response contains msg "missing routing information remote_api_url / jc.loadBalancing" error "Internal Server Error" status 500

