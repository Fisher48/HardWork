#### Совмещаем несовместимое

Для первого примера я взял класс ReportService:

#### Пример - 1: ReportService
```java
/**
 * ReportService - это класс предназначенный для формирования отчета о пробегах автомобиля.
 * В зависимости от выбора это может быть пробег как предприятия, так и общий пробег всех авто менеджера.
 * Данный сервис содержит слой бизнес-логики формирования отчета о пробегах.
 * Используется в контролерах и Телеграм-боте.
 * Отчет - не является сущностью, поэтому не сохраняется в БД после формирования.
 * Это вычислительная часть данных, строится в контексте для определенного менеджера, так как есть разграничение доступа.
 * Данный сервис не валидирует данные, он считает что все данные уже корректны.
 * Сервис использует репозитории и другие сервисы для работы с полученными данными.
 * Изменения в этом сервисе могут быть отражены в главном контролере и Телеграмм-боте, поэтому
 * нужно проверить их после внесения глобальных изменений.
 */

@Service
@Slf4j
public class ReportService {

    private final TripRepository tripRepository;
    private final EnterpriseService enterpriseService;
    private final VehicleService vehicleService;

    @Autowired
    public ReportService(TripRepository tripRepository,
                         EnterpriseService enterpriseService, VehicleService vehicleService) {
        this.tripRepository = tripRepository;
        this.enterpriseService = enterpriseService;
        this.vehicleService = vehicleService;
    }

    @Cacheable(value = "mileageReports",
            key = "{#vehicleId, #startDate?.hashCode(), #endDate?.hashCode(), #period}")
    public MileageReportDTO generateMileageReport(Manager manager,
                                                  String vehicleNumber,
                                                  LocalDateTime startDate,
                                                  LocalDateTime endDate, Period period) {
        Optional<Vehicle> vehicle = Optional.ofNullable(vehicleService.findVehicleByNumber(vehicleNumber)
                .orElseThrow(() -> new VehicleNotFoundException("Машина не найдена: " + vehicleNumber)));

        Long vehicleId = vehicle.get().getId();

        if (!vehicleService.isVehicleManagedByManager(vehicleId, manager.getId())) {
            throw new AccessDeniedException("Нет доступа к этому автомобилю.");
        }
        log.info("Формирование отчета по машине id={}, период {}, с {} по {}", vehicleId, period, startDate, endDate);

        List<Trip> trips = tripRepository.findTripsForVehicleInTimeRange(vehicleId, startDate, endDate);
        log.debug("Найдено {} поездок", trips.size());

        Map<String, BigDecimal> mileageData = calculateMileage(trips, startDate, endDate, period);
        return buildReport(VEHICLE_MILEAGE, period, startDate, endDate, mileageData);
    }

    @Cacheable(value = "enterpriseMileageReports",
            key = "{#enterpriseId, #startDate?.hashCode(), #endDate?.hashCode(), #period}")
    public MileageReportDTO generateEnterpriseMileageReport(Manager manager,
                                                            Long enterpriseId,
                                                            LocalDateTime startDate,
                                                            LocalDateTime endDate, Period period) {
        if (!enterpriseService.isEnterpriseManagedByManager(enterpriseId, manager.getId())) {
            throw new AccessDeniedException("Нет доступа к этому предприятию.");
        }

        log.info("Формирование отчета по предприятию id={}, период {}, с {} по {}", enterpriseId, period, startDate, endDate);

        List<Trip> allTrips = tripRepository.findTripsByEnterpriseAndTimeRange(enterpriseId, startDate, endDate);
        Map<String, BigDecimal> mileageData = calculateMileage(allTrips, startDate, endDate, period);

        return buildReport(ENTERPRISE_MILEAGE, period, startDate, endDate, mileageData);
    }

    public MileageReportDTO generateTotalMileageReport(Manager manager,
                                                       LocalDateTime startDate,
                                                       LocalDateTime endDate, Period period) {
        List<Enterprise> enterprises = enterpriseService.findAllForManager(manager.getId());
        List<Trip> trips = tripRepository.findTripsByEnterpriseAndTimeRange(enterprises, startDate, endDate);

        Map<String, BigDecimal> mileageData = calculateMileage(trips, startDate, endDate, period);

        return buildReport(TOTAL_MILEAGE, period, startDate, endDate, mileageData);
    }

    private MileageReportDTO buildReport(ReportType title, Period period, LocalDateTime startDate,
                                  LocalDateTime endDate, Map<String, BigDecimal> results) {
        MileageReportDTO report = new MileageReportDTO();
        report.setReportType(title.getTitle());
        report.setPeriod(period.getTitle());
        report.setStartDate(startDate.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")));
        report.setEndDate(endDate.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")));
        report.setResults(results);
        return report;
    }

    private Map<String, BigDecimal> calculateMileage(List<Trip> trips, LocalDateTime startDate,
                                                 LocalDateTime endDate, Period period) {
        Map<String, BigDecimal> mileageMap = new HashMap<>();

        for (Trip trip : trips) {
            if (trip.getStartTime().isAfter(startDate) && trip.getEndTime().isBefore(endDate)) {
                String key = switch (period) {
                    case DAY -> trip.getStartTime().toLocalDate().toString();
                    case MONTH -> trip.getStartTime().getYear() + "-" +
                            String.format("%02d", trip.getStartTime().getMonthValue());
                    case YEAR -> String.valueOf(trip.getStartTime().getYear());
                    default -> throw new IllegalArgumentException("Неподдерживаемый период: " + period);
                };

                BigDecimal tripMileage = trip.getMileage() != null ? trip.getMileage() : BigDecimal.ZERO;

                mileageMap.merge(key, tripMileage, BigDecimal::add);
            }
        }

        // Округление и преобразование в Map<String, BigDecimal>
        return mileageMap.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, // Оставляем в BigDecimal
                        (e1, e2) -> e1, LinkedHashMap::new));
    }

}
```
___

Второй пример - это ReportFlowService:

#### Пример - 2: ReportFlowService
```java
/**
 * ReportFlowService - это класс предназначенный для обработки сообщений при работе с Телеграмм-ботом.
 * Управляет состоянием диалога пользователя во времени как state-machine.
 * Данный сервис находится между TelegramBot (сообщения, callback, кнопки) и ReportService (чистая бизнес-логика отчетов)
 * Задача данного сервиса - связать пользовательский ввод с корректным вызовом нужного метода в нужном сервисе (отчетов).
 * TelegramBot - получил сообщение, затем передал в этот сервис, а уже ReportFlowService знает на каком этапе диалог и что делать дальше.
 * Сервис использует необходимые сервисы для обработки данных.
 * Изменения в этом сервисе не затрагивают бизнес-процессы, а влияют только на сценарий диалога пользователя
 */

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
___

3-й пример я беру из пет-проекта разработки интернет-магазина инструментов:

#### Пример - 3: ImageStorageService
```java
/**
 * ImageStorageService - это класс предназначенный для сохранения и удаления изображений, связанных с сущностями приложения (товары и категории).
 * Схема хранения изображений может быть изменена в любой момент на другие типы помимо физического хранения в проекте.
 * Класс является частью слоя взаимодействия с файловой системой и изолирует часть приложения от деталей: 
 * структуры хранения, формирование имён файлов и публичных URL.
 * Данный класс не влияет на бизнес-логику приложения.
 * Это самостоятельный сервис не использующий какие-либо зависимости.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ImageStorageService {

    @Value("${app.upload.path:./uploads/images}")
    private String uploadPath;

    @Value("${app.base.url:http://localhost:8080}")
    private String baseUrl;

    public ProductImage saveImage(MultipartFile file, String productTitle) {
        log.info("Attempting to save image: {}, size: {}, type: {}",
                file.getOriginalFilename(), file.getSize(), file.getContentType());

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Файл пустой");
        }

        if (!isImage(file)) {
            throw new IllegalArgumentException("Файл не является изображением");
        }

        try {
            // Создаем директорию если не существует
            Path uploadDir = Paths.get(uploadPath, "products");
            Files.createDirectories(uploadDir);

            // Генерируем уникальное имя файла
            String originalFileName = file.getOriginalFilename();
            String fileExtension = getFileExtension(originalFileName);
            String fileName = generateFileName(productTitle, fileExtension);

            // Сохраняем файл
            Path filePath = uploadDir.resolve(fileName);
            Files.write(filePath, file.getBytes());

            log.info("Image saved: {}", filePath);

            // Создаем и возвращаем сущность ProductImage
            return ProductImage.builder()
                    .url(baseUrl + "/images/products/" + fileName) // Полный URL
                    .alt(productTitle) // Базовое описание
                    .sortOrder(0)
                    .build();

        } catch (IOException e) {
            throw new RuntimeException("Failed to save image: " + e.getMessage(), e);
        }
    }

    public List<ProductImage> saveImages(List<MultipartFile> files, String productTitle) {
        List<ProductImage> productImages = new ArrayList<>();

        for (MultipartFile file : files) {
            if (!file.isEmpty() && isImage(file)) {
                try {
                    ProductImage productImage = saveImage(file, productTitle);
                    productImages.add(productImage);
                } catch (Exception e) {
                    log.warn("Не удалось сохранить изображение: {}", file.getOriginalFilename(), e);
                }
            }
        }
        return productImages;
    }

    public void deleteImage(String imageUrl) {
        try {
            // Извлекаем имя файла из URL
            String fileName = extractFileNameFromUrl(imageUrl);
            Path filePath = Paths.get(uploadPath,"products", fileName);

            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("Image deleted: {}", filePath);
            } else {
                log.warn("File not found for deletion: {}", filePath);
            }
        } catch (IOException e) {
            log.error("Failed to delete image: {}", e.getMessage());
        }
    }

    public boolean isImage(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && contentType.startsWith("image/");
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return ".jpg";
        }
        return fileName.substring(fileName.lastIndexOf("."));
    }

    private String generateFileName(String productTitle, String extension) {
        String safeTitle = productTitle.replaceAll("[^a-zA-Z0-9-]", "_");
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        return safeTitle + "_" + uniqueId + "_" + System.currentTimeMillis() + extension;
    }

    private String extractFileNameFromUrl(String imageUrl) {
        // Извлекаем имя файла из URL: http://localhost:8080/images/filename.jpg -> filename.jpg
        if (imageUrl.contains("/")) {
            return imageUrl.substring(imageUrl.lastIndexOf("/") + 1);
        }
        return imageUrl;
    }

    /**
     * Сохраняет изображение категории
     */
    public String saveCategoryImage(MultipartFile file, String categoryTitle) {
        log.info("Saving category image: {}, category: {}, size: {}",
                file.getOriginalFilename(), categoryTitle, file.getSize());

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Файл пустой");
        }

        if (!isImage(file)) {
            throw new IllegalArgumentException("Файл не является изображением");
        }

        // Проверяем размер для категорий (максимум 2MB)
        if (file.getSize() > 2 * 1024 * 1024) {
            throw new IllegalArgumentException("Размер изображения категории не должен превышать 2MB");
        }

        try {
            // Создаем поддиректорию для категорий
            Path categoryDir = Paths.get(uploadPath, "categories");
            Files.createDirectories(categoryDir);

            // Генерируем уникальное имя файла
            String originalFileName = file.getOriginalFilename();
            String fileExtension = getFileExtension(originalFileName);
            String fileName = generateCategoryFileName(categoryTitle, fileExtension);

            // Сохраняем файл
            Path filePath = categoryDir.resolve(fileName);
            Files.write(filePath, file.getBytes());

            // Формируем URL для доступа к изображению
            String imageUrl = baseUrl + "/images/categories/" + fileName;
            log.info("Category image saved: {}", imageUrl);

            return imageUrl;

        } catch (IOException e) {
            log.error("Failed to save category image: {}", e.getMessage(), e);
            throw new RuntimeException("Не удалось сохранить изображение категории: " + e.getMessage(), e);
        }
    }

    /**
     * Генерирует миниатюру для категории (пока возвращает тот же URL)
     */
    public String generateThumbnail(String originalImageUrl) {
        log.info("Generating thumbnail for: {}", originalImageUrl);
        // Пока просто возвращаем тот же URL
        // В будущем можно реализовать реальную генерацию миниатюр
        return originalImageUrl;
    }

    /**
     * Удаляет изображение категории
     */
    public void deleteCategoryImage(String imageUrl) {
        try {
            // Извлекаем имя файла из URL
            String fileName = extractFileNameFromUrl(imageUrl);

            // Учитываем что файл в поддиректории categories
            Path filePath = Paths.get(uploadPath, "categories", fileName);

            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("Category image deleted: {}", filePath);
            } else {
                log.warn("Category image file not found: {}", filePath);
            }
        } catch (IOException e) {
            log.error("Failed to delete category image: {}", e.getMessage(), e);
        }
    }

    /**
     * Генерирует имя файла для категории
     */
    private String generateCategoryFileName(String categoryTitle, String extension) {
        String safeTitle = categoryTitle.replaceAll("[^a-zA-Z0-9-]", "_");
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        return "category_" + safeTitle + "_" + uniqueId + "_" + System.currentTimeMillis() + extension;
    }

    @PostConstruct
    public void init() {
        try {
            // Создаем основную директорию
            Files.createDirectories(Paths.get(uploadPath));

            // Создаем поддиректорию для категорий
            Path categoryDir = Paths.get(uploadPath, "categories");
            Files.createDirectories(categoryDir);

            log.info("Upload directories created:");
            log.info(" - Main: {}", uploadPath);
            log.info(" - Categories: {}", categoryDir);

        } catch (IOException e) {
            log.warn("Could not create upload directories: {}", e.getMessage());
        }
    }
}
```

Что хочется сказать о "Самодокументируемом коде" - это важная для понимания вещь, с которой не так просто работать. 
Ведь разработчик, который непосредственно пишет код в проекте, он держит весь контекст и не может мыслить более высоко и абстрактно, по крайней мере начинающий).
Всегда, когда ты пишешь комментарий, хочется написать именно то, "Что код делает?", хотя нужно по типу "Как этот код вписывается в общую программу?".
Я старался абстрагироваться от кода, особенно понимая и зная, что он должен делать, как будто ни разу его не видел, но получилось не совсем хорошо, но иду в правильном направлении.
Конечно очень помогает в этом плане спецификация и дизайн кода. Если сразу писать хорошо, с понятными именами, не спеша - это даст лучшее понимание кода другими людьми.
И тогда объяснения сами собой пропадут, хоть и не всегда так получается.
Как говорится _"То, что в проекте очевидно для вас, совсем не очевидно для всех остальных"_. 
Поэтому стоит писать комментарии таким образом, чтобы потом они не становились устаревшими, не рассинхронились с другим кодом, 
не быть излишне комментируемым и тренировать данный софт-скилл. В первую очередь они будут полезны другим разработчикам и это уже вежливость.


