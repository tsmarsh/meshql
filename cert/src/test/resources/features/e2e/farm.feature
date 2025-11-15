Feature: The Farm - Server Certification
  As a database plugin developer
  I want to verify my plugin works with the full MeshQL server
  So that farms, coops, and hens can be managed through GraphQL and REST APIs

  Scenario:
    Given a MeshQL server is running with the plugin
    And I have created "farm":
      | name | data |
      | Emerdale  | { name: 'Emerdale' } |
    And I have created "coop":
      | name | data |
      | red | { name: 'red', farm_id: '{{ids.farm.Emerdale}}' } |
      | yellow | { name: 'yellow', farm_id: '{{ids.farm.Emerdale}}' }   |

    And I have created "hen":
      | name | data |
      | chuck | { name: 'chuck', eggs: 2, coop_id: '{{ids.coop.red}}' } |
      | duck  | { name: 'duck', eggs: 0, coop_id: '{{ids.coop.red}}' }  |
      | euck  | { name: 'euck', eggs: 1, coop_id: '{{ids.coop.yellow}}' }  |
      | fuck  | { name: 'fuck', eggs: 2, coop_id: '{{ids.coop.yellow}}' }  |

    And I have captured the first timestamp

    And I have updated "coop":
      | name | data |
      | red  | { name: 'purple', farm_id: '{{ids.farm.Emerdale}}' }     |

  Scenario: Build a server with multiple nodes
    When I query the "farm" graph:
      """
      {
        getById(id: "{{ids.farm.Emerdale}}") {
          name
          coops {
            name
            hens {
              eggs
              name
            }
          }
        }
      }
      """
    Then the farm name should be "Emerdale"
    And there should be 2 "coops"

  Scenario: Answer simple queries
    When I query the "hen" graph:
      """
      {
        getByName(name: "duck") {
          id
          name
        }
      }
      """
    Then the result should contain "name" "duck"
    And the 0 "hen" ID should match the saved "duck" ID

  Scenario: Query in both directions
    When I query the "hen" graph:
      """
      {
        getByCoop(id: "{{ids.coop.red}}") {
          name
          eggs
          coop {
            name
            farm {
              name
            }
          }
        }
      }
      """
    Then there should be 2 results
    And the hens should include "chuck" and "duck"
    And the coop name should be "purple"

  Scenario: Get latest by default
    When I query the "coop" graph:
      """
      {
        getById(id: "{{ids.coop.red}}") {
          id
          name
        }
      }
      """
    Then the "coop" ID should match the saved "red" ID
    And the coop name should be "purple"

  Scenario: Get closest to the timestamp when specified
    When I query the "coop" graph:
      """
      {
        getById(id: "{{ids.coop.red}}", at: {{first_stamp}}) {
          name
        }
      }
      """
    Then the coop name should be "red"

  Scenario: Obey the timestamps
    When I query the "farm" graph:
      """
      {
        getById(id: "{{ids.farm.Emerdale}}", at: {{first_stamp}}) {
          coops {
            name
          }
        }
      }
      """
    Then the results should not include "purple"

  Scenario: Pass timestamps to next layer
    When I query the "farm" graph:
      """
      {
        getById(id: "{{ids.farm.Emerdale}}", at: {{now}}) {
          coops {
            name
          }
        }
      }
      """
    Then the results should include "purple"
