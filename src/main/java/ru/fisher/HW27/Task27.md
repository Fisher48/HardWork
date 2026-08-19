## Безошибочный код с помощью Typestate-Oriented Programming (TOP)

Для данного задания в моем проекте ToolsMarket интернет-магазин инструмента, я нашел пару мест, где есть изменения состояния. 
Где попытался применить TOP, хотя в Java его практически нет как нативного инструмента.

---

### Пример 1. Заказы  

#### До  
В оригинальном коде был один универсальный класс `Order` с enum-полем `OrderStatus`. 
Допустимость каждого действия проверялась в runtime через булевы методы-предикаты (`canBeProcessed()`, `canBePaid()`, `canBeCancelled()`), 
а сами методы-переходы бросали `InvalidStatusTransitionException` при нарушении:

```java
public class Order {
    
    // ... //
    
    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.CREATED;

    public boolean canBeProcessed() {
        return status == OrderStatus.CREATED;
    }

    public boolean canBePaid() {
        return status == OrderStatus.CREATED || status == OrderStatus.PROCESSING;
    }

    public void process() {
        if (!canBeProcessed()) {
            throw new InvalidStatusTransitionException("Cannot process order in status: " + status);
        }
        this.status = OrderStatus.PROCESSING;
        this.updatedAt = Instant.now();
    }

    public void markAsPaid() {
        if (!canBePaid()) {
            throw new InvalidStatusTransitionException("Cannot pay for order in status: " + status);
        }
        this.status = OrderStatus.PAID;
        this.updatedAt = Instant.now();
    }

    public void complete() { 
        // ... // 
    }
    public void cancel()   {
        // ... //  
    }
}
```

Проблема: любой из четырёх методов (`process`, `markAsPaid`, `complete`, `cancel`) можно вызвать в любом состоянии — ошибка обнаружится только в runtime. 
Например, вызов `process()` у уже обработанного заказа или `markAsPaid()` у оплаченного — оба скомпилируются, но упадут на проде.

#### После

Каждое состояние заказа стало отдельным классом. `interface OrderState` и 
`abstract class AbstractOrderState` разрешают ровно пять реализаций — по одной на каждое состояние. 
Методы-переходы возвращают новый объект нужного типа, а недопустимые методы просто не существуют:

```java
// Создаю общий интерфейс для состояний заказа
public interface OrderState {
    Long getId();
    Long getOrderNumber();
    BigDecimal getTotalPrice();
    OrderStatus getStatus();
}

public abstract class AbstractOrderState implements OrderState {
    /* общие поля */
}

// Состояние CREATED — все 4 перехода доступны
public class CreatedOrder extends AbstractOrderState {
    public ProcessingOrder process()    { // ... //  }
    public PaidOrder markAsPaid()       { // ... //  }
    public CompletedOrder complete()    { // ... //  }
    public CancelledOrder cancel()      { // ... //  }
}

// Состояние PROCESSING — process() недоступен
public class ProcessingOrder extends AbstractOrderState {
    public PaidOrder markAsPaid()       { // ... //  }
    public CompletedOrder complete()    { // ... //  }
    public CancelledOrder cancel()      { // ... //  }
    // process() нету
}

// Состояние PAID — только complete() и cancel()
public class PaidOrder extends AbstractOrderState {
    public CompletedOrder complete()    { // ... //  }
    public CancelledOrder cancel()      { // ... //  }
    // process() и markAsPaid() нету
}

// Терминальные состояния — вообще нет методов перехода
public class CompletedOrder extends AbstractOrderState { }
public class CancelledOrder extends AbstractOrderState { }

// Фабрика: создаёт начальное состояние и восстанавливает из БД
public class OrderStateFactory {

    public static CreatedOrder createNew(Long orderNumber, BigDecimal totalPrice, String note) {
        Instant now = Instant.now();
        return new CreatedOrder(null, orderNumber, totalPrice, note, now, now);
    }

    public static OrderState fromPersisted(Long id, Long orderNumber, BigDecimal totalPrice, String note, 
                                           OrderStatus status, Instant createdAt, Instant updatedAt) {
        return switch (status) {
            case CREATED     -> new CreatedOrder(id, orderNumber, totalPrice, note, createdAt, updatedAt);
            case PROCESSING  -> new ProcessingOrder(id, orderNumber, totalPrice, note, createdAt, updatedAt);
            case PAID        -> new PaidOrder(id, orderNumber, totalPrice, note, createdAt, updatedAt);
            case COMPLETED   -> new CompletedOrder(id, orderNumber, totalPrice, note, createdAt, updatedAt);
            case CANCELLED   -> new CancelledOrder(id, orderNumber, totalPrice, note, createdAt, updatedAt);
        };
    }
}

// Обратное сопоставление: typestate → enum (для сохранения в БД)
public final class OrderStateMatcher {

    public static OrderStatus extractStatus(OrderState state) {
        return state.getStatus();
    }

    public static boolean isFinal(OrderState state) {
        return state instanceof CompletedOrder || state instanceof CancelledOrder;
    }
}
```

Для восстановления состояния из БД и создания новых заказов добавлены `OrderStateFactory` (с `switch` по enum) и 
`OrderStateMatcher` (для обратного сопоставления с enum).
Раньше код мог упасть из-за ошибочного порядка вызова и упасть падал в runtime. 
Теперь вызов `processing.process()` или `paid.markAsPaid()` просто не скомпилируется — у этих классов нет таких методов. 
Терминальные состояния `CompletedOrder` и `CancelledOrder` вообще не имеют методов перехода, так что попытка изменить завершённый заказ — ошибка компиляции. 
Общие данные (id, номер, сумма, примечание) вынесены в `AbstractOrderState`, а конкретные классы содержат только логику переходов. 
Фабрика `OrderStateFactory.fromPersisted()` позволяет восстановить нужный класс-состояние при загрузке из БД через `switch`.

---

### Пример 2. Импорт цены товаров

#### До

Оригинальный `PricemportService.importFromUrl()` выполнял 4 последовательных этапа (категории, preload контекста, товары, batch flush) 
в одном методе под `@Transactional`. 
Все этапы вызывались напрямую, порядок обеспечивался только текстуальным следованием в коде:

```java
@Service
public class StemYmlImportService {

    @Transactional
    public ImportResult importFromUrl(String url) throws Exception {
        // 1: категории
        Map<String, Category> categoryByXmlId;
        try (InputStream is = new URL(url).openStream()) {
            XMLStreamReader reader = createReader(is);
            categoryByXmlId = categoryImporter.importCategories(reader);
        }

        // 2: preload
        ImportContext ctx = prepareContext(categoryByXmlId);

        // 3: товары
        try (InputStream is = new URL(url).openStream()) {
            XMLStreamReader reader = createReader(is);
            // ... парсинг offers
            offerImporter.importOffers(reader, ctx);
        }

        // 4: flush
        flush(ctx);

        return new ImportResult(true,
            categoryByXmlId.size(),
            ctx.getProductsToSave().size(), null);
    }
}
```

Проблема: если кто-то захочет переиспользовать отдельные этапы (например, для частичного импорта или тестирования), 
он может случайно вызвать их в неверном порядке — скажем, `flush()` до `importOffers()`. 
Кроме того, все данные между этапами передаются через неявную мутацию общего `ImportContext`, что затрудняет понимание, какие данные доступны на каждом этапе.

#### После

Каждый этап пайплайна стал отдельным классом-состоянием. 
`interface YmlImportState` разрешает ровно пять реализаций, соответствующих этапам. 
Метод каждого состояния возвращает объект следующего типа, а функциональные интерфейсы инкапсулируют реальную работу (чтобы классы состояний не зависели от Spring-бинов):

```java
public interface YmlImportState {
    int getCategoriesImported();
}

// 1: только importCategories() доступен
public class YmlImportReady implements YmlImportState {
    public YmlImportCategoriesLoaded importCategories(CategoryImportAction action) {
        // ... //
    }
    // prepareContext(), importOffers(), flush() — нету
}

// 2: только prepareContext() доступен
public class YmlImportCategoriesLoaded implements YmlImportState {
    public YmlImportContextReady prepareContext(ContextPreparationAction action) {
        // ... //
    }
    // importOffers(), flush() — нету
}

// 3: только importOffers() доступен
public class YmlImportContextReady implements YmlImportState {
    public YmlImportOffersParsed importOffers(OffersImportAction action) {
        // ... //
    }
    // flush() — нету
}

// 4: только flush() доступен
public class YmlImportOffersParsed implements YmlImportState {
    public YmlImportCompleted flush(FlushAction action) {
        // ... //
    }
    // importCategories(), prepareContext() — нету
}

// Терминальное — нет методов перехода
public class YmlImportCompleted implements YmlImportState { }

// Фабрика начального состояния
public final class YmlImportFactory {

    public static YmlImportReady fromUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL для импорта не может быть пустым");
        }
        return new YmlImportReady(url.trim());
    }
}
```

Использование:

```java
YmlImportCompleted completed = YmlImportFactory.fromUrl("https://sturm.ru/catalog.xml")
    .importCategories(url -> categoryImporter.importCategories(url))
    .prepareContext((url, cats) -> prepareContext(cats))
    .importOffers((url, ctx) -> offerImporter.importOffers(ctx))
    .flush(ctx -> flushAll(ctx));
// completed: YmlImportCompleted — терминальное, дальше ничего нельзя
```

Теперь невозможно вызвать `flush()` до `importOffers()` — метода просто нет у `YmlImportContextReady`. 
Нельзя перепрыгнуть через этап — `YmlImportReady` не имеет метода `importOffers()`. 
Каждый этап явно получает от предыдущего ровно те данные, которые нужны, через поля класса и параметры функционального интерфейса. 
Реальная работа с БД, XML и Spring-репозиториями вынесена в лямбды, передаваемые при вызове метода перехода — 
это позволяет тестировать пайплайн с моковыми действиями без поднятия Spring-контекста. 
Фабрика `YmlImportFactory.fromUrl()` валидирует входные данные и является единственной точкой входа.

---

### Пример 3. Email уведомления на почту админу

#### До

Оригинальный `EmailService` отправлял email через `JavaMailSender` с аннотациями Spring Retry (`@Retryable` / `@Recover`). 
Состояние уведомления было неявным: либо email находился в retry-цикле Spring, либо после исчерпания попыток попадал в `@Recover`, 
где сохранялся как `FailedEmail` в БД. 
Явного объекта, представляющего lifecycle, не было:

```java
@Service
public class EmailService {
    public final JavaMailSender mailSender;
    private final FailedEmailRepository failedEmailRepository;

    @Retryable(
        retryFor = {MailException.class, MessagingException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 3000, multiplier = 2)
    )
    public void sendOrderCreatedEmail(OrderCreatedEvent event)
            throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        // ... настройка message ...
        mailSender.send(message);
    }

    @Recover
    public void recover(Exception ex, OrderCreatedEvent event) {
        failedEmailRepository.save(FailedEmail.from(payload, OrderStatus.CREATED, ex, objectMapper));
    }
}
```

Проблема: lifecycle email полностью управляется Spring Retry. 
Нельзя явно создать объект «ожидаемый email», попробовать отправить, получить результат в виде объекта состояния и решить, что делать дальше (retry или сохранить). 
Повторная отправка после recover здесь не предусмотрена — только ручное вмешательство. 
Состояние (pending / sending / sent / failed) нигде не моделируется.

#### После

Жизненный цикл email стал явным через `interface EmailNotificationState` с тремя состояниями. 
Каждое состояние содержит только те методы, которые допустимы. 
Реальная отправка инкапсулирована в функциональный интерфейс:

```java
public interface EmailNotificationState {
    EmailType getEmailType();
    String getRecipient();
}

// PENDING — только send()
public class PendingEmail implements EmailNotificationState {
    public EmailNotificationState send(EmailSendAction action) {
        attemptCount++;
        try {
            action.execute(recipient, subject, htmlContent);
            return new SentEmail(emailType, recipient, subject);
        } catch (Exception ex) {
            return new FailedEmailState(emailType, recipient, subject, htmlContent, ex.getMessage(), attemptCount);
        }
    }
    // retry() — нету (нельзя retry-ть то, что ещё не отправлялось)
}

// SENT — терминальное, нет методов перехода
public class SentEmail implements EmailNotificationState { }

// FAILED — можно retry()
public class FailedEmailState implements EmailNotificationState {
    public EmailNotificationState retry(EmailSendAction action) {
        try {
            action.execute(recipient, subject, htmlContent);
            return new SentEmail(emailType, recipient, subject);
        } catch (Exception ex) {
            return new FailedEmailState(emailType, recipient, subject, htmlContent, ex.getMessage(), failedAttempts + 1);
        }
    }
    // send() — нету (для повторной используется retry())
}

// Фабрика начального состояния
public class EmailNotificationFactory {

    public static PendingEmail create(EmailType emailType, String recipient, String subject, String htmlContent) {
        if (emailType == null) {
            throw new IllegalArgumentException("Тип email не может быть null");
        }
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("Получатель не может быть пустым");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Тема письма не может быть пустой");
        }
        return new PendingEmail(emailType, recipient.trim(), subject, htmlContent);
    }
}
```

Использование:  
```java
PendingEmail pending = EmailNotificationFactory.create(EmailType.ORDER_CREATED, "toolsmarket48@yandex.ru", "Новый заказ #54223", "<h1>Детали заказа</h1>");

EmailNotificationState result = pending.send((to, subj, body) -> mailSender.send(...));

if (result instanceof SentEmail sent) {
    // Успешно
} else if (result instanceof FailedEmailState failed) {
    // Повторная попытка
    EmailNotificationState retryResult = failed.retry((to, subj, body) -> mailSender.send(...));
}
```

Состояние email стало явным — мы видим тип переменной и сразу понимаем, что с ним можно делать. 
`PendingEmail` не имеет метода `retry()` — невозможно повторить отправку, которая ещё не выполнялась. 
`SentEmail` не имеет никаких методов перехода — отправленный email нельзя «переотправить» или «пометить как ошибку». 
`FailedEmailState` не имеет метода `send()` — для повторной используется `retry()`, который семантически отражает именно повторную попытку и инкрементирует счётчик. 
Результат `send()` и `retry()` — это `EmailNotificationState`, то есть вызывающий код обязан обработать оба исхода (через `instanceof` или pattern matching). 
Счётчик попыток хранится в состоянии и инкрементируется при каждом вызове. 
Все объекты состояния иммутабельны (кроме `attemptCount` у `PendingEmail`), 
что делает retry безопасным — оригинальный `FailedEmailState` не меняется при повторной попытке.
---

### Вывод

После изучения TOP, в голове так и крутится что типы это состояния, я состояния - это типы.

Первое, что вижу - это строгая типизация через состояния.
Используя интерфейсы и отдельные классы для каждого состояния, мы получаем контроль доступных методов на уровне компилятора.
Попытка вызвать `processing.process()` или `paid.markAsPaid()` просто не скомпилируется.

Второе - это то что каждый класс-состояние отвечает только за действия в одном конкретном состоянии.
Состояние определяется через тип объекта, а не через внутренний enum или булевы флаги. 
Чтение кода становится проще: видишь `ProcessingOrder` — сразу понимаешь, 
что можно вызывать только `markAsPaid()`, `complete()`, `cancel()`, а `process()` недоступен.

Третье - повышение безопасности и предотвращение ошибок.
Терминальные состояния (`CompletedOrder`, `CancelledOrder`, `SentEmail`, `YmlImportCompleted`) вообще не имеют методов перехода — 
любая попытка изменить финальное состояние является ошибкой компиляции. 
В оригинальном коде те же проверки выполнялись через `isValidTransition()`, `canBeCancelled()` и т.д., а ошибки обнаруживались только на проде через исключения.

И также отмечу моменты (по моему мнению) когда TOP не нужен.  
Его не стоит применять к объектам, которые не имеют выраженных состояний, если у объекта два состояния и один метод, проще использовать булев флаг.
То есть если состояния фиксированы и не подвержены дальнейшему изменению или не планируется добавления новых, то ТОР это будет перебор как я думаю.
Или переходы очень сложные и запутанные.
В целом, TOP — это мощный инструмент для моделирования бизнес-процессов с чётко определёнными стадиями и запретами. 


