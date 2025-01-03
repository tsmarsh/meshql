import { processContext, callSubgraph } from "./subgraph";

import Log4js from "log4js";
import {GraphQLArgs} from "graphql/graphql";

let logger = Log4js.getLogger("gridql/DTOFactory");

export type DTOConfig = {
    id: any;
    queryName: string;
    url: URL;
}

export class DTOFactory {
    resolvers: { [key: string]: any } = {};

    constructor(config?: Resolver[]) {
        if (config !== undefined) {
            for (const c of config) {
                this.resolvers[c.name] = assignResolver(c.id, c.queryName, new URL(c.url));
            }
        }
    }

    fillOne(data: Record<string, any>, authHeader: any, timestamp: number) : Record<string, any>{
        let copy: { [key: string]: any } = { _authHeader: authHeader, _timestamp: timestamp };

        for (const f in this.resolvers) {
            if (typeof this.resolvers[f] === "function") {
                copy[f] = this.resolvers[f];
            }
        }

        assignProperties(copy, data);
        return copy;
    }

    fillMany(data: Record<string, any>, authHeader: any, timestamp: number) : Record<string, any>[]{
        return data.map((d: Record<string, any>) => this.fillOne(d, authHeader, timestamp));
    }
}

const assignProperties = (target: { [key: string]: any }, source: { [key: string]: any }): void => {
    Object.keys(source).forEach((key):void => {
        target[key] = source[key];
    });
};

const assignResolver = (id: string = "id", queryName: string, url: URL) : Record<string, any> => {
    logger.debug(`Assigning resolver for: ${id}, ${queryName}, ${url}`);
    return async function (this: { [key: string]: any }, parent: any, args: any, context: GraphQLArgs): Promise<Record<string, any>> {
        let self = this as { [key: string]: any }
        let foreignKey: any = self[id];
        const query: string = processContext(
            foreignKey,
            context,
            queryName,
            self._timestamp,
        );
        let header =
            typeof self._authHeader === "undefined" ? undefined : self._authHeader;
        return await callSubgraph(url, query, queryName, header);
    };
};
