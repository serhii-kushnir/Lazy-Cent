-- V7__add_kushnir_admin.sql
-- Додаємо адміністратора з логіном 'kushnir_s' та паролем 'kushnir123'
-- Пароль захешовано за допомогою BCrypt.
INSERT INTO users (username, password, role, preferred_currency)
VALUES (
           'kushnir_s',
           '$2a$12$AmClTLJDRxshNaruG0ZaOeBa3fXqPzZjVlCFfGweo8iuVKlqgW5Hm', -- Замініть цей хеш на свій (див. пояснення нижче)
           'ADMIN',
           'UAH'
       );