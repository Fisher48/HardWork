#### Совмещаем несовместимое
2. Выберите в вашем рабочем проекте некоторый важный и достаточно автономный "кусок кода" - например, класс, активно используемый в проекте.
3. Непосредственно в коде (например, перед заголовком класса) добавьте комментарии, содержащие информацию глобального характера:
   как этот код вписывается в общую систему (понимание на более высоком уровне дизайна системы).
   Не пишите, что делает этот код/класс внутри - сам код не должен ничего знать о программе в целом, 
   а "пользователи" этого кода не должны ничего знать о его внутреннем устройстве.

4. Повторите пункты 2 и 3 ещё 2-3 раза с другим кодом.

В решении отправляете (по каждой итерации) часть исходного кода и полный комментарий к нему.

Для первого примера я взял класс ReportService:

#### Пример - 1 ReportService
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

#### Пример - 2: ReportFlowService
```java
/**
 * ReportFlowService - это класс предназначенный для обработки сообщений при работе с Телеграмм-ботом.
 * Управляет состоянием диалога пользователя во времени как state-machine.
 * Данный сервис находится между TelegramBot (сообщения, callback, кнопки) и ReportService (чистая бизнес-логика отчетов)
 * Задача данного сервиса - связать пользовательский ввод с корректным вызовом нужного метода в нужном сервисе (отчетов).
 * TelegramBot - получил сообщение, затем передал в этот сервис, а уже ReportFlowService знает на каком этапе диалог и что делать дальше.
 * 
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

#### Пример - 3:
```java

```

