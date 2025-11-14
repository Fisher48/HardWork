Задание 1: Снижаем цикломатическую сложность (ЦС).  
Для этого задания я решил поискать примеры с курса "Выживший".  
И нашел пару моментов, где ЦС действительно зашкаливает.  
Первый пример это задание с захватом карты Conquest, я взял основной метод ConquestCampaign()  

Что было **ДО**:  
ЦС - порядка 40  
куча ифов и доп. проверок  
4 вложенных цикла for + 1 цикл while.  
```java
public static int ConquestCampaign(int N, int M, int L, int[] battalion) {
    int days = 1;
    boolean isCaptured = false;
    int[][] square = new int[N][M];
    int expectedSum = N * M;
    int sum = 0;

        // Высадка десанта и захват первых точек
        if (battalion.length == L * 2) {
            for (int i = 0; i <= L * 2 - 1; i += 2) {
                square[battalion[i] - 1][battalion[i + 1] - 1] = 1;
            }
        }
        // Проверяем захвачена ли карта уже на этапе высадки
        for (int i = 0; i <= N - 1; i++) {
            for (int j = 0; j <= M - 1; j++) {
                sum += square[i][j];
            }
        }
        if (sum == expectedSum) {
            isCaptured = true;
        }
        // TODO - На будещее, в следующей ревизии, попробовать упростить цикл и сделать его более компактным и читабельным.
        while (!isCaptured) {
            for (int i = 0; i <= N - 1; i++) {
                for (int j = 0; j <= M - 1; j++) {
                    if (square[i][j] == days) {
                        if (i == 0) {
                            if (j == 0) {
                                if (square[i + 1][j] == 0) {
                                    square[i + 1][j] = days + 1;
                                }
                                if (square[i][j + 1] == 0) {
                                    square[i][j + 1] = days + 1;
                                }
                            }
                            if (j == M - 1) {
                                if (square[i][j - 1] == 0) {
                                    square[i][j - 1] = days + 1;
                                }
                                if (square[i + 1][j] == 0) {
                                    square[i + 1][j] = days + 1;
                                }
                            }
                            if (j > 0 && j < M - 1) {
                                if (square[i][j - 1] == 0) {
                                    square[i][j - 1] = days + 1;
                                }
                                if (square[i + 1][j] == 0) {
                                    square[i + 1][j] = days + 1;
                                }
                                if (square[i][j + 1] == 0) {
                                    square[i][j + 1] = days + 1;
                                }
                            }
                        }
                        if (i == N - 1) {
                            if (j == 0) {
                                if (square[i - 1][j] == 0) {
                                    square[i - 1][j] = days + 1;
                                }
                                if (square[i][j + 1] == 0) {
                                    square[i][j + 1] = days + 1;
                                }
                            }
                            if (j == M - 1) {
                                if (square[i][j - 1] == 0) {
                                    square[i][j - 1] = days + 1;
                                }
                                if (square[i - 1][j] == 0) {
                                    square[i - 1][j] = days + 1;
                                }
                            }
                            if (j > 0 && j < M - 1) {
                                if (square[i][j - 1] == 0) {
                                    square[i][j - 1] = days + 1;
                                }
                                if (square[i - 1][j] == 0) {
                                    square[i - 1][j] = days + 1;
                                }
                                if (square[i][j + 1] == 0) {
                                    square[i][j + 1] = days + 1;
                                }
                            }
                        }
                        if (i < N - 1 && i > 0 && j == 0) {
                            if (square[i - 1][j] == 0) {
                                square[i - 1][j] = days + 1;
                            }
                            if (square[i + 1][j] == 0) {
                                square[i + 1][j] = days + 1;
                            }
                            if (square[i][j + 1] == 0) {
                                square[i][j + 1] = days + 1;
                            }
                        }
                        if (i < N - 1 && i > 0 && j == M - 1) {
                            if (square[i - 1][j] == 0) {
                                square[i - 1][j] = days + 1;
                            }
                            if (square[i + 1][j] == 0) {
                                square[i + 1][j] = days + 1;
                            }
                            if (square[i][j - 1] == 0) {
                                square[i][j - 1] = days + 1;
                            }
                        }
                        if (i > 0 && i < N - 1 && j > 0 && j < M - 1) {
                            if (square[i - 1][j] == 0) {
                                square[i - 1][j] = days + 1;
                            }
                            if (square[i + 1][j] == 0) {
                                square[i + 1][j] = days + 1;
                            }
                            if (square[i][j - 1] == 0) {
                                square[i][j - 1] = days + 1;
                            }
                            if (square[i][j + 1] == 0) {
                                square[i][j + 1] = days + 1;
                            }
                        }
                    }
                }
            }

            for (int k = 0; k <= N - 1; k++) {
                for (int l = 0; l <= M - 1; l++) {
                    if (square[k][l] != 0) {
                        square[k][l] = days + 1;
                    }
                }
            }
            days++;

            isCaptured = true;
            for (int i = 0; i <= N - 1; i++) {
                for (int j = 0; j <= M - 1; j++) {
                    if (square[i][j] == 0) {
                        isCaptured = false;
                        break;
                    }
                }
            }
        }
        return days;
    }
````

Результат после рефакторинга и попытки снизить ЦС:  
ЦС - 5  
Циклы - 1 вложенный for в цикл while  
Полностью переработанный подход к решению, исключающий кучу излишних (дублирующих) проверок.  
- Добавил двумерный массив, который ограничивает ход захвата карты.
- Изменил проверку на 1 if вместо кучи разных.
- Добавлена очередь и одномерного массива для координат, которые попадают на проверку после каждого дня захвата.

```java
public static int ConquestCampaign(int N, int M, int L, int[] battalion) {
        int[][] square = new int[N][M];
        Queue<int[]> queue = new LinkedList<>();

        // Высадка десанта и захват первых точек
        for (int i = 0; i < L * 2; i += 2) {
            int x = battalion[i] - 1;
            int y = battalion[i + 1] - 1;
            square[x][y] = 1;
            queue.add(new int[]{x, y});
        }

        // Определяем ограничение по направлению (вверх, вниз, влево, вправо)
        int[][] directions = { {0, 1}, {1, 0}, {-1, 0}, {0, -1} };

        int maxDay = 1;
    
        // Выполняем захват карты, пока очередь не будет пустой
        while (!queue.isEmpty()) {
            // Берем текущую координату из очереди
            int[] cell = queue.poll();
            int currRow = cell[0]; // текущая колонка
            int currCol = cell[1]; // текущий столбец
            // День захвата на текущей ячейке карты
            int days = square[currRow][currCol]; 

            // Проходим по направлениям (вверх, вниз, влево, вправо)
            // И захватывает соседние координаты на карте (при условии, что они в пределах границы)
            for (int[] direction : directions) {
                int neighborRow = currRow + direction[0];
                int neighborCol = currCol + direction[1];
                // Проверяем что в пределах границ и мы еще не посещали эту точку
                if (neighborRow >= 0 && neighborRow < N && neighborCol >= 0 && neighborCol < M
                        && square[neighborRow][neighborCol] == 0) {
                    // Устанавливаем новый день захвата на карте для соседа
                    square[neighborRow][neighborCol] = days + 1; 
                    maxDay = Math.max(maxDay, days + 1); // обновляем максимальный день захвата
                    queue.add(new int[]{neighborRow, neighborCol}); // добавляем соседа в очередь, для последующего захвата от него 
                }
            }
        }

        return maxDay;
    }
```

Второй пример также из курса "Выживший", это уже задание с Деревом Жизни, где тоже очень много проверок if и т.д.  

Что было **ДО**:  
ЦС - порядка 38  
Также много if и доп. проверок с дублирующимися условиями

```java

public static final int AGE_OF_DEATH = 3;
public static final int NO_BRANCH = 0;

 public static int[][] destructionOfTree(int[][] tree) {

        for (int i = 0; i < tree.length; i++) {
            for (int j = 0; j < tree[0].length; j++) {
                if (tree[i][j] >= AGE_OF_DEATH) {
                    tree[i][j] = 0;
                    if (i == 0) {
                        if (j == 0) {
                            if (tree[i + 1][j] < AGE_OF_DEATH) {
                                tree[i + 1][j] = NO_BRANCH;
                            }
                            if (tree[i][j + 1] < AGE_OF_DEATH) {
                                tree[i][j + 1] = NO_BRANCH;
                            }
                        }
                        if (j == tree[0].length - 1) {
                            if (tree[i][j - 1] < AGE_OF_DEATH) {
                                tree[i][j - 1] = NO_BRANCH;
                            }
                            if (tree[i + 1][j] < AGE_OF_DEATH) {
                                tree[i + 1][j] = NO_BRANCH;
                            }
                        }
                        if (j > 0 && j < tree[0].length - 1) {
                            if (tree[i][j - 1] < AGE_OF_DEATH) {
                                tree[i][j - 1] = NO_BRANCH;
                            }
                            if (tree[i + 1][j] < AGE_OF_DEATH) {
                                tree[i + 1][j] = NO_BRANCH;
                            }
                            if (tree[i][j + 1] < AGE_OF_DEATH) {
                                tree[i][j + 1] = NO_BRANCH;
                            }
                        }
                    }
                    if (i == tree.length - 1) {
                        if (j == 0) {
                            if (tree[i - 1][j] < AGE_OF_DEATH) {
                                tree[i - 1][j] = NO_BRANCH;
                            }
                            if (tree[i][j + 1] < AGE_OF_DEATH) {
                                tree[i][j + 1] = NO_BRANCH;
                            }
                        }
                        if (j == tree[0].length - 1) {
                            if (tree[i][j - 1] < AGE_OF_DEATH) {
                                tree[i][j - 1] = NO_BRANCH;
                            }
                            if (tree[i - 1][j] < AGE_OF_DEATH) {
                                tree[i - 1][j] = NO_BRANCH;
                            }
                        }
                        if (j > 0 && j < tree[0].length - 1) {
                            if (tree[i][j - 1] < AGE_OF_DEATH) {
                                tree[i][j - 1] = NO_BRANCH;
                            }
                            if (tree[i - 1][j] < AGE_OF_DEATH) {
                                tree[i - 1][j] = NO_BRANCH;
                            }
                            if (tree[i][j + 1] < AGE_OF_DEATH) {
                                tree[i][j + 1] = NO_BRANCH;
                            }
                        }
                    }
                    if (i < tree.length - 1 && i > 0 && j == 0) {
                        if (tree[i - 1][j] < AGE_OF_DEATH) {
                            tree[i - 1][j] = NO_BRANCH;
                        }
                        if (tree[i + 1][j] < AGE_OF_DEATH) {
                            tree[i + 1][j] = NO_BRANCH;
                        }
                        if (tree[i][j + 1] < AGE_OF_DEATH) {
                            tree[i][j + 1] = NO_BRANCH;
                        }
                    }
                    if (i < tree.length - 1 && i > 0 && j == tree[0].length - 1) {
                        if (tree[i - 1][j] < AGE_OF_DEATH) {
                            tree[i - 1][j] = NO_BRANCH;
                        }
                        if (tree[i + 1][j] < AGE_OF_DEATH) {
                            tree[i + 1][j] = NO_BRANCH;
                        }
                        if (tree[i][j - 1] < AGE_OF_DEATH) {
                            tree[i][j - 1] = NO_BRANCH;
                        }
                    }
                    if (i > 0 && i < tree.length - 1 && j > 0 && j < tree[0].length - 1) {
                        if (tree[i - 1][j] < AGE_OF_DEATH) {
                            tree[i - 1][j] = NO_BRANCH;
                        }
                        if (tree[i + 1][j] < AGE_OF_DEATH) {
                            tree[i + 1][j] = NO_BRANCH;
                        }
                        if (tree[i][j - 1] < AGE_OF_DEATH) {
                            tree[i][j - 1] = NO_BRANCH;
                        }
                        if (tree[i][j + 1] < AGE_OF_DEATH) {
                            tree[i][j + 1] = NO_BRANCH;
                        }
                    }
                }
            }
        }
        return tree;
    }
```

Что стало **ПОСЛЕ**:

- ЦС - 5
- Добавил массив, который ограничивает ход захвата соседних веток.
- Изменил проверку на 1 if вместо кучи разных добавив единую проверку.

```java
 public static final int AGE_OF_DEATH = 3;
    public static final int NO_BRANCH = 0;

    private static final int[][] DIRECTIONS = { {-1, 0}, {1, 0}, {0, -1}, {0, 1} };

    public static int[][] destructionOfTree(int[][] tree) {
        int rows = tree.length;
        int cols = tree[0].length;

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {

                if (tree[i][j] >= AGE_OF_DEATH) {
                    tree[i][j] = NO_BRANCH;

                    for (int[] d : DIRECTIONS) {
                        int ni = i + d[0];
                        int nj = j + d[1];

                        if (ni >= 0 && ni < rows && nj >= 0 && nj < cols 
                                && tree[ni][nj] < AGE_OF_DEATH) {
                            tree[ni][nj] = NO_BRANCH;
                        }
                    }
                }
            }
        }
        return tree;
    }
```

И затем, я решил посмотреть мой дипломный проект, где тоже как я и думал, есть места с повышенным ЦС.  
Я взял метод импорта данных из CSV.
Конкретно метод сохранения импорта данных в БД.

Что было **ДО**:  
- Огромный метод с кучей if и else
- ЦС - 10

```java
 // Сохранение импортированных данных
private void saveImportedData(ImportDTO data) {
    log.info("Начинаем импорт данных...");

    // Получаем текущего менеджера
    Manager currentManager = getCurrentManager();

    // Сохранение предприятия
    EnterpriseImportData enterpriseData = data.getEnterprise();
    log.info("Обрабатываем предприятие: {}", enterpriseData.getName());
    Enterprise enterprise = enterpriseService.findById(enterpriseData.getId());

    if (enterprise != null) {
        // Обновляем существующее предприятие
        enterprise.setName(enterpriseData.getName());
        enterprise.setCity(enterpriseData.getCity());
        enterprise.setTimezone(enterpriseData.getTimezone());

        // Инициализация списка менеджеров, если он null
        if (enterprise.getManagers() == null) {
            enterprise.setManagers(new ArrayList<>());
        }

        // Привязываем текущего менеджера
        if (!enterprise.getManagers().contains(currentManager)) {
            enterprise.getManagers().add(currentManager);
        }

        enterpriseService.save(enterprise);
        log.info("Предприятие обновлено: {}", enterprise.getName());
    } else {
        // Создаем новое предприятие
        enterprise = convertToEnterprise(enterpriseData);

        // Инициализация списка менеджеров, если он null
        if (enterprise.getManagers() == null) {
            enterprise.setManagers(new ArrayList<>());
        }

        // Добавляем текущего менеджера
        enterprise.getManagers().add(currentManager);

        enterpriseService.save(enterprise);
        log.info("Предприятие создано: {}", enterprise.getName());
    }

    // Сохраняем автомобили
    Enterprise finalEnterprise = enterprise;
    if (data.getVehicles() != null && !data.getVehicles().isEmpty()) {
        data.getVehicles().forEach(vehicleDTO -> {
            Vehicle existingVehicle = vehicleService.findVehicleByNumber(vehicleDTO.getNumber()).orElse(null);
            if (existingVehicle != null) {
                log.info("Автомобиль {} уже существует. Обновляем данные.", vehicleDTO.getNumber());
                existingVehicle.setPrice(vehicleDTO.getPrice());
                existingVehicle.setYearOfCarProduction(vehicleDTO.getYearOfCarProduction());
                existingVehicle.setMileage(vehicleDTO.getMileage());
                existingVehicle.setBrand(brandService.findOne(vehicleDTO.getBrand().getId()));
                // existingVehicle.setEnterprise(enterpriseService.findOne(enterpriseData.getId()));
                vehicleService.save(existingVehicle);
            } else {
                Vehicle newVehicle = convertToVehicle(vehicleDTO);
                newVehicle.setEnterprise(finalEnterprise);
                vehicleService.save(newVehicle);
                log.info("Автомобиль сохранен: {}, {}", newVehicle.getId(), newVehicle.getNumber());
            }
        });
    } else {
        log.info("Данные об автомобилях не переданы.");
    }

    // Сохранение поездок
    data.getTrips().forEach(tripImportData -> {
        Trip trip = new Trip();
        trip.setStartTime(LocalDateTime.parse(tripImportData.getStartTime(),
                DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")));
        trip.setEndTime(LocalDateTime.parse(tripImportData.getEndTime(),
                DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")));

        // Привязываем машину
        Vehicle vehicle = vehicleService.findVehicleByNumber(tripImportData.getVehicleNumber())
                .orElseThrow(() -> new VehicleNotFoundException
                        ("Машина с номером " + tripImportData.getVehicleNumber() + " не найдена"));
        trip.setVehicle(vehicle);

        // Генерация и сохранение точек маршрута между начальной и конечной точками
        LinkedList<GpsData> routePoints = generateRoutePoints(
                tripImportData.getStartLatitude(),
                tripImportData.getStartLongitude(),
                tripImportData.getEndLatitude(),
                tripImportData.getEndLongitude(),
                trip, trip.getStartTime()
        );
        gpsDataRepository.saveAll(routePoints);

        BigDecimal distanceKm = DistanceCalculator.calculateDistance
                (tripImportData.getStartLatitude(), tripImportData.getStartLongitude(),
                        tripImportData.getEndLatitude(), tripImportData.getEndLongitude());

        trip.setMileage(distanceKm);
        trip.setStartGpsData(routePoints.getFirst());
        trip.setEndGpsData(routePoints.getLast());

        // Сохраняем поездку
        tripService.save(trip);
        log.info("Поездка сохранена. ID: {}", trip.getId());
    });

}
```

Что стало **ПОСЛЕ**:  
- ЦС - 3-4 для каждого метода
- Выполнил декомпозицию одного большого метода на несколько отдельных методов. 
- Отдельно выделил методы обновления/создания предприятия, сохранения машины и поездки
- Заменил if/else на ifPresentOrElse
- Использовал названия методов более читаемые, что позволило убрать ненужные комментарии в коде

```java
private void saveImportedData(ImportDTO data) {
    log.info("Начинаем импорт данных...");

    Manager currentManager = getCurrentManager();
    Enterprise enterprise = upsertEnterprise(data.getEnterprise(), currentManager);

    saveVehicles(data.getVehicles(), enterprise);
    saveTrips(data.getTrips());
}

private Enterprise upsertEnterprise(EnterpriseImportData enterpriseData, Manager manager) {
    Enterprise enterprise = enterpriseService.findById(enterpriseData.getId());
    if (enterprise == null) {
        return createEnterprise(enterpriseData, manager);
    }
    return updateEnterprise(enterprise, enterpriseData, manager);
}

private Enterprise createEnterprise(EnterpriseImportData data, Manager manager) {
    Enterprise enterprise = convertToEnterprise(data);
    enterprise.setManagers(new ArrayList<>(List.of(manager)));
    enterpriseService.save(enterprise);
    log.info("Предприятие создано: {}", enterprise.getName());
    return enterprise;
}

private Enterprise updateEnterprise(Enterprise enterprise, EnterpriseImportData data, Manager manager) {
    enterprise.setName(data.getName());
    enterprise.setCity(data.getCity());
    enterprise.setTimezone(data.getTimezone());
    enterprise.getManagers().add(manager);
    enterpriseService.save(enterprise);
    log.info("Предприятие обновлено: {}", enterprise.getName());
    return enterprise;
}


private void saveVehicles(List<VehicleDTO> vehicles, Enterprise enterprise) {
    if (vehicles == null || vehicles.isEmpty()) {
        log.info("Данные об автомобилях не переданы.");
        return;
    }

    vehicles.forEach(dto -> vehicleService.findVehicleByNumber(dto.getNumber())
            .ifPresentOrElse(
                    existing -> updateVehicle(existing, dto),
                    () -> createVehicle(dto, enterprise)
            ));
}

private void updateVehicle(Vehicle vehicle, VehicleDTO dto) {
    log.info("Обновляем автомобиль {}", dto.getNumber());
    vehicle.setPrice(dto.getPrice());
    vehicle.setYearOfCarProduction(dto.getYearOfCarProduction());
    vehicle.setMileage(dto.getMileage());
    vehicle.setBrand(brandService.findOne(dto.getBrand().getId()));
    vehicleService.save(vehicle);
}

private void createVehicle(VehicleDTO dto, Enterprise enterprise) {
    Vehicle vehicle = convertToVehicle(dto);
    vehicle.setEnterprise(enterprise);
    vehicleService.save(vehicle);
    log.info("Новый автомобиль сохранён: {}", vehicle.getNumber());
}


private void saveTrips(List<TripImportData> trips) {
    if (trips == null || trips.isEmpty()) return;

    trips.forEach(this::saveTrip);
}

private void saveTrip(TripImportData tripImportData) {
    Trip trip = new Trip();
    trip.setStartTime(parseTime(tripImportData.getStartTime()));
    trip.setEndTime(parseTime(tripImportData.getEndTime()));

    Vehicle vehicle = vehicleService.findVehicleByNumber(tripImportData.getVehicleNumber())
            .orElseThrow(() -> new VehicleNotFoundException("Машина не найдена: " + tripImportData.getVehicleNumber()));
    trip.setVehicle(vehicle);

    LinkedList<GpsData> points = generateRoutePoints(
            tripImportData.getStartLatitude(),
            tripImportData.getStartLongitude(),
            tripImportData.getEndLatitude(),
            tripImportData.getEndLongitude(),
            trip, trip.getStartTime()
    );
    gpsDataRepository.saveAll(points);

    trip.setMileage(DistanceCalculator.calculateDistance(
            tripImportData.getStartLatitude(), tripImportData.getStartLongitude(),
            tripImportData.getEndLatitude(), tripImportData.getEndLongitude()
    ));
    trip.setStartGpsData(points.getFirst());
    trip.setEndGpsData(points.getLast());
    tripService.save(trip);
}

private LocalDateTime parseTime(String value) {
    return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"));
}

```

Подводя итог данному занятию, хочется отметить что ЦС нужно постараться анализировать уже на этапе создания методов или сервисов.
Чтобы потом это было легче читать, понимать отслеживать и тестировать.
Очень большой эффект виден в старых своих материалах, где я только начинал заниматься.
Буду теперь обращать на это внимание, хоть и спасибо, что sonarlint видит это тоже и подсвечивает.
Хотя с момента его перехода в sonarqube я почему-то не увидел подсветки этого в некоторых классах, методах.
Основные моменты учтены, можно и дальше искать это в своём коде, найти всегда можно и попрактиковаться заодно.