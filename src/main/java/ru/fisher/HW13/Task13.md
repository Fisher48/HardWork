### Абстрагируем управляющие паттерны

#### Пример 1

Было
```java
@Transactional
public ImportResult importFromUrl(String url) throws Exception {

    log.info("Импорт из {}", url);

    Map<String, Category> categoryByXmlId = new HashMap<>();
    ImportContext ctx = new ImportContext();
    
    InputStream is1 = null;
    XMLStreamReader reader1 = null;

    try {
        is1 = new URL(url).openStream();
        reader1 = createReader(is1);

        categoryByXmlId = categoryImporter.importCategories(reader1);

    } catch (Exception e) {
        log.error("Ошибка при импорте категорий", e);
        throw e;
    } finally {
        if (reader1 != null) reader1.close();
        if (is1 != null) is1.close();
    }

    try {
        ctx = prepareContext(categoryByXmlId);
    } catch (Exception e) {
        log.error("Ошибка при подготовке контекста", e);
        throw e;
    }

    InputStream is2 = null;
    XMLStreamReader reader2 = null;

    try {
        is2 = new URL(url).openStream();
        reader2 = createReader(is2);

        while (reader2.hasNext()) {
            reader2.next();
            if (reader2.isStartElement()
                    && reader2.getLocalName().equals("offers")) {
                break;
            }
        }

        offerImporter.importOffers(reader2, ctx);

    } catch (Exception e) {
        log.error("Ошибка при импорте товаров", e);
        throw e;
    } finally {
        if (reader2 != null) reader2.close();
        if (is2 != null) is2.close();
    }

    try {
        flush(ctx);
    } catch (Exception e) {
        log.error("Ошибка при сохранении", e);
        throw e;
    }

    log.info("Импорт завершен");

    return new ImportResult(true, categoryByXmlId.size(), ctx.getProductsToSave().size(), null);
}
```

Стало
```java
@Transactional
public ImportResult importFromUrl(String url) throws Exception {

    log.info("Импорт из {}", url);

    Map<String, Category> categoryByXmlId;

    // -------- ЭТАП 1: категории --------
    try (InputStream is = new URL(url).openStream()) {
        XMLStreamReader reader = createReader(is);
        categoryByXmlId = categoryImporter.importCategories(reader);
    }

    // -------- ЭТАП 2: preload --------
    ImportContext ctx = prepareContext(categoryByXmlId);

    // -------- ЭТАП 3: товары --------
    try (InputStream is = new URL(url).openStream()) {
        XMLStreamReader reader = createReader(is);

        while (reader.hasNext()) {
            reader.next();
            if (reader.isStartElement()
                    && reader.getLocalName().equals("offers")) {
                break;
            }
        }

        offerImporter.importOffers(reader, ctx);
    }

    // -------- ЭТАП 4: batch flush --------
    flush(ctx);

    log.info("Импорт завершен");

    return new ImportResult(true, categoryByXmlId.size(), ctx.getProductsToSave().size(), null);
}
```
Есть метод в сервисе по импорту товаров из ссылки формата xml.
Заменил конструкцию try на try-with-resources, хоть я везде её и так использую, это больше для примера,
где мы избавляемся от блока finally и необходимости следить за закрытием ресурса.
___

#### Пример 2

Было
```java
@ShellMethod(key = "generate-trips-batch", value = "Generate trips for vehicles in bulk")
    public void generateTrips(
            @ShellOption(defaultValue = "5") int vehicleCounts,
            @ShellOption(defaultValue = "5") int tripsPerVehicle,
            @ShellOption String startDate,
            @ShellOption String endDate) {

        List<Vehicle> vehicles = new ArrayList<>();
        for (int i = 0; i < vehicleCounts; i++) {
            Vehicle vehicle = vehicleService.findOne(rand.nextLong(15000) + 1);
            vehicles.add(vehicle);
        }
        log.info("Найдено {} машин для генерации поездок", vehicles.size());

        for (Vehicle vehicle : vehicles) {
            for (int i = 0; i < tripsPerVehicle; i++) {
                boolean success = false;
                while (!success) {
                    try {
                        generateTripForVehicle(vehicle, startDate, endDate);
                        success = true;
                    } catch (TooManyRequestsException e) {
                        log.warn("Превышен лимит запросов. Ожидание 1 минуты...");
                        pauseForOneMinute();
                    } catch (Exception e) {
                        log.error("Ошибка генерации поездки: {}", e.getMessage());
                        break;
                    }
                }
            }
        }
    }
```

Стало
```java
private void retryWithBackoff(Runnable action) {
    while (true) {
        try {
            action.run();
            return;
        } catch (TooManyRequestsException e) {
            log.warn("429 Too Many Requests → retry через минуту");
            pauseForOneMinute();
        } catch (Exception e) {
            log.error("Ошибка: {}", e.getMessage());
            return;
        }
    }
}

for (Vehicle vehicle : vehicles) {
    for (int i = 0; i < tripsPerVehicle; i++) {
        retryWithBackoff(() -> generateTripForVehicle(vehicle, startDate, endDate));
    }
}
```
Код из дипломного проекта генерации поездки для автомобиля. 
Здесь зашито всё в бизнес логике повторные попытки, обработка ошибок при превышении лимита запросов на 1 минуту. 
Я вынес логику повторных попыток в отдельный метод и теперь всё читается гораздо проще.
___

#### Пример 3

Было
```java
@Transactional
public Trip uploadTripFromGpx(Vehicle vehicle, LocalDateTime startTime,
                              LocalDateTime endTime, MultipartFile gpxFile) {

    // Проверяем, что временной диапазон не пересекается с существующими поездками
    if (isTimeRangeOverlapping(vehicle.getId(), startTime, endTime)) {
        throw new IllegalArgumentException("Наложение с существующей поездкой");
    }

    // 1. Парсим точки из GPX
    List<GpsData> gpsDataList = gpxParserService.parseGpxFile(vehicle, gpxFile, startTime, endTime);

    // 2. Создаем Trip (сначала без связки с GPS)
    Trip trip = new Trip();
    trip.setVehicle(vehicle);
    trip.setStartTime(startTime);
    trip.setEndTime(endTime);
    trip.setStartGpsData(gpsDataList.getFirst());
    trip.setEndGpsData(gpsDataList.getLast());
    trip.setMileage(DistanceCalculator.calculateMileageFromGpx(gpsDataList));

    gpsDataService.saveAll(gpsDataList);

    // 3. Сохраняем поездку
    tripRepository.save(trip);

    // 4. Связываем все GPS точки с этой поездкой
    for (GpsData gps : gpsDataList) {
        gps.setTrip(trip);
    }

    // 5. Сохраняем GPS данные
    gpsDataService.saveAll(gpsDataList);

    return trip;
}
```

Стало
```java
// Основной метод создания поездки
private Trip createTripFromGps(Vehicle vehicle, LocalDateTime startTime, 
                               LocalDateTime endTime, List<GpsData> gpsDataList) {

    gpsDataService.saveAll(gpsDataList);

    Trip trip = buildTrip(vehicle, startTime, endTime, gpsDataList);
    tripRepository.save(trip);

    linkGpsToTrip(gpsDataList, trip);
    gpsDataService.saveAll(gpsDataList);

    return trip;
}

// Формируем поездку
private Trip buildTrip(Vehicle vehicle, LocalDateTime startTime,
                       LocalDateTime endTime, List<GpsData> gpsDataList) {

    Trip trip = new Trip();
    trip.setVehicle(vehicle);
    trip.setStartTime(startTime);
    trip.setEndTime(endTime);
    trip.setStartGpsData(gpsDataList.getFirst());
    trip.setEndGpsData(gpsDataList.getLast());
    trip.setMileage(DistanceCalculator.calculateMileageFromGpx(gpsDataList));

    return trip;
}

// Соединяем точки gps с поездкой
private void linkGpsToTrip(List<GpsData> gpsDataList, Trip trip) {
    gpsDataList.forEach(gps -> gps.setTrip(trip));
}

// Итоговый метод уже читаемый и компактный
@Transactional
public Trip uploadTripFromGpx(Vehicle vehicle, LocalDateTime startTime,
                              LocalDateTime endTime, MultipartFile gpxFile) {
    if (isTimeRangeOverlapping(vehicle.getId(), startTime, endTime)) {
        throw new IllegalArgumentException("Наложение с существующей поездкой");
    }
    List<GpsData> gpsDataList = gpxParserService.parseGpxFile(vehicle, gpxFile, startTime, endTime);
    return createTripFromGps(vehicle, startTime, endTime, gpsDataList);
}
```
В методе импорта поездки был такой шаблон в одном методе: Парси точки gps -> затем создаем поездки без привязки к точкам -> 
далее сохраняем поездки и потом -> связываем все GPS точки с этой поездкой и уже в конце -> сохраняем итоговые поезди с точками gps.
Сложная структура и я решил её разделить на логические этапы и вынести отдельно каждый этап, также видоизменил обычный цикл forEach на стрим.