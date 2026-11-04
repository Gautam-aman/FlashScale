
CREATE TABLE reservations (
                              id UUID PRIMARY KEY,

                              flash_sale_item_id BIGINT NOT NULL
                                  REFERENCES flash_sale_items(id),

                              user_id VARCHAR(100) NOT NULL,

                              idempotency_key VARCHAR(128) NOT NULL,

                              quantity INTEGER NOT NULL
                                  CHECK (quantity BETWEEN 1 AND 5),

                              status VARCHAR(20) NOT NULL DEFAULT 'RESERVED'
                                  CHECK (
                                      status IN (
                                                 'RESERVED',
                                                 'CONFIRMED',
                                                 'CANCELLED',
                                                 'EXPIRED'
                                          )
                                      ),

                              created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                              CONSTRAINT uq_reservation_user_key
                                  UNIQUE (user_id, idempotency_key)
);

CREATE INDEX idx_reservations_item
    ON reservations(flash_sale_item_id);
