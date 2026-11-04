-- Run with psql -qAt -v ON_ERROR_STOP=1 -v stock=50000.
-- Returns only the new flash-sale item ID for shell command substitution.
WITH test_product AS (
    INSERT INTO products (sku, name, price_paise)
    VALUES ('BENCH-001', 'Benchmark Product', 299900)
    ON CONFLICT (sku) DO UPDATE SET sku = EXCLUDED.sku
    RETURNING id
), new_sale AS (
    INSERT INTO flash_sales (title, starts_at, ends_at)
    VALUES (
        'Reservation benchmark',
        NOW() - INTERVAL '1 minute',
        NOW() + INTERVAL '1 day'
    )
    RETURNING id
)
INSERT INTO flash_sale_items (
    flash_sale_id, product_id, sale_price_paise, total_stock, available_stock
)
SELECT new_sale.id, test_product.id, 199900, :stock, :stock
FROM new_sale CROSS JOIN test_product
RETURNING id;
