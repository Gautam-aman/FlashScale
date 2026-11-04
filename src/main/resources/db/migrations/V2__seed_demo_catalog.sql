
INSERT INTO products (sku, name, price_paise)
VALUES
    ('HP-001', 'Wireless Headphones', 299900),
    ('KB-001', 'Mechanical Keyboard', 499900);

INSERT INTO flash_sales (title, starts_at, ends_at)
VALUES (
           'Diwali Flash Sale',
           NOW() - INTERVAL '1 hour',
           NOW() + INTERVAL '30 days'
       );

INSERT INTO flash_sale_items (
    flash_sale_id,
    product_id,
    sale_price_paise,
    total_stock,
    available_stock
)
SELECT
    fs.id,
    p.id,
    199900,
    100,
    100
FROM flash_sales fs
         JOIN products p ON p.sku = 'HP-001'
WHERE fs.title = 'Diwali Flash Sale';

INSERT INTO flash_sale_items (
    flash_sale_id,
    product_id,
    sale_price_paise,
    total_stock,
    available_stock
)
SELECT
    fs.id,
    p.id,
    349900,
    50,
    50
FROM flash_sales fs
         JOIN products p ON p.sku = 'KB-001'
WHERE fs.title = 'Diwali Flash Sale';
