import { defineConfig } from 'vitest/config';

export default defineConfig({
    test: {
        hookTimeout: 120000, // this is what you're missing
        testTimeout: 120000, // optional, for consistency
        fileParallelism: false, // Run test files sequentially to avoid docker port conflicts
        coverage: {
            reportsDirectory: '../../coverage',
            include: ['test/**/*.spec.ts'],
            exclude: ['**/*.d.ts', '**/*.js', '**/coverage/**'],
            all: false,
        },
    },

});