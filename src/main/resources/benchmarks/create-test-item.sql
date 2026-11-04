
WITH new_sale AS (
INSERT INTO flash_sales (
    title,
    starts_at,
    ends_at
)
VALUES (
    'FlashScale Benchmark ' || clock_timestamp()::text,
    NOW() - INTERVAL '1 minute',
    NOW() + INTERVAL '4 hours'
    )
    RETURNING id
    ),
    new_item AS (
INSERT INTO flash_sale_items (
    flash_sale_id,
    product_id,
    sale_price_paise,
    total_stock,
    available_stock
)
SELECT
    s.id,
    p.id,
    p.price_paise,
    (:stock)::integer,
    (:stock)::integer
FROM new_sale s
    CROSS JOIN products p
WHERE p.sku = 'HP-001'
    RETURNING id
    )
SELECT id FROM new_item;
