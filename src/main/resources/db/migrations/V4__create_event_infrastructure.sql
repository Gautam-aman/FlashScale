
CREATE TABLE outbox_events (
                               event_id UUID PRIMARY KEY,

                               reservation_id UUID NOT NULL
                                   REFERENCES reservations(id),

                               payload TEXT NOT NULL,

                               created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                               claimed_until TIMESTAMPTZ,

                               published_at TIMESTAMPTZ,

                               attempts INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_outbox_pending
    ON outbox_events(created_at)
    WHERE published_at IS NULL;

CREATE TABLE processed_events (
                                  consumer_name VARCHAR(100) NOT NULL,
                                  event_id UUID NOT NULL,
                                  processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                                  PRIMARY KEY (consumer_name, event_id)
);

CREATE TABLE reservation_projections (
                                         reservation_id UUID PRIMARY KEY,

                                         flash_sale_item_id BIGINT NOT NULL,
                                         quantity INTEGER NOT NULL,

                                         state VARCHAR(30) NOT NULL,

                                         processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
