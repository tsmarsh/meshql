Feature: Logistics - End-to-End Package Tracking
  As a logistics operator and customer
  I want to create and track packages through the system
  So that shipments move from warehouses to recipients with full tracking visibility

  # Scenario 1: Setup — creates all test data used by subsequent scenarios.
  # State persists across scenarios (Cucumber + shared world).
  Scenario: Create logistics test data via REST API
    Given the logistics API is available
    When I create a warehouse via REST:
      | name                 | address          | city    | state | zip   | capacity |
      | BDD Test Hub Denver  | 999 Test Pkwy    | Denver  | CO    | 80202 | 5000     |
    And I create a second warehouse via REST:
      | name                     | address          | city    | state | zip   | capacity |
      | BDD Test Hub Chicago     | 888 Test Blvd    | Chicago | IL    | 60601 | 8000     |
    And I create a shipment for the first warehouse:
      | destination  | carrier | status    | estimated_delivery |
      | Portland, OR | FedEx   | in_transit | 2026-03-01        |
    And I create a shipment for the second warehouse:
      | destination  | carrier | status    | estimated_delivery |
      | Detroit, MI  | UPS     | preparing | 2026-03-05        |
    And I create a package in the first shipment:
      | tracking_number | description       | weight | recipient     | recipient_address                  |
      | PKG-BDDTEST1    | BDD Test Widget   | 5.5    | Jane Doe      | 100 Main St, Portland, OR 97201    |
    And I create a package in the second shipment:
      | tracking_number | description        | weight | recipient     | recipient_address                  |
      | PKG-BDDTEST2    | BDD Test Gadget    | 2.3    | John Smith    | 200 Oak Ave, Detroit, MI 48201     |
    And I create tracking updates for the first package:
      | status              | location            | timestamp                | notes                     |
      | label_created       | Denver, CO          | 2026-02-10T08:00:00Z     | Shipping label created    |
      | picked_up           | Denver, CO          | 2026-02-10T14:00:00Z     | Picked up by FedEx        |
      | arrived_at_facility | Denver Hub, CO      | 2026-02-11T05:00:00Z     | Arrived at sorting hub    |
      | departed_facility   | Denver Hub, CO      | 2026-02-11T20:00:00Z     | Departed Denver           |
      | in_transit          | Salt Lake City, UT  | 2026-02-12T10:00:00Z     | In transit via SLC        |
    And I create tracking updates for the second package:
      | status              | location            | timestamp                | notes                     |
      | label_created       | Chicago, IL         | 2026-02-11T09:00:00Z     | Shipping label created    |
    Then the first warehouse ID should be set
    And the first package ID should be set

  Scenario: REST API returns created entities
    When I GET the first warehouse via REST
    Then the response "name" should be "BDD Test Hub Denver"
    And the response "city" should be "Denver"
    And the response "capacity" should be 5000

  Scenario: REST API lists all warehouses
    When I GET all warehouses via REST
    Then the list should contain at least 2 items
    And the list should contain an item with "name" equal to "BDD Test Hub Denver"
    And the list should contain an item with "name" equal to "BDD Test Hub Chicago"

  Scenario: GraphQL singleton query — getById for warehouse
    When I query the warehouse graph for the first warehouse by ID
    Then the GraphQL result "name" should be "BDD Test Hub Denver"
    And the GraphQL result "state" should be "CO"

  Scenario: GraphQL vector query — getByCity
    When I query the warehouse graph by city "Denver"
    Then the GraphQL results should contain at least 1 item
    And the GraphQL results should contain an item with "name" equal to "BDD Test Hub Denver"

  Scenario: GraphQL singleton query — getByTrackingNumber
    When I query the package graph by tracking number "PKG-BDDTEST1"
    Then the GraphQL result "description" should be "BDD Test Widget"
    And the GraphQL result "recipient" should be "Jane Doe"
    And the GraphQL result "tracking_number" should be "PKG-BDDTEST1"

  Scenario: GraphQL vector query — getByStatus for shipments
    When I query the shipment graph by status "in_transit"
    Then the GraphQL results should contain at least 1 item
    And the GraphQL results should contain an item with "carrier" equal to "FedEx"

  Scenario: Federation — warehouse resolves shipments
    When I query the warehouse graph with nested shipments for the first warehouse
    Then the GraphQL result "name" should be "BDD Test Hub Denver"
    And the nested "shipments" should contain at least 1 item
    And the nested "shipments" should contain an item with "destination" equal to "Portland, OR"

  Scenario: Federation — warehouse resolves packages
    When I query the warehouse graph with nested packages for the first warehouse
    Then the nested "packages" should contain at least 1 item
    And the nested "packages" should contain an item with "tracking_number" equal to "PKG-BDDTEST1"

  Scenario: Federation — shipment resolves warehouse and packages
    When I query the shipment graph with nested warehouse and packages for the first shipment
    Then the nested "warehouse" field "name" should be "BDD Test Hub Denver"
    And the nested "packages" should contain at least 1 item
    And the nested "packages" should contain an item with "tracking_number" equal to "PKG-BDDTEST1"

  Scenario: Federation — package resolves warehouse, shipment, and tracking updates
    When I query the package graph with full federation for "PKG-BDDTEST1"
    Then the GraphQL result "description" should be "BDD Test Widget"
    And the nested "warehouse" field "name" should be "BDD Test Hub Denver"
    And the nested "warehouse" field "city" should be "Denver"
    And the nested "shipment" field "carrier" should be "FedEx"
    And the nested "shipment" field "destination" should be "Portland, OR"
    And the nested "trackingUpdates" should contain at least 5 items
    And the nested "trackingUpdates" should contain an item with "status" equal to "in_transit"

  Scenario: Federation — tracking update resolves package
    When I query the tracking update graph for the first package
    Then the GraphQL results should contain at least 5 items
    And the first result nested "package" field "tracking_number" should be "PKG-BDDTEST1"

  Scenario: Full customer journey — tracking number lookup returns complete data
    When I query the package graph with full federation for "PKG-BDDTEST1"
    Then the GraphQL result "tracking_number" should be "PKG-BDDTEST1"
    And the GraphQL result "recipient" should be "Jane Doe"
    And the GraphQL result "weight" should be 5.5
    And the nested "warehouse" field "state" should be "CO"
    And the nested "shipment" field "status" should be "in_transit"
    And the nested "trackingUpdates" should contain an item with "status" equal to "label_created"
    And the nested "trackingUpdates" should contain an item with "status" equal to "picked_up"
    And the nested "trackingUpdates" should contain an item with "status" equal to "departed_facility"
    And the nested "trackingUpdates" should contain an item with "status" equal to "in_transit"
    And the nested "trackingUpdates" should contain an item with "location" equal to "Salt Lake City, UT"
