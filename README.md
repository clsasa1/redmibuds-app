# Redmi Buds 8 Pro — экспериментальное приложение

Минимальное Android-приложение (minSdk 31) для управления Redmi Buds 8 Pro (CN).
Протокол (RFCOMM `fd2d`, кадры `FE DC BA … EF`, аутентификация 0x50/0x51) портирован из
[Gadgetbridge](https://codeberg.org/Freeyourgadget/Gadgetbridge) (AGPL-3.0). Есть BLE GATT диагностика,
журнал TX/RX, экспорт GATT карты, обновление через GitHub Releases.

Лицензия: AGPL-3.0-or-later (см. LICENSE, NOTICE.md). Подпись (keystore) в репозиторий не входит —
`signingConfigs.debug` ссылается на `../../debug.keystore`; без него удалите блок signingConfigs.
