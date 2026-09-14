# RGS.Center.DB

Скрипты создания PostgreSQL-базы данных для приложения **RGSCenter**.

Проект создаёт отдельные роли PostgreSQL, заново разворачивает базу данных,
создаёт таблицы пользователей и токен-сессий, добавляет начальные роли и
учётные записи, а затем назначает прикладному пользователю необходимые права.

> [!CAUTION]
> Каждый запуск удаляет базу, указанную в `RGSCENTER_DBNAME`, вместе со всеми
> данными и создаёт её заново. Не запускайте скрипты для базы, которую требуется
> сохранить. Перед развёртыванием рабочей базы сделайте резервную копию.

## Требования

- PostgreSQL с доступной утилитой `psql`;
- учётная запись PostgreSQL с правами создавать базы и роли;
- PowerShell 7/Windows PowerShell либо Bash;
- файл `.env` с параметрами подключения и именами создаваемых ролей.

## Состав проекта

| Файл | Назначение |
| --- | --- |
| `000.dev_rgscenter.ps1` | Запуск развёртывания из PowerShell |
| `000.dev_rgscenter.sh` | Запуск развёртывания из Bash, Linux или контейнера |
| `000.rgscenter.tpl` | Главный шаблон: роли PostgreSQL, пересоздание базы и привилегии |
| `010.rgscenter_roles.sql` | Таблица и начальные роли RGSCenter |
| `020.rgscenter_users.sql` | Таблица пользователей |
| `030.rgscenter_auth_sessions.sql` | Серверные токен-сессии и индексы |
| `035.rgscenter_device_settings.sql` | Типизированные настройки подключений приборов |
| `037.rgscenter_device_settings_audit.sql` | Неизменяемая история изменения настроек |
| `040.rgscenter_default_users.sql` | Начальные пользователи приложения |
| `060.rgscenter_measurement_parameters.sql` | Общие параметры автоматических и ручных измерений |
| `070.rgscenter_manual_measurements.sql` | Результаты измерений, выполненных в ручном режиме |
| `080.rgscenter_automatic_measurements.sql` | Результаты объектов автоматических циклов |
| `090.rgscenter_scenario_incidents.sql` | Аварии сценария и подтверждения операторов |

## Настройка `.env`

Создайте в корне клонированного репозитория файл `.env`:

```dotenv
RGSCENTER_POSTGRES_DB_HOST=localhost
RGSCENTER_POSTGRES_DB_PORT=5432
RGSCENTER_POSTGRES_DB_NAME=postgres
RGSCENTER_POSTGRES_DB_ROOTUSER=postgres
RGSCENTER_POSTGRES_DB_ROOTUSER_PASSWORD=change_me

RGSCENTER_DBNAME=rgscenter
RGSCENTER_DEDICATED_USER_NAME=rgscenter
RGSCENTER_DEDICATED_USER_PASSWORD=change_me
RGSCENTER_DEDICATED_APP_USER_NAME=rgscenter1
RGSCENTER_DEDICATED_APP_USER_PASSWORD=change_me
```

Назначение переменных:

| Переменная | Описание |
| --- | --- |
| `RGSCENTER_POSTGRES_DB_HOST` | Хост PostgreSQL |
| `RGSCENTER_POSTGRES_DB_PORT` | Порт PostgreSQL |
| `RGSCENTER_POSTGRES_DB_NAME` | Служебная база для подключения, обычно `postgres` |
| `RGSCENTER_POSTGRES_DB_ROOTUSER` | Администратор, выполняющий развёртывание |
| `RGSCENTER_POSTGRES_DB_ROOTUSER_PASSWORD` | Пароль администратора |
| `RGSCENTER_DBNAME` | База RGSCenter, которая будет пересоздана |
| `RGSCENTER_DEDICATED_USER_NAME` | Владелец базы и объектов |
| `RGSCENTER_DEDICATED_USER_PASSWORD` | Пароль владельца |
| `RGSCENTER_DEDICATED_APP_USER_NAME` | Пользователь Java-приложения |
| `RGSCENTER_DEDICATED_APP_USER_PASSWORD` | Пароль пользователя приложения |

Не добавляйте рабочий `.env` в Git: он содержит пароли.

## Запуск в PowerShell

Если выполнение `.ps1` запрещено системной политикой, запустите скрипт в
отдельном процессе с временным `ExecutionPolicy Bypass`:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\Projects\RGSCenterDB\000.dev_rgscenter.ps1" -EnvFile "C:\Projects\RGSCenterDB\.env"
```

Параметр действует только для запущенного процесса и не изменяет системную
политику PowerShell.

Если `psql.exe` отсутствует в `PATH`, укажите его явно:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\Projects\RGSCenterDB\000.dev_rgscenter.ps1" -EnvFile "C:\Projects\RGSCenterDB\.env" -PsqlPath "C:\Program Files\PostgreSQL\18\bin\psql.exe"
```

## Запуск в Bash

Из корня репозитория:

```bash
RGSCENTER_ENV_FILE="$PWD/.env" ./000.dev_rgscenter.sh
```

При запуске без `-EnvFile` или `RGSCENTER_ENV_FILE` обёртки ищут `.env` на один
уровень выше своего каталога. Явное указание пути удобно для автономного клона
этого репозитория.

## Что выполняется при развёртывании

1. Проверяется наличие обязательных переменных и SQL-файлов.
2. Создаются PostgreSQL-роли владельца и приложения, если они отсутствуют.
3. Пароли обеих ролей синхронизируются со значениями из `.env`.
4. Завершаются активные подключения к целевой базе.
5. Целевая база удаляется и создаётся заново с владельцем
   `RGSCENTER_DEDICATED_USER_NAME`.
6. Создаются таблицы `roles`, `users` и `auth_sessions`.
7. Создаются настройки подключений и журнал их изменений.
8. Добавляются начальные роли и пользователи RGSCenter.
9. Прикладному пользователю назначаются права на подключение и работу с
   данными, включая default privileges для новых таблиц и последовательностей.

При любой ошибке `psql` останавливает выполнение благодаря
`ON_ERROR_STOP=1`.

## Структура данных

### `roles`

Справочник прикладных ролей:

- `service`;
- `administrator`;
- `operator`.

### `users`

Содержит логин, BCrypt-хеш пароля и ссылку на `roles.id`. Пароли в открытом
виде в базе не хранятся.

### `auth_sessions`

Содержит серверные токен-сессии браузеров. В таблице хранится SHA-256-хеш
токена, сроки действия и отзыва, время последней активности, IP-адрес и
User-Agent. Исходный токен хранится только в cookie браузера приложения.

### `device_settings` и `device_settings_audit`

`device_settings` хранит текущие типизированные параметры подключений. Таблица
`device_settings_audit` дописывает отдельную строку для каждого изменения:
предыдущее и новое значение, время и пользователя-администратора. Записи
истории не обновляются и не удаляются приложением.

### `manual_measurements` и `automatic_measurements`

`manual_measurements` хранит итоговую запись ручного измерения.
`automatic_measurements` хранит отдельную строку для каждого объекта
автоматического цикла: координаты, штрих-код, массы, снимок параметров,
ссылку на спектр, итоговый статус и описание ошибки.

## Начальные пользователи приложения

| Логин | Роль | Начальный пароль |
| --- | --- | --- |
| `service` | `service` | `service` |
| `admin` | `administrator` | `admin` |
| `operator` | `operator` | `operator` |

Начальные пароли предназначены только для первого локального запуска. После
развёртывания их необходимо заменить.

## Привилегии PostgreSQL

Роль владельца создаёт базу, схему и все объекты. Прикладная роль получает:

- `CONNECT` и `TEMPORARY` для базы;
- `USAGE` для схемы `public`;
- `SELECT`, `INSERT`, `UPDATE`, `DELETE` для таблиц;
- `USAGE`, `SELECT` для последовательностей;
- аналогичные default privileges для будущих объектов владельца.

Прикладной роли не выдаются права изменять структуру базы.

## Проверка результата

После успешного запуска подключитесь к созданной базе прикладным пользователем:

```bash
psql \
  -h localhost \
  -p 5432 \
  -U rgscenter1 \
  -d rgscenter
```

Проверьте созданные таблицы:

```sql
\dt public.*

SELECT u.id, u.login, r.rolename
FROM public.users u
JOIN public.roles r ON r.id = u.role
ORDER BY u.id;
```

## Рекомендации для production

- используйте уникальные длинные пароли для всех PostgreSQL-ролей;
- ограничьте сетевой доступ к PostgreSQL;
- храните `.env` вне Git и предоставляйте к нему минимальные права доступа;
- создавайте резервную копию перед каждым повторным развёртыванием;
- не запускайте destructive development-скрипт для базы с рабочими данными.
