SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;
SELECT COUNT(*) AS catalogue_packs FROM product_variants;
SELECT COUNT(*) AS local_approved_price_observations FROM approved_price_observations;
SELECT COUNT(*) AS local_legacy_price_observations FROM price_history;
SELECT COUNT(*) AS mapping_decisions FROM provider_mapping_decisions;
SELECT COUNT(*) AS test_users, COUNT(CASE WHEN password_hash LIKE '$2%' THEN 1 END) AS bcrypt_rows FROM users;
WITH owners AS (
 SELECT id, name, ROW_NUMBER() OVER(PARTITION BY name ORDER BY created_at DESC, id DESC) AS recent
 FROM users
 WHERE name IN ('Browser Test Shopper 1440','Browser Test Shopper 1366','Browser Test Shopper 390',
 'API Test Shopper','Isolation Test Shopper','Cart SQL Control')
)
SELECT o.name AS actor,
 (SELECT COUNT(*) FROM shopping_carts c JOIN cart_items i ON i.cart_id=c.id WHERE c.user_id=o.id) AS cart_rows,
 (SELECT MAX(i.quantity) FROM shopping_carts c JOIN cart_items i ON i.cart_id=c.id WHERE c.user_id=o.id) AS cart_quantity,
 (SELECT COUNT(*) FROM user_pantry p WHERE p.user_id=o.id) AS pantry_rows,
 (SELECT MAX(p.amount_base) FROM user_pantry p WHERE p.user_id=o.id AND p.product_id='rice' AND p.base_unit='g') AS rice_g,
 (SELECT COUNT(*) FROM weekly_shopping_plans w WHERE w.user_id=o.id) AS plans,
 (SELECT MAX(w.revision) FROM weekly_shopping_plans w WHERE w.user_id=o.id) AS plan_revision
FROM owners o WHERE recent=1 ORDER BY o.name;
SELECT COUNT(*) AS duplicate_cart_items FROM (
 SELECT cart_id,variant_id FROM cart_items GROUP BY cart_id,variant_id HAVING COUNT(*)>1
) d;
SELECT COUNT(*) AS duplicate_pantry_rows FROM (
 SELECT user_id,product_id FROM user_pantry GROUP BY user_id,product_id HAVING COUNT(*)>1
) d;
SELECT COUNT(*) AS duplicate_weekly_plans FROM (
 SELECT user_id,location_id FROM weekly_shopping_plans GROUP BY user_id,location_id HAVING COUNT(*)>1
) d;
