# Авторизация RGSCenter

Модуль перенесён на основе модели пользователей BSI Center. Для совместимости
сохраняются поля `appusers.login`, `appusers.scrthsh` (BCrypt),
`appusers.treename` и `appusers.active`. Благодаря этому существующих активных
пользователей BSI можно перенести без сброса паролей.

## Перенос пользователей BSI

Из исходной базы следует переносить только реальные учётные записи с логином и
хешем пароля:

```sql
INSERT INTO appusers
    (treename, login, scrthsh, active, email, role, change_pass_next_login)
VALUES
    (:treename, :login, :scrthsh, :active, :email, :role,
     :change_pass_next_login)
ON CONFLICT (login) DO UPDATE SET
    treename = EXCLUDED.treename,
    scrthsh = EXCLUDED.scrthsh,
    active = EXCLUDED.active,
    email = EXCLUDED.email,
    role = EXCLUDED.role,
    change_pass_next_login = EXCLUDED.change_pass_next_login;
```

Идентификаторы BSI переносить не требуется: токены RGSCenter ссылаются на
локальные идентификаторы пользователей. Группы, дерево подразделений и права
BSI в текущий простой сценарий RGSCenter не входят.

## Токены и подключения

- Браузер получает случайный 256-битный токен в cookie `RGS_AUTH_TOKEN`.
- Cookie имеет флаги `HttpOnly`, `SameSite=Strict` и путь `/`.
- В базе хранится только SHA-256-хеш токена; сырой токен нельзя восстановить.
- Токен действует 8 часов и немедленно отзывается при выходе.
- Подключённым считается браузер, проявлявший активность за последние 90 секунд.
- Интерфейс опрашивает счётчик каждые 30 секунд и показывает предупреждение,
  когда одновременно подключено больше одного браузера.

## Настройки окружения

Подключение к БД можно переопределить переменными `RGSCENTER_DB_URL`,
`RGSCENTER_DB_USER`, `RGSCENTER_DB_PASSWORD`. При работе через HTTPS обязательно
установить `RGSCENTER_COOKIE_SECURE=true`.

Схема создаётся идемпотентным файлом `src/main/resources/schema.sql` при запуске
приложения. Для смены пароля администратора можно использовать BCrypt-хеш,
совместимый с Spring Security и BSI Center.
