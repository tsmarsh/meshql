module.exports = {
    default: {
        requireModule: ['ts-node/register'],
        require: [
            'test/support/**/*.ts',
            'test/steps/**/*.ts'
        ],
        paths: ['test/features/**/*.feature'],
        format: ['progress', 'json:test-results-bdd.json'],
        formatOptions: { snippetInterface: 'async-await' },
        worldParameters: {
            appUrl: 'http://localhost:3033'
        },
        publishQuiet: true
    }
};
