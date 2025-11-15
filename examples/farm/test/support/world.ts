import { World, IWorldOptions } from '@cucumber/cucumber';
import { StartedDockerComposeEnvironment } from 'testcontainers';
import { OpenAPIClient } from 'openapi-client-axios';

export interface FarmWorld extends World {
    environment?: StartedDockerComposeEnvironment;
    hen_api?: OpenAPIClient;
    coop_api?: OpenAPIClient;
    farm_api?: OpenAPIClient;
    farm_id?: string;
    coop1_id?: string;
    coop2_id?: string;
    graphqlResult?: any;
    tearDown?: () => Promise<void>;
}

export class FarmTestWorld extends World implements FarmWorld {
    environment?: StartedDockerComposeEnvironment;
    hen_api?: OpenAPIClient;
    coop_api?: OpenAPIClient;
    farm_api?: OpenAPIClient;
    farm_id?: string;
    coop1_id?: string;
    coop2_id?: string;
    graphqlResult?: any;
    tearDown?: () => Promise<void>;

    constructor(options: IWorldOptions) {
        super(options);
    }
}
