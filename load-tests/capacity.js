// Reuses the open-loop business workload; rejected reservations never count as success.
import { options as baselineOptions } from './baseline.js';
export { default } from './baseline.js';
const rate = Number(__ENV.RATE || 50);
export const options = {
    ...baselineOptions,
    thresholds: {
        business_success: ['rate>0.999'],
        http_req_failed: ['rate<0.001'],
        dropped_iterations: ['count==0'],
        http_reqs: [`rate>=${rate * 0.99}`],
        http_req_duration: [
            `p(95)<${Number(__ENV.P95_MS || 100)}`,
            `p(99)<${Number(__ENV.P99_MS || 250)}`,
        ],
    },
};
