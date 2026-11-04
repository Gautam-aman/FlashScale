
import http from "k6/http";
import exec from "k6/execution";
import { check } from "k6";
import { Rate } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";

const RATE = Number(__ENV.RATE || 25);
const VUS = Number(__ENV.VUS || 100);
const DURATION = __ENV.DURATION || "60s";

const ITEM_ID = Number(__ENV.ITEM_ID || 0);
const RUN_ID = __ENV.RUN_ID;

if (!ITEM_ID || !RUN_ID) {
    throw new Error("ITEM_ID and RUN_ID are required");
}

const reservationSuccess = new Rate("reservation_success");

export const options = {
    scenarios: {
        reservations: {
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
        reservation_success: ["rate==1"],
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
    const iteration = exec.scenario.iterationInTest;
    const uniqueKey = `${RUN_ID}-${iteration}`;

    const payload = JSON.stringify({
        flashSaleItemId: ITEM_ID,
        userId: `user-${uniqueKey}`,
        quantity: 1,
    });

    const response = http.post(
        `${BASE_URL}/api/v1/reservations`,
        payload,
        {
            headers: {
                "Content-Type": "application/json",
                "Idempotency-Key": uniqueKey,
            },
            timeout: "15s",
            tags: {
                operation: "reservation",
            },
        }
    );

    const success = response.status === 201;

    reservationSuccess.add(success);

    check(response, {
        "reservation created": (r) => r.status === 201,
    });
}

