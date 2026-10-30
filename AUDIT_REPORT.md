# DailyCanvas: полный аудит слабых мест

Дата: 2026-10-27. Аудит выполнен независимым субагентом после исправления пользовательских дефектов. Проверены backend, OpenAPI/Postgres, Android, web frontend и инфраструктура. Аудит был read-only; перечисленные ниже пункты, кроме отдельно выполненных исправлений из основной задачи, не исправлялись.

## P0 — критические

Безусловных P0 в исходниках не обнаружено.

Условный P0 при публикации текущего Compose-контура: API и Postgres выставлены на все интерфейсы с известными учётными данными, предсказуемым JWT-secret и известным demo-администратором (`backend/docker-compose.yml:8,29,35`, `backend/migrations/004_seed_demo_users.sql:6`, `backend/README.md:74`). Если этот compose доступен извне, возможен полный захват аккаунта/API. Production-конфигурация должна отказываться стартовать с default-secret/default-password; demo seed нужен только в dev-профиле, DB-порт — только localhost/internal network.

## P1 — высокие

1. **DOM XSS в web.** Данные картин, тегов, URL и id из API вставляются через `innerHTML` без escaping (`frontend/app.js:137,145,184,251,268`). Токены хранятся в `sessionStorage` (`frontend/app.js:59,104`), поэтому XSS крадёт сессию. Нужны безопасное создание DOM через `textContent`/`setAttribute`, проверка URL и CSP.
2. **Cleartext HTTP в Android release.** Default API URL использует HTTP, а manifest глобально разрешает cleartext (`android/app/build.gradle.kts:17`, `android/app/src/main/AndroidManifest.xml:13`). HTTP следует оставить только в debug network-security-config, release должен требовать HTTPS.
3. **Неатомарная rotation refresh-token.** Active-check, revoke и выпуск нового токена выполняются раздельно (`backend/internal/auth/auth.go:76`, `backend/internal/adapters/postgres/store.go:83,94`). Два параллельных refresh могут оба получить новые пары. Нужна транзакция с conditional update/locking.
4. **Logout и смена пароля не отзывают access JWT.** Уже выданный access token работает до часа (`backend/internal/auth/auth.go:95,102`, `backend/internal/platform/config/config.go:52`, `backend/internal/adapters/http/handler.go:495`). Нужен token version/revocation timestamp либо существенно меньший access TTL.
5. **Неатомарная регистрация.** Пользователь и настройки создаются двумя запросами (`backend/internal/adapters/postgres/store.go:38`). Ошибка второго оставляет сломанный аккаунт, который нельзя зарегистрировать повторно. Нужна единая транзакция.
6. **Галерея/фильтры видят только первые 20 работ.** Android и web строят локальный поиск и значения фильтров по default-странице (`android/.../DailyCanvasApi.kt:41`, `android/.../DailyCanvasRepository.kt:44`, `android/.../DailyCanvasApp.kt:1067`, `frontend/app.js:189,289`). Нужна серверная фильтрация/поиск и пагинация либо загрузка всех страниц.
7. **Некорректная backend-пагинация.** `total` равен размеру текущей страницы, не общему числу (`backend/internal/adapters/http/handler.go:86,121`). Favorites игнорирует `page`, `limit`, `q` (`handler.go:280`, `store.go:363`, `api/openapi.yaml:188`).
8. **Напоминания не реализованы.** Android только сохраняет флаг/время; нет permission/channel, WorkManager/AlarmManager/receiver и серверной доставки (`android/.../DailyCanvasApp.kt:1548`, `DailyCanvasRepository.kt:85`, `AndroidManifest.xml:4`).
9. **Web не обновляет access token.** Refresh token сохраняется, но не используется (`frontend/app.js:58,104`). Startup ищет строку `401`, хотя request уже заменяет её локализованным сообщением (`frontend/app.js:42,398`); после истечения токена UI остаётся сломанным.
10. **Сохранение на Android 8/9 не попадает в пользовательскую галерею.** Ветка API 26–28 пишет в app-specific external directory и не запускает MediaScanner (`android/.../DailyCanvasApp.kt:2277`). Нужен MediaStore/SAF либо legacy permission + scan.

## P2 — средние

1. OpenAPI constraints фактически не валидируются: minimum/maximum, enum, patterns, email, время, timezone, collection и пустой search могут пройти. Нужны OAPI request validator и доменная валидация.
2. Ошибки query/path binding из generated wrapper идут через стандартный `http.Error(err.Error())` как `text/plain` (`backend/.../openapi/server.gen.go:1392`). Нужен собственный `ErrorHandlerFunc` с единым `ErrorResponse`.
3. `decodeJSON` не ограничивает body, допускает неизвестные поля и второй JSON-объект (`backend/internal/adapters/http/handler.go:560`). Нужны `MaxBytesReader`, `DisallowUnknownFields` и проверка EOF.
4. HTTP server не задаёт Read/Write/Idle timeouts (`backend/cmd/api/main.go:44`), что вместе с неограниченным body упрощает resource-exhaustion DoS.
5. Нет rate limiting на login/register/refresh; неизвестный email имеет отличный timing path. Нужны per-IP/account лимиты и dummy bcrypt.
6. Для пароля нет верхней границы; bcrypt отвергает более 72 байт, сейчас это превращается в 500 (`handler.go:195`, `auth.go:49`).
7. Email-валидация различается между backend, Android и browser type=email (`auth.go:145`, `DailyCanvasApp.kt:510`, `frontend/index.html:15`). Один адрес может пройти клиент и быть отклонён сервером.
8. Settings принимают пустые/неизвестные collections и произвольное время/timezone (`settings.go:44`, `store.go:121`); пустой список может привести `/main` к 500 (`store.go:160`, `handler.go:38`).
9. Параллельные PATCH settings делают read-modify-write без блокировки и могут потерять независимые изменения (`settings.go:44`).
10. Calendar не проверяет `from <= to` и не требует параметры парой (`handler.go:249`, `api/openapi.yaml:526`).
11. Favorites расходится с контрактом: повторное добавление делает upsert/201 вместо 409; удаление отсутствующего возвращает успех вместо 404 (`api/openapi.yaml:218`, `store.go:349,358`).
12. Добавление несуществующей картины в favorites даёт FK error → 500 вместо 404 (`handler.go:327`).
13. `GetByID` не фильтрует `active`, поэтому деактивированная работа доступна напрямую (`store.go:240`). Требуется явное правило для истории и каталога.
14. Android при любой ошибке refresh, включая offline/timeout/5xx, очищает сессию (`android/.../NetworkModule.kt:37`). Очищать токены нужно только при окончательном invalid token.
15. Optimistic favorites откатывает снимок всего state и может затереть параллельное успешное изменение другой картины (`DailyCanvasViewModel.kt:163,176`). Откатывать только конкретный id.
16. При открытии некэшированной картины Android временно показывает картину дня под route другого id (`DailyCanvasViewModel.kt:94`). Нужен отдельный loading/null detail state.
17. Android `loadHome()` делает четыре последовательных запроса и роняет весь экран при отказе необязательного блока (`DailyCanvasRepository.kt:42`). Нужны параллельная/независимая загрузка и частичные states.
18. Web `Promise.all` также делает весь UI недоступным при отказе tags/status/settings (`frontend/app.js:286`).
19. Web не имеет logout, password change и account deletion, хотя API и Android их поддерживают (`frontend/app.js:210`).
20. Нет CSP/security headers/frame protection в static router (`backend/internal/adapters/http/router.go:87`).
21. CORS открыт для любого origin (`router.go:103`). Production origins нужно ограничить.
22. `/healthz` не проверяет DB/catalog readiness (`router.go:42`).
23. Compose `depends_on` не ждёт готовности Postgres; нет healthcheck/restart policy (`backend/docker-compose.yml:19`).
24. Dockerfile жёстко собирает amd64, запускает root и копирует backend source в runtime image (`backend/Dockerfile:11,18`).
25. Migration runner не хранит checksum и не имеет общей блокировки между репликами (`backend/internal/platform/postgres/postgres.go:41`).

## P3 — низкие/поддерживаемость

1. Web favorite target 42×42 px меньше распространённого accessibility-минимума 44×44 (`frontend/styles.css:425`). Android target после исправления составляет 52–58 dp.
2. Многие Compose `Text`/`Box` с `clickable` не имеют button role/selected semantics: категории, автор, collection rows (`DailyCanvasApp.kt:1124,668,1963`). Это ухудшает TalkBack.
3. Фиксированная высота gallery card 300 dp может обрезать контент при крупном font scale (`DailyCanvasApp.kt:1407`).
4. Android backup включён без data-extraction rules при encrypted preferences (`AndroidManifest.xml:7`, `SessionStore.kt:9`). Session prefs нужно исключить из backup.
5. Истёкшие/revoked refresh-token rows не очищаются (`backend/internal/adapters/postgres/store.go:75`).
6. `PUBLIC_BASE_URL` и добавленная `role` сейчас не используются (`config.go:28`, `004_seed_demo_users.sql:1`), создавая ложное ожидание absolute URL/RBAC.

## Пробелы тестирования

- Автоматизированное покрытие почти отсутствует: есть только `backend/internal/auth/email_test.go`. Нет handler/store/Postgres/auth-rotation tests, Android unit/instrumentation/Compose tests, web tests, contract/e2e и migration tests.
- `go test ./...` не поднимает Postgres и не проверяет API end-to-end.
- Не выполнялись dependency/CVE scan, нагрузочный тест, automated accessibility scan и аудит реального TLS/production deployment.
- DOM-XSS sink подтверждён; эксплуатация зависит от попадания недоверенных данных в каталог/API.
- Условный P0 зависит от фактической публичности dev Compose-контура.

