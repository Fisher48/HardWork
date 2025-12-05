#### Увидеть ясную структуру дизайна
В данном задании требуется сформулировать логический дизайн для 3 примеров своего кода из рабочего проекта (несколько сотен строк).
Я постарался найти пару примеров, где можно попробовать его проработать в рамках этого задания.
Ознакомившись с материалом из 3-й части цикла _"Три уровня рассуждений о программной системе"_, было понятно что подразумевается именно в заключительной части статьи.
Хочу подчеркнуть еще раз, что я пока в поиске работы и могу искать только в дипломном проекте, но там не так много сервисов или кусков кода под несколько сотен строк).

И так 1-й пример - это сервис генерации поездок для машин **_BulkTripGenerator_**:

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
    private final AuthContextService authContextService;
    private final NotificationKafkaProducer notificationKafkaProducer;
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
- Сформулируйте словесно его логический дизайн.
- Насколько существующий код ему реально соответствует? 
- Может быть, теперь даже напрашивается совершенно другой код?

- Реализуйте этот дизайн (перепишите существующий код) ...
   -- в схеме "целиком или ничего", или (лучше)
   -- в декларативном виде, насколько возможно.

Логический дизайн данного кода...




























