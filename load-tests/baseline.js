
import http from "k6/http";
import exec from "k6/execution";
import { check } from "k6";
import { Rate } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const MODE = __ENV.MODE || "product";
const RATE = Number(__ENV.RATE || 50);
const DURATION = __ENV.DURATION || "60s";
const VUS = Number(__ENV.VUS || 100);

const PRODUCT_ID = Number(__ENV.PRODUCT_ID || 1);
const SALE_ID = Number(__ENV.SALE_ID || 1);
const ITEM_ID = Number(__ENV.ITEM_ID || 0);
const RUN_ID = __ENV.RUN_ID || "baseline";

if (
    !["product", "sale_items", "reserve", "soldout"].includes(MODE)
) {
    throw new Error(`Unsupported MODE: ${MODE}`);
}

if (
    (MODE === "reserve" || MODE === "soldout") &&
    ITEM_ID <= 0
) {
    throw new Error("ITEM_ID is required for reservations");
}

const expectedStatus =
    MODE === "reserve" ? 201 :
        MODE === "soldout" ? 409 :
            200;

// A 409 is an expected business response in sold-out mode.
http.setResponseCallback(
    http.expectedStatuses(expectedStatus)
);

const businessSuccess = new Rate("business_success");

export const options = {
    scenarios: {
        baseline: {
            executor: "constant-arrival-rate",
            rate: RATE,
            timeUnit: "1s",
            duration: DURATION,
            preAllocatedVUs: VUS,
            gracefulStop: "30s",
        },
    },

    summaryTrendStats: [
        "avg",
        "min",
        "med",
        "p(90)",
        "p(95)",
        "p(99)",
        "max",
    ],

    thresholds: {
        business_success: ["rate>0.99"],
        http_req_failed: ["rate<0.01"],
        dropped_iterations: ["count==0"],
    },
};

export default function () {
    let response;

    if (MODE === "product") {
        response = http.get(
            `${BASE_URL}/api/v1/products/${PRODUCT_ID}`
        );

    } else if (MODE === "sale_items") {
        response = http.get(
            `${BASE_URL}/api/v1/sales/${SALE_ID}/items`
        );

    } else {
        // Globally unique across all iterations in this k6 run.
        const uniqueId =
            `${RUN_ID}-${exec.scenario.iterationInTest}`;

        const payload = JSON.stringify({
            flashSaleItemId: ITEM_ID,
            userId: `bench-${uniqueId}`,
            quantity: 1,
        });

        response = http.post(
            `${BASE_URL}/api/v1/reservations`,
            payload,
            {
                headers: {
                    "Content-Type": "application/json",
                    "Idempotency-Key": uniqueId,
                },
                timeout: "30s",
            }
        );
    }

    const correct = response.status === expectedStatus;

    if (!correct && exec.scenario.iterationInTest === 0) {
        console.error(
            `Expected HTTP ${expectedStatus}, got ${response.status} for ${response.url}. ` +
            `Error: ${response.error || "none"}. Body: ${(response.body || "").slice(0, 1000)}`
        );
    }

    businessSuccess.add(correct);

    check(response, {
        "expected HTTP status": (r) =>
            r.status === expectedStatus,
    });
}
