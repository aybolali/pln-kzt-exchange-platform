-- Fix sequence to prevent duplicate key errors after cleanup
SELECT setval('exchange_requests_id_seq',
              COALESCE((SELECT MAX(id) FROM exchange_requests), 1),
              true
           );