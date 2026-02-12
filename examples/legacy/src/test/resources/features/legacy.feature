Feature: Springfield Electric Anti-Corruption Layer
  The legacy database contains SCREAMING_SNAKE columns, YYYYMMDD dates,
  amounts in cents, and single-character status codes. The anti-corruption
  layer transforms this into clean domain objects via CDC.

  Background:
    Given the legacy API is available
    And the CDC pipeline has populated clean data

  # ---- Transformation Tests ----

  Scenario: Customer names are properly cased
    When I query the customer graph for all customers
    Then the customer results should contain a customer with first_name "Margaret"
    And the customer results should contain a customer with last_name "Henderson"
    And the customer results should not contain a customer with first_name "MARGARET"

  Scenario: Dates are converted from YYYYMMDD to ISO format
    When I query the customer graph for account "100100-00001"
    Then the GraphQL result "connected_date" should be "1998-03-15"

  Scenario: Status codes are expanded to full words
    When I query the customer graph for account "100100-00001"
    Then the GraphQL result "status" should be "active"
    When I query the customer graph for account "100100-00003"
    Then the GraphQL result "status" should be "suspended"

  Scenario: Rate codes are expanded to human-readable names
    When I query the customer graph for account "100100-00001"
    Then the GraphQL result "rate_class" should be "residential"
    When I query the customer graph for account "200200-00002"
    Then the GraphQL result "rate_class" should be "commercial"
    When I query the customer graph for account "300300-00004"
    Then the GraphQL result "rate_class" should be "industrial"

  Scenario: Customer addresses are title-cased
    When I query the customer graph for account "100100-00001"
    Then the GraphQL result "address" should be "742 Evergreen Terrace"

  Scenario: Meter reading types are expanded and estimated flag is boolean
    When I query the meter reading graph for all readings
    Then the meter reading results should contain a reading with reading_type "actual"
    And the meter reading results should contain a reading with reading_type "estimated"
    And the meter reading results should contain a reading with estimated true

  Scenario: Bill amounts are in dollars not cents
    When I query the bill graph for all bills
    Then the bill results should contain a bill with total_amount 98.40
    And the bill results should not contain a bill with total_amount 9840

  Scenario: Payment methods are expanded to full words
    When I query the payment graph for all payments
    Then the payment results should contain a payment with method "web"
    And the payment results should contain a payment with method "electronic"
    And the payment results should contain a payment with method "check"

  Scenario: Payment amounts are in dollars not cents
    When I query the payment graph for all payments
    Then the payment results should contain a payment with amount 98.40
    And the payment results should not contain a payment with amount 9840

  # ---- Federation Tests ----

  Scenario: Customer has nested meter readings
    When I query the customer graph for account "100100-00001" with meter readings
    Then the nested "meterReadings" should contain at least 3 item(s)

  Scenario: Customer has nested bills
    When I query the customer graph for account "100100-00001" with bills
    Then the nested "bills" should contain at least 2 item(s)
    And the nested "bills" should contain an item with "status" equal to "paid"

  Scenario: Customer has nested payments
    When I query the customer graph for account "100100-00001" with payments
    Then the nested "payments" should contain at least 1 item(s)
    And the nested "payments" should contain an item with "method" equal to "web"

  Scenario: Bill has nested customer and payments
    When I query the bill graph for paid bills with customer and payments
    Then the first bill result should have a nested "customer" with "first_name" equal to "Margaret"

  Scenario: Full customer query returns clean data from all four entities
    When I query the customer graph for account "100100-00001" with full federation
    Then the GraphQL result "first_name" should be "Margaret"
    And the GraphQL result "last_name" should be "Henderson"
    And the GraphQL result "rate_class" should be "residential"
    And the GraphQL result "status" should be "active"
    And the GraphQL result "connected_date" should be "1998-03-15"
    And the nested "meterReadings" should contain at least 3 item(s)
    And the nested "bills" should contain at least 2 item(s)
    And the nested "payments" should contain at least 1 item(s)
