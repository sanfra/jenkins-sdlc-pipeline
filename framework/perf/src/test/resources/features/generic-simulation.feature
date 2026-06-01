# ============================================================
# Performance Scenarios — Generic SPA Simulation
#
# Each Scenario maps to a YAML entry in profile-spa.yml by name.
# @scenario tag must match the YAML scenarios[].name field.
# @weight tags must sum to 100 for enabled scenarios.
# Thresholds (p95, success%) are defined in the YAML config layers.
# ============================================================
Feature: SPA Performance Load Test

  Background:
    Given the application is running at the configured base URL
    And the load profile is determined by environment and criticality tier

  @scenario:browse_home @weight:60
  Scenario: User visits the home page
    Given an anonymous user opens the application
    When they load the home page
    Then the page should return HTTP 200 or 304
    And the p95 response time should be within the configured threshold

  @scenario:explore_pages @weight:30
  Scenario: User browses content pages
    Given an anonymous user opens the application
    When they navigate to the about page
    And they navigate to the privacy page
    Then all pages should return HTTP 200 or 304
    And the mean response time should be within the configured threshold

  @scenario:contact_visit @weight:10
  Scenario: User visits the contact page
    Given an anonymous user opens the application
    When they navigate to the contact page
    Then the page should return HTTP 200 or 304
    And the response should complete within the configured threshold
