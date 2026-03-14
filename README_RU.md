# VibePlayer

<div align="center">

**VibePlayer** — это современный видеоплеер для Android с поддержкой синхронизации с устройствами для взрослых через **Buttplug API**.

[![Platform](https://img.shields.io/badge/platform-Android-green.svg)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.09.00-blue.svg)](https://developer.android.com/jetpack/compose)
[![Buttplug](https://img.shields.io/badge/Buttplug-API-orange.svg)](https://buttplug.io/)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

[🇬🇧 English version](README.md)

</div>

---

## 📱 Скриншоты

<div align="center">

| Главный экран | Галерея |
|:---:|:---:|
| <img src="repo/main.png" width="200" alt="Главный экран"/> | <img src="repo/gallery.png" width="200" alt="Галерея"/> |

| Добавление видео | Настройки |
|:---:|:---:|
| <img src="repo/AddVideo.png" width="200" alt="Добавление видео"/> | <img src="repo/settings.png" width="200" alt="Настройки"/> |

</div>

---

## ✨ Особенности

- **🎬 Локальное воспроизведение** — поддержка различных форматов через ExoPlayer
- **📁 Галерея** — управление библиотекой с переименованием и обложками
- **🔗 Buttplug API** — прямая синхронизация через Buttplug.io
- **📡 Bluetooth** — подключение через Intiface Central
- **⏱️ Таймер** — автоматическое переключение видео
- **🎛️ Скорость** — регулировка от 0.5x до 2.0x
- **🔒 Пароль** — блокировка приложения
- **🌐 Языки** — русский/английский с автоопределением
- **📥 Загрузка** — скачивание видео по URL
- **🎨 Material 3** — современный UI на Jetpack Compose
- **💾 Шифрование** — SQLCipher для защиты данных

---

## 🔌 Интеграция с Buttplug API

VibePlayer использует **Buttplug API** для синхронизации с совместимыми устройствами:

### Поддерживаемые устройства

- **Lovense** — Max, Nora, Lush, Calor, Ferri, Solace и другие
- **WeVibe** — серии Pivot, Connect, Verge, Moxie, Jive
- **Kiiroo** — Pearl, Keon, Onyx, Pulse
- **Satisfyer** — модели с Bluetooth
- **Magic Motion** — совместимые устройства
- **Другие** — любые устройства с поддержкой Buttplug.io

### Как это работает

```
┌─────────────────┐     Bluetooth      ┌─────────────────┐
│   VibePlayer    │ ◄────────────────► │   Устройство    │
│   (Android)     │                    │  (Lovense и др.)│
└────────┬────────┘                    └─────────────────┘
         │
         │ Buttplug Protocol
         ▼
┌─────────────────┐
│ Intiface Central│
│   (Сервер)      │
└─────────────────┘
```

1. **Intiface Central** запускается на ПК или мобильном устройстве
2. VibePlayer подключается через **Bluetooth**
3. Видео синхронизируется с устройством через **Buttplug Protocol**
4. Интенсивность регулируется автоматически на основе воспроизведения

### Команды Buttplug

| Команда | Описание |
|---------|----------|
| `DeviceScan` | Сканирование nearby устройств |
| `DeviceConnect` | Подключение к выбранному устройству |
| `StopAllDevices` | Остановка всех устройств |
| `SingleMotorVibrateCmd` | Управление интенсивностью вибрации |
| `BatteryLevelCmd` | Проверка заряда батареи |

### Преимущества Buttplug API

- ✅ **Единый протокол** для всех поддерживаемых устройств
- ✅ **Открытая спецификация** — полная документация
- ✅ **Активное сообщество** — регулярные обновления и поддержка
- ✅ **Кроссплатформенность** — работает на Android, iOS, PC

---

## 🛠️ Технологический стек

| Категория | Технология |
|-----------|------------|
| **Язык** | Kotlin 2.0.21 |
| **UI фреймворк** | Jetpack Compose, Material Design 3 |
| **Архитектура** | MVVM, Clean Architecture |
| **DI** | Hilt |
| **База данных** | Room + SQLCipher |
| **Асинхронность** | Coroutines, Flow |
| **Навигация** | Navigation Compose |
| **Сеть** | OkHttp |
| **Медиа плеер** | ExoPlayer (Media3) |
| **Загрузка изображений** | Coil |
| **Протокол устройств** | Buttplug Android Library |

---

## 📋 Требования

- **Android 8.0 (API 26)** или выше
- **Android 13 (API 33)** рекомендуется для полной поддержки локализации

### Для синхронизации Buttplug

- **Intiface Central** (ПК или мобильное приложение)
- **Совместимое Bluetooth устройство**
- **Разрешение Bluetooth** предоставлено

---

## 🚀 Установка

### Из APK

1. **Скачайте APK** из раздела [Releases](https://github.com/spgsroot/VibePlayer/releases)
2. **Включите установку из неизвестных источников** в настройках устройства
3. **Установите APK** и запустите приложение
4. **Для Buttplug:** установите [Intiface Central](https://intiface.com/central/)

### Из Google Play

> Скоро...

---

## 🔧 Сборка из исходников

```bash
# Клонируйте репозиторий
git clone https://github.com/spgsroot/VibePlayer.git
cd VibePlayer

# Откройте в Android Studio или соберите через командную строку
./gradlew assembleDebug

# APK будет создан в app/build/outputs/apk/debug/
```

### Требования для сборки

- Android Studio Hedgehog или новее
- JDK 11 или выше
- Android SDK 36

---

## 📖 Руководство по использованию

### Быстрый старт с Buttplug

#### 1. Настройка Intiface Central

- Скачайте и установите [Intiface Central](https://intiface.com/central/)
- Запустите приложение на ПК
- Включите Bluetooth сервер в настройках

#### 2. Подключение устройства

- Откройте VibePlayer на Android устройстве
- Перейдите в **Настройки → Подключение устройства**
- Нажмите **Начать сканирование**
- Выберите ваше устройство из списка найденных

#### 3. Запуск воспроизведения

- Добавьте видео из галереи или вставьте URL
- Нажмите для начала воспроизведения
- Устройство синхронизируется автоматически с видео

#### 4. Настройка синхронизации

- Откройте меню **Настройки**
- Настройте **таймер автопереключения** для автоматического воспроизведения
- Отрегулируйте **скорость воспроизведения** (0.5x - 2.0x)
- Установите предпочтительный **язык**

### Добавление видео

| Способ | Описание |
|--------|----------|
| **Галерея** | Импорт видео из хранилища устройства |
| **URL** | Вставить прямую ссылку на видео |
| **Импорт списка** | Импорт нескольких URL одновременно |

---

## 🌐 Языки

Приложение поддерживает два языка с автоматическим определением:

| Язык | Опция |
|------|-------|
| **Системный** | Следует языку устройства |
| **Русский** | Принудительно русский интерфейс |
| **English** | Принудительно английский интерфейс |

Изменить язык можно в **Настройки → Язык**.

---

## 🔒 Безопасность

- **🔐 Пароль приложения** — PIN-код защита от несанкционированного доступа
- **🔒 SQLCipher** — 256-битное AES шифрование базы данных
- **🛡️ Secure Storage** — Android Keystore для чувствительных данных
- **📱 Минимальные разрешения** — запрашиваются только необходимые разрешения

---

## 📁 Структура проекта

```
VibePlayer/
├── app/
│   ├── src/main/
│   │   ├── java/ru/spgsroot/vibeplayer/
│   │   │   ├── data/
│   │   │   │   ├── db/              # Room database + SQLCipher
│   │   │   │   ├── repository/      # Репозитории данных
│   │   │   │   ├── downloader/      # Сервис загрузки видео
│   │   │   │   └── storage/         # Управление файлами
│   │   │   ├── device/
│   │   │   │   └── buttplug/        # Интеграция Buttplug API
│   │   │   ├── domain/
│   │   │   │   └── model/           # Модели бизнес-логики
│   │   │   ├── ui/
│   │   │   │   ├── player/          # Экран видеоплеера
│   │   │   │   ├── gallery/         # Экран галереи
│   │   │   │   ├── settings/        # Экран настроек
│   │   │   │   ├── auth/            # Экран авторизации
│   │   │   │   ├── dialog/          # Диалоговые компоненты
│   │   │   │   └── onboarding/      # Экран онбординга
│   │   │   ├── di/                  # Hilt dependency injection
│   │   │   ├── locale/              # Менеджер локализации
│   │   │   └── security/            # Авторизация и шифрование
│   │   └── res/
│   │       ├── values/              # Русские строки
│   │       └── values-en/           # Английские строки
│   └── build.gradle.kts
└── build.gradle.kts
```

---

## 🔗 Полезные ссылки

### Ресурсы Buttplug

- [**Buttplug.io Official**](https://buttplug.io/) — Официальная документация
- [**Intiface Central**](https://intiface.com/central/) — Сервер подключения
- [**Список устройств**](https://buttplug.io/docs/devices/) — Поддерживаемые устройства
- [**API Reference**](https://buttplug.io/docs/) — Документация API
- [**Discord сообщество**](https://discord.gg/9jRg3qf) — Помощь и общение

### Разработка

- [**Jetpack Compose**](https://developer.android.com/jetpack/compose)
- [**Hilt**](https://developer.android.com/training/dependency-injection/hilt-android)
- [**Room Database**](https://developer.android.com/training/data-storage/room)

---

## 🤝 Вклад в проект

Вклад приветствуется! Вот как вы можете помочь:

1. **Сообщить об ошибке** — Создайте issue с подробным описанием
2. **Предложить функцию** — Поделитесь идеями для улучшений
3. **Улучшить переводы** — Помогите с локализацией приложения
4. **Добавить поддержку устройств** — Внесите конфигурации устройств Buttplug
5. **Отправить PR** — Присылайте pull requests для исправлений и функций

### Настройка разработки

```bash
# Форкните и клонируйте
git clone https://github.com/spgsroot/VibePlayer.git
cd VibePlayer

# Создайте ветку
git checkout -b feature/ваша-функция

# Внесите изменения и закоммитьте
git commit -m "Add: описание вашей функции"

# Отправьте и создайте PR
git push origin feature/ваша-функция
```

---

## 📄 Лицензия

**Лицензия MIT** — свободное использование, модификация и распространение.

```
Copyright (c) 2024 VibePlayer

Разрешение предоставляется любому лицу, бесплатно получившему копию
этого программного обеспечения и сопутствующих документов, иметь дело
в Программном обеспечении без ограничений, включая без ограничения права
использовать, копировать, изменять, объединять, публиковать, распространять,
сублицензировать и/или продавать копии Программного обеспечения.
```

---

## 💖 Поддержка

Если вам нравится этот проект и вы хотите поддержать его разработку, вы можете сделать пожертвование через TON:

**TON:** `UQCGFymEHFNq1IcIhXBWJJe7Ha7Cx7RU6apvotRs5DcEEAaG`

Каждый взнос помогает поддерживать и развивать этот проект! ❤️

---

## 📞 Контакты

- **GitHub:** [@spgsroot](https://github.com/spgsroot)
- **Email:** aqu.de@yandex.ru

---

<div align="center">

**VibePlayer** © 2026

Работает на **[Buttplug.io](https://buttplug.io/)**

Сделано с ❤️ используя Kotlin & Jetpack Compose

</div>
