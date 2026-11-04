
import http from "k6/http";
import exec from "k6/execution";
import { check } from "k6";
import { Counter } from "k6/metrics";

const created = new Counter("reservations_created");
const soldOut = new Counter("reservations_sold_out");
const unexpected = new Counter("reservations_unexpected");

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const ITEM_ID = Number(__ENV.ITEM_ID || 0);
const RUN_ID = __ENV.RUN_ID || "local-test";

if (ITEM_ID <= 0) {
    throw new Error("Set ITEM_ID to a valid test item ID");
}
http.setResponseCallback(
    http.expectedStatuses(201, 409)
);

export const options = {
    scenarios: {
        flash_sale: {
            executor: "shared-iterations",
            vus: Number(__ENV.VUS || 100),
            iterations: Number(__ENV.ITERATIONS || 10000),
            maxDuration: "15m",
        },
    },

    thresholds: {
        checks: ["rate==1"],
        dropped_iterations: ["count==0"],
    },
};

export default function () {

    const iteration = exec.scenario.iterationInTest;
    const uniqueId = `${RUN_ID}-${iteration}`;

    const payload = JSON.stringify({
        flashSaleItemId: ITEM_ID,
        userId: `user-${uniqueId}`,
        quantity: 1,
    });

    const response = http.post(
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

    if (response.status === 201) {
        created.add(1);
    } else if (response.status === 409) {
        soldOut.add(1);
    } else {
        unexpected.add(1);
        console.error(
            `Unexpected: ${response.status} ${response.body}`
        );
    }

    check(response, {
        "reserved or sold out": (r) =>
            r.status === 201 || r.status === 409,
    });
}
