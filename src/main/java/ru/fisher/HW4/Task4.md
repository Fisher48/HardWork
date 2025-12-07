#### Увидеть ясную структуру дизайна
В данном задании требуется сформулировать логический дизайн для 3 примеров своего кода из рабочего проекта (несколько сотен строк).
Я постарался найти пару примеров, где можно попробовать его проработать в рамках этого задания.
Ознакомившись с материалом из 3-й части цикла _"Три уровня рассуждений о программной системе"_, было понятно что подразумевается именно в заключительной части статьи.
Хочу подчеркнуть еще раз, что я пока в поиске работы и могу искать только в дипломном проекте, но там не так много сервисов или кусков кода под несколько сотен строк).

#### 1-й пример Сервис генерации поездок для машин **_BulkTripGenerator_**:

```java
@ShellComponent
@Slf4j
@RequiredArgsConstructor
public class BulkTripGenerator {

    @Value("${kafka.topic.notifications}")
    private String topic;

    private final GpsDataService gpsDataService;
    private final VehicleService vehicleService;
    private final TripService tripService;
    private final WebClient webClient;
    private final Random rand = new Random();

    @Value("${openrouteservice.api.key}")
    public String key;

    @Value("${openrouteservice.url}")
    public String openRouteUrl;

    @Value("${openrouteservice.url.snap}")
    public String openRouteSnap;

    private static final double EARTH_RADIUS = 6371;

    // Область для генерации точек (Липецк)
    private static final double LIPETSK_MIN_LAT = 51.0;
    private static final double LIPETSK_MAX_LAT = 53.0;
    private static final double LIPETSK_MIN_LON = 38.0;
    private static final double LIPETSK_MAX_LON = 40.5;

    private static final double MOSCOW_MIN_LAT = 55.334;
    private static final double MOSCOW_MAX_LAT = 56.025;
    private static final double MOSCOW_MIN_LON = 37.321;
    private static final double MOSCOW_MAX_LON = 38.100;

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

    private void generateTripForVehicle(Vehicle vehicle, String startDate, String endDate) {
        log.info("Генерация трека для машины с id: {}", vehicle.getId());
        double[] startCoordinates = generateRandomPointInArea
                (MOSCOW_MIN_LAT, MOSCOW_MAX_LAT, MOSCOW_MIN_LON, MOSCOW_MAX_LON);
        double randomDistance = 25 + rand.nextDouble() * 75; // Расстояние от 50 до 100 км
        LocalDateTime tripStartTime = generateRandomDateBetween(startDate, endDate);

        int maxRetries = 50;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                double[] endCoordinates = generateCoordinatesByDistance(
                        startCoordinates[0], startCoordinates[1], randomDistance);

                String route = callOpenRouteService(startCoordinates[0], startCoordinates[1],
                        endCoordinates[0], endCoordinates[1]);

                saveTripWithTrack(route, vehicle, tripStartTime, startCoordinates, endCoordinates);
                return;
            } catch (NotFoundException e) {
                randomDistance -= 1;
                if (randomDistance < 1) {
                    throw new RuntimeException("Не удалось сгенерировать поездку: минимальное расстояние достигнуто.");
                }
            }
        }
        throw new RuntimeException("Превышено количество попыток генерации поездки.");
    }

    private String callOpenRouteService(double sourceLat, double sourceLon,
                                        double targetLat, double targetLon) {
        String body = "{\"coordinates\":[[" + sourceLon + "," + sourceLat + "]," +
                "[" + targetLon + "," + targetLat + "]]}";
        log.info("Запрос к OpenRouteService: {}", body);

        return webClient.post()
                .uri(openRouteUrl)
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> {
                    if (response.statusCode().value() == 429) {
                        return Mono.error(new TooManyRequestsException("Превышен лимит запросов (429 Too Many Requests)"));
                    } else if (response.statusCode().value() == 404) {
                        return Mono.error(new NotFoundException("Маршрут не найден (404 Not Found)"));
                    }
                    return Mono.error(new RuntimeException("Ошибка API: " + response.statusCode()));
                })
                .bodyToMono(String.class)
                .block();
    }

    private double[] generateRandomPointInArea(double minLat, double maxLat, double minLon, double maxLon) {
        double latitude = minLat + (maxLat - minLat) * rand.nextDouble();
        double longitude = minLon + (maxLon - minLon) * rand.nextDouble();
        log.info("Сгенерирована начальная точка: [latitude={}, longitude={}]", latitude, longitude);
        return new double[]{latitude, longitude};
    }

    private double[] generateCoordinatesByDistance(double startLat, double startLon, double distanceKm) {
        double angle = 2 * Math.PI * rand.nextDouble();
        double deltaLat = Math.toDegrees(distanceKm / EARTH_RADIUS);
        double deltaLon = Math.toDegrees(distanceKm / (EARTH_RADIUS * Math.cos(Math.toRadians(startLat))));

        double endLat = startLat + deltaLat * Math.cos(angle);
        double endLon = startLon + deltaLon * Math.sin(angle);

        log.info("Сгенерирована конечная точка: [latitude={}, longitude={}]", endLat, endLon);
        return new double[]{endLat, endLon};
    }

    private void saveTripWithTrack(String routeJson, Vehicle vehicle, LocalDateTime startTime,
                                   double[] startCoordinates, double[] endCoordinates) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode features = objectMapper.readTree(routeJson).path("features");
            if (!features.isArray() || features.isEmpty()) throw new RuntimeException("Маршрут пуст");

            List<GpsData> gpsDataList = new ArrayList<>();
            GeometryFactory geometryFactory = new GeometryFactory();
            LocalDateTime localDateTime = startTime;

            for (JsonNode coordinateNode : features.get(0).path("geometry").path("coordinates")) {
                double longitude = coordinateNode.get(0).asDouble();
                double latitude = coordinateNode.get(1).asDouble();

                GpsData gpsData = new GpsData();
                gpsData.setVehicle(vehicle);
                Point point = geometryFactory.createPoint(new Coordinate(longitude, latitude));
                gpsData.setCoordinates(point);
                gpsData.setTimestamp(localDateTime);
                gpsDataList.add(gpsData);
                localDateTime = localDateTime.plusSeconds(10);
            }

            // 1. Сохраняем GPS-данные без trip
            gpsDataService.saveAll(gpsDataList);

            BigDecimal distanceKm = DistanceCalculator.calculateDistance(startCoordinates[0], startCoordinates[1],
                    endCoordinates[0], endCoordinates[1]);

            // 2. Создаём поездку с уже сохранёнными точками
            Trip trip = new Trip();
            trip.setVehicle(vehicle);
            trip.setStartTime(startTime);
            trip.setEndTime(localDateTime);
            trip.setStartGpsData(gpsDataList.getFirst());
            trip.setEndGpsData(gpsDataList.getLast());
            trip.setMileage(distanceKm);

            // Сохраняем поездку
            tripService.save(trip);

            // 3. Привязываем trip ко всем GPS-данным и обновляем
            for (GpsData gps : gpsDataList) {
                gps.setTrip(trip);
            }

            // Сохраняем GPS-данные с поездкой
            gpsDataService.saveAll(gpsDataList); // второй вызов обновляет

            log.info("Поездка сохранена. Машина ID: {}, расстояние: {} км", vehicle.getId(), distanceKm);
        } catch (Exception e) {
            log.error("Ошибка при сохранении поездки: {}", e.getMessage());
        }
    }

    private void pauseForOneMinute() {
        try {
            Thread.sleep(60000); // 1 минута
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private LocalDateTime generateRandomDateBetween(String startDate, String endDate) {
        // Преобразуем строки в LocalDate
        LocalDate startLocalDate = LocalDate.parse(startDate);
        LocalDate endLocalDate = LocalDate.parse(endDate);

        // Преобразуем LocalDate в LocalDateTime (начало дня)
        long startEpoch = startLocalDate.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        long endEpoch = endLocalDate.atStartOfDay(ZoneOffset.UTC).toEpochSecond();

        // Генерация случайного времени между двумя датами
        long randomEpoch = startEpoch + (long) (Math.random() * (endEpoch - startEpoch));
        return LocalDateTime.ofEpochSecond(randomEpoch, 0, ZoneOffset.UTC);
    }

    private static class TooManyRequestsException extends RuntimeException {
        public TooManyRequestsException(String message) {
            super(message);
        }
    }

    private static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }

}
```

Логический дизайн данного кода:
Данный сервис предназначен для генерации поездок для машин.
Он используется для массовой загрузки данных (bulk data generation) и работает через команду оболочки Spring Shell.
1. Входные параметры
Команда generate-trips-batch принимает:
- количество автомобилей vehicleCounts; 
- количество поездок на каждое авто tripsPerVehicle;
- диапазон дат startDate / endDate, в пределах которых производится случайная генерация времени начала поездки.

2. Получение автомобилей
- Для каждого из vehicleCounts генерируется случайный ID.
- Из VehicleService извлекаются реальные сущности Vehicle.
- Все выбранные автомобили помещаются в список.
Автомобили выбираются случайно (в пределах существующих ID).

3. Генерация поездок для каждого автомобиля
- Для каждого автомобиля генерируется tripsPerVehicle поездок.
- Каждая поездка создаётся с возможностью нескольких попыток (retry), если OpenRouteService возвращает ошибки.

4. Генерация маршрута
Для очередной поездки выполняются следующие шаги:

- Определение стартовой точки:
Случайная точка берётся в заранее определённом прямоугольном регионе (например, Москва).

- Генерация конечной точки:
Определяется случайная дистанция (в диапазоне 50–100 км).
По случайному углу рассчитывается конечная точка на сфере.

- Запрос маршрута к OpenRouteService:
Отправляется POST-запрос c двумя координатами: начальной и конечной.

- Обрабатываются ошибки:
429 — превышение лимита, генерация делает паузу и повторяется;
404 — маршрут не найден, уменьшается дистанция и повторяется попытка.

5. Сохранение поездки
После получения маршрута:
JSON с координатами разбирается.
Для каждой точки маршрута создаётся объект GpsData:
с координатами, ссылкой на автомобиль, timestamp (увеличивается каждые 10 секунд).
Список GPS точек сохраняется в базу без привязки к поездке.
Создаётся объект Trip со всеми необходимыми полями и сохраняется в БД.
Все GPS-точки обновляются, им присваивается tripId.

Если посмотреть на дизайн и на код. То кажется что он ему соответствует, и логика прослеживается.
Но проблема в том что этот код я создавал и знал что он должен делать и представлял еше на этапе проектирования.
Поэтому мне кажется что все понятно, но если бы кто-то другой посмотрел не понимая дизайн просто на код ему возможно будет сложнее.

```java

@Getter
@AllArgsConstructor
public enum GenerationArea {
    MOSCOW(55.334, 56.025, 37.321, 38.100),
    LIPETSK(51.0, 53.0, 38.0, 40.5);

    private final double minLat;
    private final double maxLat;
    private final double minLon;
    private final double maxLon;
}

@ShellComponent
@Slf4j
@RequiredArgsConstructor
public class BulkTripGenerator {

    private final GpsDataService gpsDataService;
    private final VehicleService vehicleService;
    private final TripService tripService;
    private final WebClient webClient;
    private final Random rand = new Random();

    @Value("${openrouteservice.api.key}")
    public String key;

    @Value("${openrouteservice.url}")
    public String openRouteUrl;

    @Value("${openrouteservice.url.snap}")
    public String openRouteSnap;

    private static final double EARTH_RADIUS = 6371;

    // Входные параметры
    @ShellMethod(key = "generate-trips-batch", value = "Generate trips for vehicles in bulk")
    public void generateTrips(
            @ShellOption(defaultValue = "5") int vehicleCount,
            @ShellOption(defaultValue = "5") int tripsPerVehicle,
            @ShellOption String startDate,
            @ShellOption String endDate) {

        List<Vehicle> vehicles = findRandomVehicles(vehicleCount);
        log.info("Найдено {} машин", vehicles.size());

        for (Vehicle vehicle : vehicles) {
            generateTripsForVehicle(vehicle, tripsPerVehicle, startDate, endDate);
        }
    }

    // Поиск и получение автомобилей
    private List<Vehicle> findRandomVehicles(int count) {
        List<Vehicle> vehicles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            vehicles.add(vehicleService.findOne(rand.nextLong(15000) + 1));
        }
        return vehicles;
    }

    // Генерация поездок для одной машины
    private void generateTripsForVehicle(Vehicle vehicle, int tripsCount,
                                         String startDate, String endDate) {
        for (int i = 0; i < tripsCount; i++) {
            generateOneTripWithRetry(vehicle, startDate, endDate);
        }
    }

    // Генерация для одной поездки (с повторными попытками)
    private void generateOneTripWithRetry(Vehicle vehicle, String startDate, String endDate) {
        int maxAttempts = 5;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                generateOneTrip(vehicle, startDate, endDate);
                return; // Успех - выходим

            } catch (TooManyRequestsException e) {
                log.warn("Слишком много запросов (попытка {}). Ждем...", attempt);
                if (attempt == maxAttempts) {
                    log.error("Не удалось после {} попыток", maxAttempts);
                    throw e;
                }
                pauseForOneMinute(); // Ждем минуту

            } catch (Exception e) {
                log.error("Ошибка: {}", e.getMessage());
                break; // Для других ошибок не повторяем
            }
        }
    }

    // Генерация одной поездки
    private void generateOneTrip(Vehicle vehicle, String startDate, String endDate) {
        log.info("Генерация для машины {}", vehicle.getId());

        // Генерируем точки
        double[] startPoint = generateRandomPoint(MOSCOW);
        double distance = 25 + rand.nextDouble() * 75; // 25-100 км
        double[] endPoint = generatePointAtDistance(startPoint[0], startPoint[1], distance);

        LocalDateTime startTime = generateRandomTime(startDate, endDate);

        // Получаем маршрут
        String routeJson = callRouteApi(startPoint, endPoint);

        // Сохраняем
        saveTrip(routeJson, vehicle, startTime, startPoint, endPoint);
    }

    // Генерация случайной точки в области
    private double[] generateRandomPoint(GenerationArea area) {
        double lat = area.getMinLat() + (area.getMaxLat() - area.getMinLat()) * rand.nextDouble();
        double lon = area.getMinLon() + (area.getMaxLon() - area.getMinLon()) * rand.nextDouble();
        log.debug("Точка в {}: {}, {}", area, lat, lon);
        return new double[]{lat, lon};
    }

    // Получение конечной точки
    private double[] generatePointAtDistance(double startLat, double startLon, double distanceKm) {
        double angle = 2 * Math.PI * rand.nextDouble();
        double deltaLat = Math.toDegrees(distanceKm / EARTH_RADIUS);
        double deltaLon = Math.toDegrees(distanceKm / (EARTH_RADIUS * Math.cos(Math.toRadians(startLat))));

        return new double[]{
                startLat + deltaLat * Math.cos(angle),
                startLon + deltaLon * Math.sin(angle)
        };
    }

    // Запрос маршрута вызов (API)
    private String callRouteApi(double[] start, double[] end) {
        String body = String.format(
                "{\"coordinates\":[[%f,%f],[%f,%f]]}",
                start[1], start[0], // OpenRoute хочет lon,lat
                end[1], end[0]
        );

        return webClient.post()
                .uri(openRouteUrl)
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(status -> status.value() == 429,
                        response -> Mono.error(new TooManyRequestsException("429")))
                .onStatus(status -> status.value() == 404,
                        response -> Mono.error(new NotFoundException("404")))
                .bodyToMono(String.class)
                .block();
    }

    // Сохранение поездки
    private void saveTrip(String routeJson, Vehicle vehicle, LocalDateTime startTime,
                          double[] startCoords, double[] endCoords) {
        try {
            // Парсим JSON
            ObjectMapper mapper = new ObjectMapper();
            JsonNode features = mapper.readTree(routeJson).path("features");
            if (features.isEmpty()) throw new RuntimeException("Нет маршрута");

            // Создаем GPS точки
            List<GpsData> gpsPoints = createGpsPoints(
                    features.get(0).path("geometry").path("coordinates"),
                    vehicle,
                    startTime
            );

            // Сохраняем поездку
            saveTripWithPoints(vehicle, startTime, gpsPoints, startCoords, endCoords);

        } catch (Exception e) {
            log.error("Ошибка сохранения: {}", e.getMessage());
            throw new RuntimeException("Не удалось сохранить поездку", e);
        }
    }

    // Создание GPS точек
    private List<GpsData> createGpsPoints(JsonNode coordinatesNode,
                                          Vehicle vehicle, LocalDateTime startTime) {
        List<GpsData> points = new ArrayList<>();
        GeometryFactory factory = new GeometryFactory();
        LocalDateTime time = startTime;

        for (JsonNode node : coordinatesNode) {
            GpsData gps = new GpsData();
            gps.setVehicle(vehicle);

            double lon = node.get(0).asDouble();
            double lat = node.get(1).asDouble();
            gps.setCoordinates(factory.createPoint(new Coordinate(lon, lat)));

            gps.setTimestamp(time);
            points.add(gps);

            time = time.plusSeconds(10); // +10 секунд между точками
        }

        return points;
    }

    // Сохранение поездки с точками
    private void saveTripWithPoints(Vehicle vehicle, LocalDateTime startTime,
                                    List<GpsData> gpsPoints,
                                    double[] startCoords, double[] endCoords) {
        // 1. Сохраняем точки без поездки
        gpsDataService.saveAll(gpsPoints);

        // 2. Считаем расстояние
        BigDecimal distance = DistanceCalculator.calculateDistance(
                startCoords[0], startCoords[1],
                endCoords[0], endCoords[1]
        );

        // 3. Создаем поездку
        Trip trip = new Trip();
        trip.setVehicle(vehicle);
        trip.setStartTime(startTime);
        trip.setEndTime(gpsPoints.getLast().getTimestamp());
        trip.setStartGpsData(gpsPoints.getFirst());
        trip.setEndGpsData(gpsPoints.getLast());
        trip.setMileage(distance);

        tripService.save(trip);

        // 4. Привязываем точки к поездке
        gpsPoints.forEach(gps -> gps.setTrip(trip));
        gpsDataService.saveAll(gpsPoints);

        log.info("Сохранено: машина {}, расстояние {} км", vehicle.getId(), distance);
    }

    // Вспомогательные методы
    private LocalDateTime generateRandomTime(String startDate, String endDate) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);

        long startEpoch = start.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        long endEpoch = end.atStartOfDay(ZoneOffset.UTC).toEpochSecond();

        long randomEpoch = startEpoch + (long)(Math.random() * (endEpoch - startEpoch));
        return LocalDateTime.ofEpochSecond(randomEpoch, 0, ZoneOffset.UTC);
    }

    private void pauseForOneMinute() {
        try {
            Thread.sleep(60000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Обработка ошибок
    private static class TooManyRequestsException extends RuntimeException {
        public TooManyRequestsException(String message) {
            super(message);
        }
    }

    private static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }

}
```
И так, вот какие улучшения этого сервиса я реализовал, в соответствии с дизайном:
На первый взгляд изменения не значительные и не все сразу бросится в глаза,
но по большей части я разбил некоторые сложные (комплексные) методы на более мелкие с одной логикой работы.
- Поиск машин (findRandomVehicles)
- Генерация поездок для машины (generateTripsForVehicle)
- Генерация одной поездки с повторными попытками (generateOneTripWithRetry)
- Генерация точки и маршрута (generateRandomPoint, generatePointAtDistance, callRouteApi)
- Сохранение GPS и поездки (saveTrip, createGpsPoints, saveTripWithPoints)

Всё это делает код более декларативным и его проще читать - каждый метод делает одно, логично названо.
Также я вынес константы под зоны (координаты) где генерируются поездки (MOSCOW, LIPETSK).
В класс GenerationArea, что тоже в свою очередь делает код более гибким.

Итерация заняла около 5 часов. Большая часть времени уходит на дизайн.
Возможно в первом примере я слишком увлекся дизайном как кодом. И как будто старался подогнать дизайн под код.
Сложно было выбросить из головы уже воплощенное решение и абстрагироваться от написанного кода.
Вопрос в том, как нужно описывать дизайн исходя из того как должен работать код или что он должен делать.
Но читаю теория нужно описывать КАК работает, а не ЧТО делает. Вроде бы это перекливается между собой.
Задание очень сложное, но дает мозгам поразмыслить особенно после TDD. 
Как раз TDD и облегчает все эти этапы формирования дизайна системы как спецификации и не приходилось бы думать:
_"А что в действительности должен делать это код?"_.
___

### 2-й пример это GeoCoderService - Сервис отвечает за геокодирование и обратное геокодирование через внешние API (OpenRouteService и Яндекс)

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class GeoCoderService {

    @Value("${yandex.api.key}")
    private String apiKey;

    @Value("${openrouteservice.api.key}")
    private String openRouteApiKey;

    private final ObjectMapper objectMapper;
    private final WebClient webClient;


    /**
     * Получение координат по адресу через OpenRouteService API
     */
    public Map<String, Double> getCoordinatesFromOpenRouteService(String address) {
        try {
            // Выполняем GET-запрос
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("api.openrouteservice.org")
                            .path("/geocode/search/")
                            .queryParam("api_key", openRouteApiKey)
                            .queryParam("text", address)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.info("Ответ от OpenRouteService API: {}", response);

            // Парсим JSON-ответ
            JsonNode root = objectMapper.readTree(response);
            JsonNode features = root.path("features");

            if (features.isArray() && !features.isEmpty()) {
                JsonNode geometry = features.get(0).path("geometry");
                double longitude = geometry.path("coordinates").get(0).asDouble();
                double latitude = geometry.path("coordinates").get(1).asDouble();

                Map<String, Double> result = new HashMap<>();
                result.put("lat", latitude);
                result.put("lon", longitude);
                return result;
            } else {
                log.warn("Координаты не найдены для адреса: {}", address);
                return null;
            }
        } catch (Exception e) {
            log.error("Ошибка при вызове OpenRouteService API: {}", e.getMessage());
            return null;
        }
    }


    /**
     * Получение адреса по координатам через Яндекс Геокодер API
     */
    public String getAddressFromYandexGeo(double latitude, double longitude) {
        try {
            // Выполняем GET-запрос
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("geocode-maps.yandex.ru")
                            .path("/1.x/")
                            .queryParam("apikey", apiKey)
                            .queryParam("format", "json")
                            .queryParam("geocode", longitude + "," + latitude)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.info("Ответ от Яндекс Геокодер API: {}", response);

            // Парсим JSON-ответ
            JsonNode rootNode = objectMapper.readTree(response);

            JsonNode geoObjectCollection = rootNode.path("response")
                    .path("GeoObjectCollection")
                    .path("featureMember");

            if (geoObjectCollection.isArray() && !geoObjectCollection.isEmpty()) {
                JsonNode geoObject = geoObjectCollection.get(0).path("GeoObject");
                return geoObject.path("metaDataProperty")
                        .path("GeocoderMetaData")
                        .path("text").asText();
            } else {
                log.warn("Адрес не найден для координат: {}, {}", latitude, longitude);
                return "Address not found";
            }
        } catch (Exception e) {
            log.error("Ошибка при вызове Яндекс Геокодера: {}", e.getMessage());
            return "Address not found";
        }
    }

    /**
     * Получение адреса по координатам через OpenRouteService API
     */
    public String getAddressFromOpenRouteService(double latitude, double longitude) {
        log.info("Отправляем запрос на обратное геокодирование: {}",
                "&point.lon=" + longitude + "&point.lat=" + latitude);

        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("api.openrouteservice.org")
                            .path("/geocode/reverse/")
                            .queryParam("api_key", openRouteApiKey)
                            .queryParam("format", "json")
                            .queryParam("point.lon", longitude)
                            .queryParam("point.lat", latitude)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.info("Ответ от openrouteservice: {}", response);

            JsonNode root = objectMapper.readTree(response);
            JsonNode features = root.path("features");
            if (features.isArray() && !features.isEmpty()) {
                return features.get(0)
                        .path("properties")
                        .path("label")
                        .asText("Address not found");
            }
        } catch (Exception e) {
            log.error("Ошибка при получении адреса: {}", e.getMessage());
        }
        return "Address not found";
    }

    public Mono<String> getAddressFromOpenRouteServiceReactive(double latitude, double longitude) {
        log.info("Отправляем запрос на обратное геокодирование (реактивно): {}",
                "&point.lon=" + longitude + "&point.lat=" + latitude);
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("api.openrouteservice.org")
                        .path("/geocode/reverse/")
                        .queryParam("api_key", openRouteApiKey)
                        .queryParam("format", "json")
                        .queryParam("point.lon", longitude)
                        .queryParam("point.lat", latitude)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(response -> {
                    try {
                        JsonNode root = objectMapper.readTree(response);
                        JsonNode features = root.path("features");
                        if (features.isArray() && !features.isEmpty()) {
                            return Mono.just(features.get(0)
                                    .path("properties")
                                    .path("label")
                                    .asText("Address not found"));
                        } else {
                            return Mono.just("Address not found");
                        }
                    } catch (Exception e) {
                        log.error("Ошибка при разборе JSON: {}", e.getMessage());
                        return Mono.just("Address not found");
                    }
                })
                .onErrorResume(e -> {
                    log.error("Ошибка при получении адреса: {}", e.getMessage());
                    return Mono.just("Address not found");
                });
    }

}
```

Логический дизайн данного кода:  
Здесь конечно все проще это прямое и обратное геокодирование координат.
В зависимости от того какой внешний сервис используется (Яндекс или ORS).
Уже вижу что тут не очень корректно выглядит. Это то, что здесь просто 3 разных метода для прямого и
обратного геокодирования, в которых обычный парсинг координат и получения адреса.
Если считать что дизайн здесь очень простой.
- Получить координаты от OpenRouteService.
- Получить адрес от OpenRouteService.
- Получить адрес от OpenRouteService (реактивно)
- Получить адрес от Яндекса.

Что я изменил в этом примере:  
```java
@Service
@Slf4j
@RequiredArgsConstructor
public class GeoCoderService {

    private final OpenRouteGeocoderClient ors;
    private final YandexGeocoderClient yandex;

    // Получение координат и адреса через разные сервисы

    public Map<String, Double> getCoordinatesFromOpenRouteService(String address) {
        return ors.geocode(address).block();
    }

    public String getAddressFromYandexGeo(double lat, double lon) {
        return yandex.reverseGeocode(lat, lon).block();
    }

    public String getAddressFromOpenRouteService(double lat, double lon) {
        return ors.reverseGeocode(lat, lon).block();
    }

    // Реактивное получение адреса в ORS

    public Mono<String> getAddressFromOpenRouteServiceReactive(double lat, double lon) {
        return ors.reverseGeocode(lat, lon);
    }

}

@Component
@RequiredArgsConstructor
@Slf4j
public class BaseHttpClient {

  private final WebClient webClient;
  private final ObjectMapper objectMapper;

  public Mono<JsonNode> getJson(Consumer<UriBuilder> uriBuilder) {
    return webClient.get()
            .uri(uriBuilder.toString())
            .retrieve()
            .bodyToMono(String.class)
            .flatMap(body -> {
              try {
                return Mono.just(objectMapper.readTree(body));
              } catch (Exception e) {
                log.error("Ошибка парсинга JSON: {}", e.getMessage());
                return Mono.empty();
              }
            })
            .onErrorResume(e -> {
              log.error("HTTP ошибка: {}", e.getMessage());
              return Mono.empty();
            });
  }
}

@Component
@RequiredArgsConstructor
@Slf4j
public class OpenRouteGeocoderClient {

  @Value("${openrouteservice.api.key}")
  private String apiKey;

  private final BaseHttpClient http;


  public Mono<Map<String, Double>> geocode(String address) {
    return http.getJson(uri -> uri
                    .scheme("https")
                    .host("api.openrouteservice.org")
                    .path("/geocode/search/")
                    .queryParam("api_key", apiKey)
                    .queryParam("text", address))
            .mapNotNull(root -> {
              JsonNode features = root.path("features");
              if (features.isArray() && !features.isEmpty()) {
                JsonNode coords = features.get(0)
                        .path("geometry")
                        .path("coordinates");

                Map<String, Double> result = new HashMap<>();
                result.put("lon", coords.get(0).asDouble());
                result.put("lat", coords.get(1).asDouble());
                return result;
              }
              log.warn("Адрес не найден: {}", address);
              return null;
            });
  }


  public Mono<String> reverseGeocode(double lat, double lon) {
    return http.getJson(uri -> uri
                    .scheme("https")
                    .host("api.openrouteservice.org")
                    .path("/geocode/reverse/")
                    .queryParam("api_key", apiKey)
                    .queryParam("format", "json")
                    .queryParam("point.lon", lon)
                    .queryParam("point.lat", lat))
            .map(root -> {
              JsonNode features = root.path("features");
              if (features.isArray() && !features.isEmpty()) {
                return features.get(0)
                        .path("properties")
                        .path("label")
                        .asText("Address not found");
              }
              return "Address not found";
            })
            .defaultIfEmpty("Address not found");
  }
}

@Component
@RequiredArgsConstructor
@Slf4j
public class YandexGeocoderClient {

  @Value("${yandex.api.key}")
  private String apiKey;

  private final BaseHttpClient http;


  public Mono<String> reverseGeocode(double lat, double lon) {
    return http.getJson(uri -> uri
                    .scheme("https")
                    .host("geocode-maps.yandex.ru")
                    .path("/1.x/")
                    .queryParam("apikey", apiKey)
                    .queryParam("format", "json")
                    .queryParam("geocode", lon + "," + lat))
            .map(root -> {
              JsonNode fm = root.path("response")
                      .path("GeoObjectCollection")
                      .path("featureMember");

              if (fm.isArray() && !fm.isEmpty()) {
                return fm.get(0)
                        .path("GeoObject")
                        .path("metaDataProperty")
                        .path("GeocoderMetaData")
                        .path("text")
                        .asText("Address not found");
              }
              return "Address not found";
            })
            .defaultIfEmpty("Address not found");
  }
}
```

Каковы изменения (улучшения):
- Теперь, если смотреть на основной сервис Геокодирования, то он чисто соответствует дизайну (даже строго).
- Основной блок BaseHttpClient - общая логика, единый вызов WebClient и парсинг Json.
- Все блоки запроса вынесены в отдельные сервисы (Разделены на ORS + Yandex).
- Код компактен и декларативен.

Итерация по 2-му примеру заняла порядка 2-3 часов.
Здесь при просмотре кода, я сразу заметил что я просто натыкал разные методы в зависимости от выбора
обработчика (сервиса) координат. Была спешка, как обычно, и я не думал что нужно все аккуратно разделять.
Использовать паттерны проектирования и т.д. Плюс использование GPT при разработке очень сильно расслабляет мозг.
И ты начинаешь просто на него возлагать весь код, думая что он тебе выдает уже готовые решения. 
Но по мере роста кодовой базы и получается знаменитый _"Big Ball of Mud"_.
Поэтому стоит аккуратно использовать gpt и стараться действовать самому (лучше тебя самого никто не сделает).

___
 
#### 3-й пример TelegramBot - для обработки команд от менеджера и формирования отчета о пробегах автомобиля, предприятия и общий.

```java
@Component
@Slf4j
@RequiredArgsConstructor
public class TelegramBot extends TelegramLongPollingBot {

    private final TelegramBotConfig botConfig;
    private final ManagerService managerService;
    private final ReportService reportService;
    private final EnterpriseService enterpriseService;
    private final VehicleService vehicleService;

    // userId -> Manager
    private final Map<Long, Manager> authorizedUsers = new HashMap<>();

    // Чат -> контекст запроса
    private final Map<Long, ReportRequestContext> sessionContext = new HashMap<>();

    @PostConstruct
    public void registerCommands() {
        try {
            List<BotCommand> commands = List.of(
                    new BotCommand("/start", "Запустить бота"),
                    new BotCommand("/help", "Показать команды"),
                    new BotCommand("/login", "Авторизация логин:пароль"),
                    new BotCommand("/logout", "Выход из системы"),
                    new BotCommand("/report", "Сформировать отчёт")
            );
            execute(new SetMyCommands(commands, new BotCommandScopeDefault(), null));
        } catch (Exception e) {
            log.error("Ошибка регистрации команд", e);
        }
    }

    private boolean ensureAuthorized(Long chatId) {
        if (getAuthorizedManager(chatId) == null) {
            sendMessage(chatId, "❌ Вы не авторизованы. Введите /login логин:пароль.");
            return false;
        }
        return true;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            handleMessage(update.getMessage().getChatId(), update.getMessage().getText());
        } else if (update.hasCallbackQuery()) {
            handleCallback(update.getCallbackQuery().getMessage().getChatId(),
                    update.getCallbackQuery().getData());
        }
    }

    private void handleMessage(Long chatId, String text) {
        if (text.startsWith("/start")) {
            handleStart(chatId);
        } else if (text.startsWith("/login")) {
            handleLogin(chatId, text);
        } else if (text.equals("/help")) {
            sendHelp(chatId);
        } else {
            // 🔒 проверяем авторизацию перед любыми другими действиями
            if (!ensureAuthorized(chatId)) return;

            if (text.equals("/logout")) {
                Manager manager = getAuthorizedManager(chatId);
                if (manager != null) {
                    managerService.updateManagerChatId(manager.getId(), null); // очистка в БД
                }
                authorizedUsers.remove(chatId);
                sessionContext.remove(chatId);
                sendMessage(chatId, "Вы вышли из системы. ");
            } else if (text.equals("/report")) {
                sessionContext.put(chatId, new ReportRequestContext());
                sendReportTypeSelection(chatId);
            } else {
                handleStep(chatId, text);
            }
        }
    }

    private void handleStart(Long chatId) {
        sendMessage(chatId, "🚗 Добро пожаловать в VehiclePark Bot!\n\n" +
                "Для работы с системой используйте команды:\n" +
                "/login - для авторизация\n" +
                "/help - список команд");
    }

    private void sendHelp(Long chatId) {
        String helpText = """
            📋 Доступные команды:

            /login логин:пароль - авторизация
            /logout - выход из системы
            /report - сформировать отчет

            📊 Формирование отчетов:
            - По машине (введите гос. номер авто)
            - По предприятию (выберите из списка)
            - Общий отчет
            """;
        sendMessage(chatId, helpText);
    }

    private void sendReportTypeSelection(Long chatId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(
                List.of(
                        InlineKeyboardButton.builder().text("🚗 По машине").callbackData("report_vehicle").build(),
                        InlineKeyboardButton.builder().text("\uD83C\uDFE2 По предприятию").callbackData("report_enterprise").build(),
                        InlineKeyboardButton.builder().text("\uD83D\uDCCA Общий").callbackData("report_total").build()
                )
        ));
        sendMessage(chatId, "Выберите тип отчета:", markup);
    }

    private void handleLogin(Long chatId, String messageText) {
        try {
            String[] parts = messageText.split(" ", 2);
            if (parts.length < 2) {
                sendMessage(chatId, "Используйте формат: /login логин:пароль");
                return;
            }
            String[] creds = parts[1].split(":");
            if (creds.length != 2) {
                sendMessage(chatId, "Используйте формат: логин:пароль");
                return;
            }
            String username = creds[0];
            String password = creds[1];

            // Аутентификация
            Manager manager = managerService.authenticate(username, password);

            // ✅ Обновляем chatId для менеджера
            managerService.updateManagerChatId(manager.getId(), chatId);

            // ✅ Обновляем объект менеджера
            manager.setChatId(chatId);

            // ✅ Добавляем в сессию
            authorizedUsers.put(chatId, manager);

            sendMessage(chatId, manager.getUsername() + ", вы успешно авторизовались! ✅");
            log.info("Менеджер {} авторизован, chatId сохранён: {}", username, chatId);

        } catch (Exception e) {
            sendMessage(chatId, "❌ Ошибка авторизации: " + e.getMessage());
            log.error("Ошибка авторизации", e);
        }
    }

    private void handleStep(Long chatId, String text) {
        ReportRequestContext ctx = sessionContext.get(chatId);

        if (managerService.getManagerByChatId(chatId).isEmpty()) {
            sendMessage(chatId, "Сначала авторизуйтесь /login");
            return;
        }

        if (ctx == null && managerService.getManagerByChatId(chatId).isPresent()) {
            sendMessage(chatId, "Команды в боте /help");
            return;
        }

        if (ctx == null) {
            sendMessage(chatId, "Сначала выполните /report");
            return;
        }

        try {
            switch (ctx.getState()) {
                case VEHICLE_WAITING_NUMBER -> {
                    ctx.setVehicleNumber(text);
                    ctx.setState(BotState.PERIOD_SELECTION);
                    sendPeriodSelection(chatId);
                }
                case ENTERPRISE_WAITING_NAME -> {
                    ctx.setEnterpriseName(text);
                    ctx.setState(BotState.PERIOD_SELECTION);
                    sendPeriodSelection(chatId);
                }
                case PERIOD_SELECTION -> {
                    ctx.setPeriod(parsePeriod(text));
                    ctx.setState(BotState.WAITING_START_DATE);
                    sendMessage(chatId, "Введите начальную дату (формат yyyy-MM-dd или yyyy-MM-ddTHH:mm):");
                }
                case WAITING_START_DATE -> {
                    ctx.setStartDate(parseDate(text));
                    ctx.setState(BotState.WAITING_END_DATE);
                    sendMessage(chatId, "Введите конечную дату:");
                }
                case WAITING_END_DATE -> {
                    ctx.setEndDate(parseDate(text));
                    generateAndSendReport(chatId, ctx);
                    sessionContext.remove(chatId);
                }
                default -> sendMessage(chatId, "⚠ Неожиданное состояние. Введите /report заново.");
            }
        } catch (Exception e) {
            sendMessage(chatId, "❌ Ошибка: " + e.getMessage());
        }
    }

    private void sendPeriodSelection(Long chatId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(
                List.of(
                        InlineKeyboardButton.builder().text("День").callbackData("period_day").build(),
                        InlineKeyboardButton.builder().text("Месяц").callbackData("period_month").build(),
                        InlineKeyboardButton.builder().text("Год").callbackData("period_year").build()
                )
        ));
        sendMessage(chatId, "Выберите период отчета:", markup);
    }

    private void handleCallback(Long chatId, String data) {
        if (!ensureAuthorized(chatId)) return;

        ReportRequestContext ctx = sessionContext.computeIfAbsent(chatId, k -> new ReportRequestContext());

        if (data.startsWith("enterprise_")) {
            try {
                Long enterpriseId = Long.parseLong(data.substring("enterprise_".length()));
                Enterprise enterprise = enterpriseService.findById(enterpriseId);
                ctx.setEnterpriseName(enterprise.getName());
                ctx.setState(BotState.PERIOD_SELECTION);
                sendPeriodSelection(chatId);
            } catch (Exception e) {
                sendMessage(chatId, "❌ Ошибка выбора предприятия: " + e.getMessage());
            }
        } else if (data.startsWith("period_")) {
            try {
                String period = data.substring("period_".length());
                ctx.setPeriod(parsePeriod(period));
                ctx.setState(BotState.WAITING_START_DATE);
                sendMessage(chatId, "Введите начальную дату (формат yyyy-MM-dd или yyyy-MM-ddTHH:mm):");
            } catch (Exception e) {
                sendMessage(chatId, "❌ Ошибка выбора периода: " + e.getMessage());
            }
        }
        else {
            switch (data) {
                case "report_vehicle" -> {
                    ctx.setType(VEHICLE_MILEAGE);
                    ctx.setState(BotState.VEHICLE_WAITING_NUMBER);
                    sendMessage(chatId, "Введите номер машины:");
                }
                case "report_enterprise" -> {
                    ctx.setType(ENTERPRISE_MILEAGE);
                    ctx.setState(BotState.ENTERPRISE_WAITING_NAME);
                    sendEnterpriseSelection(chatId);
                }
                case "report_total" -> {
                    ctx.setType(TOTAL_MILEAGE);
                    ctx.setState(BotState.PERIOD_SELECTION);
                    sendPeriodSelection(chatId);
                }
                default -> sendMessage(chatId, "Неизвестный выбор: " + data);
            }
        }
    }

    private Manager getAuthorizedManager(Long chatId) {
        Manager manager = authorizedUsers.get(chatId);
        if (manager == null) {
            manager = managerService.getManagerByChatId(chatId).orElse(null);
            if (manager != null) {
                authorizedUsers.put(chatId, manager); // восстановим в память
            }
        }
        return manager;
    }

    private void sendEnterpriseSelection(Long chatId) {
        Manager manager = getAuthorizedManager(chatId);
        if (!ensureAuthorized(chatId)) return;

        List<Enterprise> enterprises = enterpriseService.findAllForManager(manager.getId());

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = enterprises.stream()
                .map(e -> List.of(
                        InlineKeyboardButton.builder().text(e.getName())
                                .callbackData("enterprise_" + e.getId()).build()))
                .toList();
        markup.setKeyboard(rows);
        sendMessage(chatId, "Выберите предприятие:", markup);
    }


    private LocalDateTime parseDate(String input) {
        try {
            return new DateTimeFormatterBuilder()
                    .appendPattern("yyyy[-MM[-dd['T'HH[:mm]]]]")
                    .parseDefaulting(ChronoField.MONTH_OF_YEAR, 1)
                    .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                    .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
                    .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
                    .toFormatter()
                    .parse(input, LocalDateTime::from);
        } catch (Exception e) {
            throw new IllegalArgumentException("Неверный формат даты: " + input);
        }
    }

    private void generateAndSendReport(Long chatId, ReportRequestContext ctx) {
        try {
            Manager manager = getAuthorizedManager(chatId);
            if (!ensureAuthorized(chatId)) return;

            MileageReportDTO reportDTO;
            switch (ctx.getType()) {
                case VEHICLE_MILEAGE -> {
                    Vehicle vehicle = vehicleService.findVehicleByNumber(ctx.getVehicleNumber())
                            .orElseThrow(() -> new VehicleNotFoundException(
                                    "Машина с " + ctx.getVehicleNumber() + " номером, не существует"));
                    reportDTO = reportService.generateMileageReport(
                            manager, vehicle.getNumber(), ctx.getStartDate(), ctx.getEndDate(), ctx.getPeriod());
                }
                case ENTERPRISE_MILEAGE -> {
                    Enterprise enterprise = enterpriseService.findByName(ctx.getEnterpriseName())
                            .orElseThrow(() -> new IllegalArgumentException("Предприятие не найдено"));
                    reportDTO = reportService.generateEnterpriseMileageReport(
                            manager, enterprise.getId(), ctx.getStartDate(), ctx.getEndDate(), ctx.getPeriod());
                }
                case TOTAL_MILEAGE -> {
                    reportDTO = reportService.generateTotalMileageReport(
                            manager, ctx.getStartDate(), ctx.getEndDate(), ctx.getPeriod());
                }
                default -> throw new IllegalArgumentException("Неизвестный тип отчета");
            }
            sendMessage(chatId, formatReport(reportDTO));
        } catch (Exception e) {
            sendMessage(chatId, "❌ Ошибка при генерации отчета: " + e.getMessage());
            log.error("Ошибка генерации отчета", e);
        }
    }

    private Period parsePeriod(String raw) {
        return switch (raw.toLowerCase()) {
            case "day" -> Period.DAY;
            case "month" -> Period.MONTH;
            case "year" -> Period.YEAR;
            default -> throw new IllegalArgumentException("Неизвестный период: " + raw);
        };
    }

    private String formatReport(MileageReportDTO report) {
        StringBuilder sb = new StringBuilder();
        sb.append("📊 ").append(report.getReportType()).append("\n");
        sb.append("⏱ Период: ").append(report.getPeriod()).append("\n");
        sb.append("🔄 С ").append(report.getStartDate()).append(" по ")
                .append(report.getEndDate()).append("\n\n");

        report.getResults().forEach((key, value) ->
                        sb.append(key).append(": ").append(value).append(" км\n"));
        return sb.toString();
    }

    private void sendMessage(Long chatId, String text) {
        sendMessage(chatId, text, null);
    }

    private void sendMessage(Long chatId, String text, InlineKeyboardMarkup markup) {
        try {
            SendMessage msg = new SendMessage();
            msg.setChatId(chatId.toString());
            msg.setText(text);
            if (markup != null) msg.setReplyMarkup(markup);
            execute(msg);
        } catch (Exception e) {
            log.error("Ошибка отправки сообщения", e);
        }
    }

    @Override
    public String getBotUsername() {
        return botConfig.getBotName();
    }

    @Override
    public String getBotToken() {
        return botConfig.getToken();
    }

}
```

Как описать логический дизайн телеграмм бота не вчитываясь в текущий код.  
Это сервис, который обрабатывает команды от менеджера и взаимодействует с приложением, в зависимости от запросов.
- В основную задачу ТГ бота входит выдача отчета о пробегах автомобиля, предприятия и общий
- При этом должен быть зарегистрированный менеджер, который логинится и может после этого посылать команды боту.
- ТГ бот принимает сообщения, определяет какое оно и передает обработчику событий.
- Обработчик событий (handler) обрабатывает команды в зависимости от выбора.
- Каждый обработчик отвечает за отправку или получение сообщения.

Если не вдаваться в подробности работы ТГ бота, то дизайн очень прост и здесь нет того, что на самом деле уже
излишне усложняет логику работы ТГ бота.

Вот изменения после рефакторинга кода на основе описанного дизайна:
```java
@Component
@Slf4j
@AllArgsConstructor
public class TelegramBot extends TelegramLongPollingBot implements MessageSender {

    private final TelegramBotConfig botConfig;
    private final BotMessageProcessor messageProcessor;
    private final BotCallbackProcessor callbackProcessor;

    @PostConstruct
    public void init() {
        registerBotCommands();
        messageProcessor.setMessageSender(this);
        callbackProcessor.setMessageSender(this);
    }

    private void registerBotCommands() {
        try {
            List<BotCommand> commands = List.of(
                    new BotCommand("/start", "Запустить бота"),
                    new BotCommand("/help", "Помощь"),
                    new BotCommand("/login", "Войти (логин:пароль)"),
                    new BotCommand("/logout", "Выйти"),
                    new BotCommand("/report", "Отчёт")
            );
            execute(new SetMyCommands(commands, new BotCommandScopeDefault(), null));
        } catch (Exception e) {
            log.error("Не удалось зарегистрировать команды", e);
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                messageProcessor.processMessage(
                        update.getMessage().getChatId(),
                        update.getMessage().getText()
                );
            } else if (update.hasCallbackQuery()) {
                callbackProcessor.processCallback(
                        update.getCallbackQuery().getMessage().getChatId(),
                        update.getCallbackQuery().getData()
                );
            }
        } catch (Exception e) {
            log.error("Ошибка обработки обновления", e);
        }
    }

    @Override
    public void sendText(Long chatId, String text) {
        try {
            execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .build());
        } catch (Exception e) {
            log.error("Ошибка отправки текста в чат {}", chatId, e);
        }
    }

    @Override
    public void sendKeyboard(Long chatId, String text, List<List<InlineKeyboardButton>> buttons) {
        sendKeyboard(chatId, text, InlineKeyboardMarkup.builder().keyboard(buttons).build());
    }

    @Override
    public void sendKeyboard(Long chatId, String text, InlineKeyboardMarkup keyboard) {
        try {
            execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .replyMarkup(keyboard)
                    .build());
        } catch (Exception e) {
            log.error("Ошибка отправки клавиатуры в чат {}", chatId, e);
        }
    }

    @Override
    public String getBotUsername() {
        return botConfig.getBotName();
    }

    @Override
    public String getBotToken() {
        return botConfig.getToken();
    }

}

public interface MessageSender {
  void sendText(Long chatId, String text);
  void sendKeyboard(Long chatId, String text, List<List<InlineKeyboardButton>> buttons);
  void sendKeyboard(Long chatId, String text, InlineKeyboardMarkup keyboard);
}

@Component
@RequiredArgsConstructor
@Slf4j
public class BotCallbackProcessor {

  private final AuthContextService authService;
  private final ReportFlowService reportFlowService;
  @Setter
  private MessageSender messageSender;


  public void processCallback(Long chatId, String callbackData) {
    log.debug("Обработка callback от {}: {}", chatId, callbackData);

    if (!authService.isAuthorized(chatId)) {
      messageSender.sendText(chatId, "Сначала войдите: /login");
      return;
    }

    reportFlowService.processCallbackInput(chatId, callbackData, messageSender);
  }
}

@Component
@Slf4j
@RequiredArgsConstructor
public class BotMessageProcessor {

  private final AuthContextService authService;
  private final ReportFlowService reportFlowService;
  @Setter
  private MessageSender messageSender;

  public void processMessage(Long chatId, String text) {
    log.debug("Обработка сообщения от {}: {}", chatId, text);

    if (text.startsWith("/")) {
      handleCommand(chatId, text);
    } else {
      handleUserInput(chatId, text);
    }
  }

  private void handleCommand(Long chatId, String commandText) {
    String[] parts = commandText.split(" ", 2);
    String command = parts[0];
    String args = parts.length > 1 ? parts[1] : "";

    switch (command) {
      case "/start" -> sendWelcome(chatId);
      case "/help" -> sendHelp(chatId);
      case "/login" -> handleLogin(chatId, args);
      case "/logout" -> handleLogout(chatId);
      case "/report" -> startReport(chatId);
      default -> messageSender.sendText(chatId, "Неизвестная команда. Используйте /help");
    }
  }

  private void handleUserInput(Long chatId, String text) {
    if (!authService.isAuthorized(chatId)) {
      messageSender.sendText(chatId, "Сначала войдите: /login логин:пароль");
      return;
    }

    reportFlowService.processTextInput(chatId, text, messageSender);
  }

  private void sendWelcome(Long chatId) {
    String text = """
            🚗 Добро пожаловать в VehiclePark Bot!
            
            Для работы используйте команды:
            /login - авторизация
            /help - помощь
            /report - формирование отчетов
            """;
    messageSender.sendText(chatId, text);
  }

  private void sendHelp(Long chatId) {
    String text = """
            📋 Доступные команды:
            
            /login логин:пароль - вход в систему
            /logout - выход
            /report - отчет о пробеге
            
            📊 Типы отчетов:
            • По машине (введите гос. номер)
            • По предприятию (выберите из списка)
            • Общий отчет
            """;
    messageSender.sendText(chatId, text);
  }

  private void handleLogin(Long chatId, String args) {
    try {
      String[] credentials = args.split(":");
      if (credentials.length != 2) {
        messageSender.sendText(chatId, "Используйте: /login логин:пароль");
        return;
      }

      authService.login(chatId, credentials[0], credentials[1]);
      messageSender.sendText(chatId, "✅ Авторизация успешна!");

    } catch (Exception e) {
      messageSender.sendText(chatId, "❌ Ошибка: " + e.getMessage());
    }
  }

  private void handleLogout(Long chatId) {
    authService.logout(chatId);
    messageSender.sendText(chatId, "Вы вышли из системы");
  }

  private void startReport(Long chatId) {
    if (!authService.isAuthorized(chatId)) {
      messageSender.sendText(chatId, "Сначала войдите: /login");
      return;
    }

    reportFlowService.startReportFlow(chatId, messageSender);
  }
}


@Component
public class ReportSessionService {

  private final Map<Long, ReportRequestContext> sessions = new ConcurrentHashMap<>();

  public void createSession(Long chatId) {
    ReportRequestContext session = new ReportRequestContext();
    session.setState(BotState.START);
    sessions.put(chatId, session);
  }

  public ReportRequestContext getSession(Long chatId) {
    return sessions.get(chatId);
  }

  public void updateSession(Long chatId, ReportRequestContext session) {
    sessions.put(chatId, session);
  }

  public void removeSession(Long chatId) {
    sessions.remove(chatId);
  }

  public boolean hasSession(Long chatId) {
    return sessions.containsKey(chatId);
  }
}


@Service
@Slf4j
@RequiredArgsConstructor
class ReportFlowService {

  private final AuthContextService authService;
  private final ReportSessionService sessionService;
  private final EnterpriseService enterpriseService;
  private final ReportService reportService;
  private final VehicleService vehicleService;

  public void startReportFlow(Long chatId, MessageSender messageSender) {
    sessionService.createSession(chatId);
    showReportTypeSelection(chatId, messageSender);
  }

  public void processTextInput(Long chatId, String text, MessageSender messageSender) {
    ReportRequestContext session = sessionService.getSession(chatId);
    if (session == null) {
      messageSender.sendText(chatId, "Начните заново: /report");
      return;
    }

    try {
      switch (session.getState()) {
        case VEHICLE_WAITING_NUMBER -> handleVehicleNumber(chatId, session, text, messageSender);
        case WAITING_START_DATE -> handleStartDate(chatId, session, text, messageSender);
        case WAITING_END_DATE -> handleEndDate(chatId, session, text, messageSender);
        default -> {
          messageSender.sendText(chatId, "Начните заново: /report");
          sessionService.removeSession(chatId);
        }
      }
    } catch (Exception e) {
      messageSender.sendText(chatId, "❌ Ошибка: " + e.getMessage());
    }
  }

  public void processCallbackInput(Long chatId, String callbackData, MessageSender messageSender) {
    ReportRequestContext session = sessionService.getSession(chatId);
    if (session == null) {
      messageSender.sendText(chatId, "Начните заново: /report");
      return;
    }

    try {
      if (callbackData.startsWith("report_type:")) {
        handleReportType(chatId, session, callbackData, messageSender);
      } else if (callbackData.startsWith("enterprise:")) {
        handleEnterpriseSelection(chatId, session, callbackData, messageSender);
      } else if (callbackData.startsWith("period:")) {
        handlePeriodSelection(chatId, session, callbackData, messageSender);
      }
    } catch (Exception e) {
      messageSender.sendText(chatId, "❌ Ошибка: " + e.getMessage());
    }
  }

  private void showReportTypeSelection(Long chatId, MessageSender messageSender) {
    List<List<InlineKeyboardButton>> buttons = List.of(
            List.of(
                    InlineKeyboardButton.builder()
                            .text("🚗 По машине")
                            .callbackData("report_type:vehicle")
                            .build(),
                    InlineKeyboardButton.builder()
                            .text("🏢 По предприятию")
                            .callbackData("report_type:enterprise")
                            .build(),
                    InlineKeyboardButton.builder()
                            .text("📊 Общий отчет")
                            .callbackData("report_type:total")
                            .build()
            )
    );

    messageSender.sendKeyboard(chatId, "Выберите тип отчета:", buttons);
  }

  private void handleReportType(Long chatId, ReportRequestContext session,
                                String callbackData, MessageSender messageSender) {
    String type = callbackData.substring("report_type:".length());

    switch (type) {
      case "vehicle" -> {
        session.setType(ReportType.VEHICLE_MILEAGE);
        session.setState(VEHICLE_WAITING_NUMBER);
        messageSender.sendText(chatId, "Введите гос. номер машины:");
      }
      case "enterprise" -> {
        session.setType(ReportType.ENTERPRISE_MILEAGE);
        showEnterpriseList(chatId, messageSender);
      }
      case "total" -> {
        session.setType(ReportType.TOTAL_MILEAGE);
        showPeriodSelection(chatId, messageSender);
      }
      default -> throw new IllegalStateException("Неизвестный выбор: " + type);
    }
  }

  private void showEnterpriseList(Long chatId, MessageSender messageSender) {
    Manager manager = authService.getCurrentManager(chatId);
    List<Enterprise> enterprises = enterpriseService.findAllForManager(manager.getId());

    List<List<InlineKeyboardButton>> rows = new ArrayList<>();
    for (Enterprise enterprise : enterprises) {
      rows.add(List.of(
              InlineKeyboardButton.builder()
                      .text(enterprise.getName())
                      .callbackData("enterprise:" + enterprise.getId())
                      .build()
      ));
    }

    messageSender.sendKeyboard(chatId, "Выберите предприятие:", rows);
  }

  private void handleEnterpriseSelection(Long chatId, ReportRequestContext session,
                                         String callbackData, MessageSender messageSender) {
    Long enterpriseId = Long.parseLong(callbackData.substring("enterprise:".length()));
    Enterprise enterprise = enterpriseService.findById(enterpriseId);

    session.setEnterpriseId(enterpriseId);
    session.setEnterpriseName(enterprise.getName());
    session.setState(BotState.PERIOD_SELECTION);
    showPeriodSelection(chatId, messageSender);
  }

  private void showPeriodSelection(Long chatId, MessageSender messageSender) {
    List<List<InlineKeyboardButton>> buttons = List.of(
            List.of(
                    InlineKeyboardButton.builder().text("День").callbackData("period:day").build(),
                    InlineKeyboardButton.builder().text("Месяц").callbackData("period:month").build(),
                    InlineKeyboardButton.builder().text("Год").callbackData("period:year").build()
            )
    );

    messageSender.sendKeyboard(chatId, "Выберите период:", buttons);
  }

  private void handlePeriodSelection(Long chatId, ReportRequestContext session,
                                     String callbackData, MessageSender messageSender) {
    String periodStr = callbackData.substring("period:".length());
    Period period = Period.valueOf(periodStr.toUpperCase());

    session.setPeriod(period);
    session.setState(WAITING_START_DATE);
    messageSender.sendText(chatId, "Введите начальную дату (ГГГГ-ММ-ДД):");
  }

  private void handleVehicleNumber(Long chatId, ReportRequestContext session,
                                   String vehicleNumber, MessageSender messageSender) {
    Vehicle vehicle = vehicleService.findVehicleByNumber(vehicleNumber)
            .orElseThrow(() -> new IllegalArgumentException("Машина не найдена"));
    session.setVehicleNumber(vehicleNumber);
    session.setState(BotState.PERIOD_SELECTION);
    showPeriodSelection(chatId, messageSender);
  }

  private void handleStartDate(Long chatId, ReportRequestContext session,
                               String dateStr, MessageSender messageSender) {
    LocalDateTime startDate = parseDate(dateStr);
    session.setStartDate(startDate);
    session.setState(BotState.WAITING_END_DATE);
    messageSender.sendText(chatId, "Введите конечную дату (ГГГГ-ММ-ДД):");
  }

  private void handleEndDate(Long chatId, ReportRequestContext session,
                             String dateStr, MessageSender messageSender) {
    LocalDateTime endDate = parseDate(dateStr);
    session.setEndDate(endDate);

    generateReport(chatId, session, messageSender);
    sessionService.removeSession(chatId);
  }

  private LocalDateTime parseDate(String input) {
    try {
      return new DateTimeFormatterBuilder()
              .appendPattern("yyyy[-MM[-dd['T'HH[:mm]]]]")
              .parseDefaulting(ChronoField.MONTH_OF_YEAR, 1)
              .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
              .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
              .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
              .toFormatter()
              .parse(input, LocalDateTime::from);
    } catch (Exception e) {
      throw new IllegalArgumentException("Неверный формат даты: " + input);
    }
  }

  private void generateReport(Long chatId, ReportRequestContext session,
                              MessageSender messageSender) {
    try {
      Manager manager = authService.getCurrentManager(chatId);
      MileageReportDTO report = createReport(manager, session);

      String formatted = formatReport(report);
      messageSender.sendText(chatId, formatted);

    } catch (Exception e) {
      messageSender.sendText(chatId, "❌ Ошибка генерации отчета: " + e.getMessage());
      log.error("Ошибка генерации отчета", e);
    }
  }

  private MileageReportDTO createReport(Manager manager, ReportRequestContext session) {
    return switch (session.getType()) {
      case VEHICLE_MILEAGE -> reportService.generateMileageReport(
              manager, session.getVehicleNumber(),
              session.getStartDate(), session.getEndDate(), session.getPeriod());

      case ENTERPRISE_MILEAGE -> reportService.generateEnterpriseMileageReport(
              manager, session.getEnterpriseId(),
              session.getStartDate(), session.getEndDate(), session.getPeriod());

      case TOTAL_MILEAGE -> reportService.generateTotalMileageReport(
              manager, session.getStartDate(), session.getEndDate(), session.getPeriod());
    };
  }

  private String formatReport(MileageReportDTO report) {
    return String.format("""
                    📊 %s
                    📅 Период: %s
                    🔄 С %s по %s
                    
                    %s
                    """,
            report.getReportType(),
            report.getPeriod(),
            report.getStartDate(),
            report.getEndDate(),
            report.getResults().entrySet().stream()
                    .map(e -> "• " + e.getKey() + ": " + e.getValue() + " км")
                    .collect(Collectors.joining("\n"))
    );
  }
}

```
В ходе рефакторинга произошли глобальные изменения кода.
Основная логика формирования отчета была вынесена в класс ReportFlowService.
Сам основной класс TelegramBot стал отвечать только за свою работу - обработка входящих команд и выходящих,
ну и регистрация этих команд при запуске.  

Основные разделения:
- TelegramBot - только работа с Telegram API
- BotMessageProcessor - обработка текстовых сообщений
- BotCallbackProcessor - обработка нажатий кнопок
- ReportSessionService - управление сессиями
- ReportFlowService - управление потоком выдачи и формирования отчета

Код стал более гибким произошло разделение ответственности, легче читать и понимать.
Как следствие TelegramBot стал более похож на описанный дизайн (почти 1:1).

Данная итерация заняла у меня 6-8 часов. Это был самый сложный пример для анализа дизайна и последующего рефакторинга.
Мне изначально казалось что в этом коде все хорошо. И я не думал что в нем что-то потребуется изменить.
Но стоило написать словесный дизайн и потом просмотреть код, понимаешь насколько они разняться.
И в целом вынос логики, разграничение обязаннстей сервисов это нужные вещи, 
которые надо стараться проектировать на ранних этапах разработки





















