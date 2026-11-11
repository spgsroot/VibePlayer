# Ридер nhentai

Дата: 2026-11-12. Источник API: [nhentai API v2](https://nhentai.net/api/v2/docs), OpenAPI `https://nhentai.net/api/v2/openapi.json`.

## Цель

Внутри уже открытого PIN-сеанса пользователь ищет галерею, открывает её и листает страницы. Видеоплеер, WebView и Buttplug не меняют поведение.

## Не входит

Избранное, комментарии, загрузка zip, офлайн-библиотека, свои хаптики на перелистывание, хардкод CDN-хоста, вшитый API-ключ.

## Факты API

- Авторизация необязательна для чтения. Заголовок: `Authorization: Key <key>`. Ключ пользователь создаёт в настройках аккаунта nhentai и вводит сам.
- `User-Agent`: `VibePlayer/<versionName> (https://github.com/spgsroot/VibePlayer)`.
- Поиск: `GET /api/v2/search?query=&sort=&page=`. `query` обязателен. `sort`: `date`, `popular`, `popular-today`, `popular-week`, `popular-month`.
- Анонимный поиск: 10 запросов в минуту на IP. С ключом: 20. `429` — пауза, не ретрай в ту же секунду.
- Карточка: `GET /api/v2/galleries/{id}`. Страница — `pages[].path` и `pages[].thumbnail`. Это относительные пути.
- CDN: `GET /api/v2/cdn` → `image_servers` и `thumb_servers`. Полный URL = сервер + `path` как есть. Не угадывать расширение, суффикс или номер. Документация прямо говорит, что такие запросы ведут к бану.
- Архив: `POST /api/v2/galleries/{id}/download`. В первой версии не вызывать.

## Граница с текущим приложением

Новый пакет `ru.spgsroot.vibeplayer.manga`. Он не вызывает `ExoPlayerWrapper`, `CommandSender`, `HapticInputRouter` и `WebViewActivity`.

Навигация: после PIN добавляется маршрут `manga`. `player` и `gallery` остаются. `ShareReceiverActivity` по-прежнему кладёт только `content://` в `PendingImport`.

Ключ хранится в том же DataStore, что и PIN, отдельным ключом `nhentai_api_key`. В логи и backup он не попадает: `sharedpref` и `datastore` уже исключены.

## Задачи

1. `NhentaiApi` на уже подключённом OkHttp. Методы: `cdn()`, `search(query, sort, page)`, `gallery(id)`. Таймаут 15 с. Разбор `429` в отдельную ошибку `RateLimited`.
2. `CdnUrl` — чистая функция `fun image(server: String, path: String): String`. Юнит-тест: сервер и path склеиваются без правки path; пустой path и path с `..` отвергаются.
3. Модели только с полями, которые читает UI: id, title, thumbnail, numPages, pages(number, path, width, height, thumbnail).
4. `MangaViewModel`: поиск, выбранная галерея, текущая страница. Ошибки сети не роняют процесс.
5. `MangaSearchScreen` и `MangaReaderScreen`. Ридер — горизонтальный pager, картинка через Coil, URL только из `CdnUrl`. Жест назад не открывает внешний браузер.
6. Настройки: поле API key и переключатель сортировки по умолчанию. Пустой ключ допустим.
7. `FLAG_SECURE` уже стоит на `MainActivity`. Ридер живёт внутри неё, отдельный флаг не нужен.
8. Проверка: юнит-тесты `CdnUrl` и разбора поиска; ручной смоук — поиск, открытие, листание, возврат в видеоплеер, устройство не получает команд от ридера.

## Риск

API v2 молодой, схема `pages` может дополниться. Парсер должен игнорировать неизвестные поля и не падать, если `pages` пуст: показать «нет страниц», а не собирать URL самому.
