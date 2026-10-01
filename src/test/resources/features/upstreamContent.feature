# SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
#
# SPDX-License-Identifier: Apache-2.0

@upstream @iris
Feature: expected request content reaches provider upstream

  Scenario: Documented standard and consumer headers reach provider
    Given RealRoute headers are set
    And documented consumer headers are set
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives documented standard and consumer headers
    And API consumer receives a 200 status code

  Scenario: Consumer (technical) headers are removed for provider
    Given RealRoute headers are set
    And technical headers added
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives no technical headers
    And API consumer receives a 200 status code

  Scenario: Consumer (technical) headers are removed for mesh gateway
    Given ProxyRoute headers are set
    And technical headers added
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives no technical headers
    And API consumer receives a 200 status code
