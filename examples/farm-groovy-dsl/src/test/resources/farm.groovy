meshql {
    port 3033
    mongo "mongodb://localhost:27017", "farm_development"

    entity("farm") {
        collection "farm-development-farm"

        graph "/farm/graph", "config/graph/farm.graphql", {
            query   "getById", id: "id"

            resolve "coops", via: "getByFarm", at: "/coop/graph"
        }

        rest "/farm/api", "config/json/farm.schema.json"
    }

    entity("coop") {
        collection "farm-development-coop"

        graph "/coop/graph", "config/graph/coop.graphql", {
            singleton "getByName", "payload.name": "id"
            query     "getById",   id: "id"
            query     "getByFarm", "payload.farm_id": "id"

            resolve "farm",           fk: "farm_id", via: "getById",  at: "/farm/graph"
            resolve "hens",                          via: "getByCoop", at: "/hen/graph"
            resolve "hens.layReports",               via: "getByHen", at: "/lay_report/graph"
        }

        rest "/coop/api", "config/json/coop.schema.json"
    }

    entity("hen") {
        collection "farm-development-hen"

        graph "/hen/graph", "config/graph/hen.graphql", {
            query "getById",   id: "id"
            query "getByName", "payload.name": "name"
            query "getByCoop", "payload.coop_id": "id"

            resolve "coop",       fk: "coop_id", via: "getById",  at: "/coop/graph"
            resolve "layReports",                 via: "getByHen", at: "/lay_report/graph"
        }

        rest "/hen/api", "config/json/hen.schema.json"
    }

    entity("lay_report") {
        collection "farm-development-lay_report"

        graph "/lay_report/graph", "config/graph/lay_report.graphql", {
            query "getById",  id: "id"
            query "getByHen", "payload.hen_id": "id"

            resolve "hen", fk: "hen_id", via: "getById", at: "/hen/graph"
        }

        rest "/lay_report/api", "config/json/lay_report.schema.json"
    }
}
