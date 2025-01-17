import {init} from "./server.js"
const parser = require("@pushcorn/hocon-parser");
import {Config} from "./configTypes.js"
import yargs from 'yargs';
import { hideBin } from 'yargs/helpers';
import {Application, Express} from "express";

const argv: any = yargs(hideBin(process.argv))
    .option('config', {
        type: 'string',
        description: 'Path to the config file ',
        default: "config/config.conf",
        demandOption: false,
    })
    .parse();

process.env.ENV = "test";
process.env.PREFIX = "farm";
process.env.PLATFORM_URL = "http://localhost:3033";



async function buildApp() {
    let configFile = `${__dirname}/${argv.config}`;
    console.log(`Using config file: ${configFile}`);
    let config = await parser.parse({ url: configFile });

    let app = await init(config);
    await app.listen({port: config.port});
}

buildApp().then(() => {
    console.log("App running")
})