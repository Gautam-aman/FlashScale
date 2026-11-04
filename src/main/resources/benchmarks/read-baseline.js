
import http from "k6/http";
import { check } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const ENDPOINT = __ENV.ENDPOINT || "/api/v1/products/1";

const RATE = Number(__ENV.RATE || 100);
const VUS = Number(__ENV.VUS || 100);
const DURATION = __ENV.DURATION || "60s";

export const options = {
    discardResponseBodies: true,

    scenarios: {
        product_reads: {
            executor: "constant-arrival-rate",
            rate: RATE,
            timeUnit: "1s",
            duration: DURATION,
            preAllocatedVUs: VUS,
            gracefulStop: "30s",
        },
    },

    thresholds: {
        http_req_failed: ["rate==0"],
        checks: ["rate==1"],
        dropped_iterations: ["count==0"],
    },

    summaryTrendStats: [
        "avg",
        "med",
        "p(95)",
        "p(99)",
        "max",
    ],
};

export default function () {
    const response = http.get(
        `${BASE_URL}${ENDPOINT}`,
        {
            timeout: "10s",
            tags: {
                operation: "product_read",
            },
        }
    );

    check(response, {
        "GET returns 200": (r) => r.status === 200,
    });
}
