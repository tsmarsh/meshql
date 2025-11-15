import { Before, After, BeforeAll, AfterAll, setDefaultTimeout, setWorldConstructor } from '@cucumber/cucumber';
import { FarmWorld, FarmTestWorld } from './world';

setWorldConstructor(FarmTestWorld);
import { DockerComposeEnvironment, StartedDockerComposeEnvironment, Wait } from 'testcontainers';
import { Document, OpenAPIClient, OpenAPIClientAxios } from 'openapi-client-axios';
import path from 'path';

setDefaultTimeout(300000); // 5 minutes for docker startup

let globalEnvironment: StartedDockerComposeEnvironment | null = null;
let globalApis: { hen_api: OpenAPIClient; coop_api: OpenAPIClient; farm_api: OpenAPIClient } | null = null;

BeforeAll(async function() {
    // Start the docker-compose environment once for all tests
    globalEnvironment = await new DockerComposeEnvironment(path.resolve(__dirname, '../..'), 'docker-compose.yml')
        .withBuild()
        .withWaitStrategy('farm_1', Wait.forHttp('/ready', 3033).withStartupTimeout(30000))
        .withWaitStrategy('mongodb', Wait.forLogMessage('Waiting for connections'))
        .up();

    // Initialize API clients
    const swagger_docs = await getSwaggerDocs();
    globalApis = await buildApis(swagger_docs);
});

AfterAll(async function() {
    if (globalEnvironment) {
        await globalEnvironment.down();
        globalEnvironment = null;
    }
});

Before(async function(this: FarmWorld) {
    // Share the global environment and APIs with each scenario
    this.environment = globalEnvironment!;
    this.hen_api = globalApis!.hen_api;
    this.coop_api = globalApis!.coop_api;
    this.farm_api = globalApis!.farm_api;
});

After(async function(this: FarmWorld) {
    if (this.tearDown) {
        await this.tearDown();
    }
});

async function getSwaggerDocs(): Promise<Document[]> {
    const maxRetries = 10;
    const retryDelay = 2000;

    for (let attempt = 1; attempt <= maxRetries; attempt++) {
        try {
            return await Promise.all(
                ['/hen', '/coop', '/farm'].map(async (restlette) => {
                    let url = `http://localhost:3033${restlette}/api/api-docs/swagger.json`;
                    const response = await fetch(url);

                    if (!response.ok) {
                        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
                    }

                    let doc = await response.json();
                    return doc;
                }),
            );
        } catch (error) {
            if (attempt === maxRetries) {
                throw error;
            }
            await new Promise(resolve => setTimeout(resolve, retryDelay));
        }
    }

    throw new Error('Failed to fetch swagger docs after retries');
}

async function buildApis(swagger_docs: Document[]): Promise<{
    hen_api: OpenAPIClient;
    coop_api: OpenAPIClient;
    farm_api: OpenAPIClient;
}> {
    const authHeaders = { Authorization: `Bearer ` };

    const apis: OpenAPIClient[] = await Promise.all(
        swagger_docs.map(async (doc: Document): Promise<OpenAPIClient> => {
            if (!doc.paths || Object.keys(doc.paths).length === 0) {
                throw new Error(`Swagger document for ${doc.info.title} has no paths defined`);
            }

            const api = new OpenAPIClientAxios({
                definition: doc,
                axiosConfigDefaults: { headers: authHeaders },
            });

            return api.init();
        }),
    );

    let result: any = {};
    for (const api of apis) {
        const firstPath = Object.keys(api.paths)[0];
        if (firstPath.includes('hen')) {
            result.hen_api = api;
        } else if (firstPath.includes('coop')) {
            result.coop_api = api;
        } else if (firstPath.includes('farm')) {
            result.farm_api = api;
        }
    }

    return result;
}
