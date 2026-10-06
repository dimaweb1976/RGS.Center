SET client_encoding = 'UTF8';

-- Начальные пароли совпадают с логинами согласно ТЗ. Сохраняются только
-- BCrypt-хэши; после установки пароли необходимо сменить.
INSERT INTO public.users (login, hash, role)
VALUES ('service', '$2a$12$AqNK6H1evUFz9v9WwHEq4OOYFOuRSAmP7p9tCSUnpIDRoxgGLtKQK', 1),
       ('admin', '$2a$12$6A1iQNunjykuPeE5P3Sviepn2ZtKZThHkoghPKcro1MGwoMAniLm6', 2),
       ('operator', '$2a$12$6ThV7BMKVSq.Z/BAkjyYaO3x.LlIoi/VSYdZuJUez5YZ0BuoKKfDu', 3)
ON CONFLICT (login) DO NOTHING;
