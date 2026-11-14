// Closed-loop saturation test. Measures delivered throughput, not a fixed arrival rate.
import { options as capacityOptions } from './capacity.js';
export { default } from './baseline.js';
export const options = {
    ...capacityOptions,
    scenarios: {
        throughput: {
            executor: 'constant-vus',
            vus: Number(__ENV.VUS || 50),
            duration: __ENV.DURATION || '60s',
            gracefulStop: '30s',
        },
    },
    thresholds: {
        ...capacityOptions.thresholds,
        dropped_iterations: [],
    },
};
