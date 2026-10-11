### Повышаем полиморфность кода

Как и в прошлых заданиях, работаю в основном проекте интернет-магазина **_ToolsMarket_** (18 Вольт).

Долго думал над заданием, потому что в прикладном коде привык ровно к обратному: чем подробнее имя и
точнее тип, тем понятнее. Но именно это и есть мономорфный тупик. Чем конкретнее сигнатура, тем больше
сущностей ей соответствуют (больше способов её реализовать и сломать) и тем длиннее приходится писать имена,
чтобы удержать внутри неё смысл. Идея задания обратная: **делать тип как можно более общим, а не точным** -
обобщение сужает пространство реализаций, и тогда описательные имена уже не нужны, потому что уточнять
становится нечего.

Искал в проекте не «полиморфизм ради полиморфизма», а места, где под каждый конкретный тип заведена своя
почти одинаковая копия логики. Таких мест набралось пять, и все они лечатся одним: конкретику убрать в
параметр (функцию, generic или наименее мощный интерфейс), а саму форму оставить одну.

___

#### Пример 1 - «Два списочных маппера в один параметрический»

CategoryMapperService, под каждую пару типов был свой метод, хотя отличаются они только
одним — какой именно маппер применить.

**_Было:_**

```java
// Дополнительные методы для коллекций
public List<CategoryDto> toDtoList(List<Category> categories) {
    return categories.stream()
            .map(this::toDto)
            .collect(Collectors.toList());
}

public List<CategorySimpleDto> toSimpleDtoList(List<Category> categories) {
    return categories.stream()
            .map(this::toSimpleDto)
            .collect(Collectors.toList());
}
```

Здесь зашиты сразу три конкретности: стандартный тип входа List, типы элементов и жёсткая связка
«входной тип это конкретный метод этого класса». Чтобы добавить третью пару, пришлось бы написать третий
метод-клон.

**_Стало:_**

```java
// Тип источника S и тип результата T не зафиксированы, а само преобразование
// передаётся функцией — одна реализация покрывает любые пары.
public static <S, T> List<T> mapAll(Iterable<S> source, Function<S, T> mapper) {
    List<T> result = new ArrayList<>();
    for (S item : source) {
        result.add(mapper.apply(item));
    }
    return result;
}

// вызов на месте старого toDtoList
return CategoryMapperService.mapAll(categories, categoryMapperService::toDto);
```

Тип входа ослаблен до Iterable (это всё, что нужно циклу), типы элементов обобщены до S/T, а «какой
именно маппер» стал параметром-функцией. Реализаций теперь ровно одна, и новый тип в список подключается
одним вызовом, без нового кода.

___

#### Пример 2 - «Сумма корзины знает только о PricedLine, а не о CartItemDto»

Сервис CartService, методу для подсчёта суммы нужны всего лишь цена и количество, а он требовал
конкретный CartItemDto.

**_Было:_**

```java
public BigDecimal calculateSummary(List<CartItemDto> items) {
    return items.stream()
            .map(item -> item.getUnitPrice()
                    .multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
}
```

Это ровно «метод, которому нужна фигура, но который требует шестиугольник»: он в курсе про весь DTO
целиком, хотя использует два геттера, и физически не примет другую позицию (например, строку заказа).

**_Стало:_**

```java
// Наименее мощный интерфейс позиции — только то, что нужно для суммы
public interface PricedLine {
    BigDecimal getUnitPrice();
    Integer getQuantity();
}

public class CartItemDto implements PricedLine { // ... //  }

public BigDecimal calculateSummary(Iterable<? extends PricedLine> items) {
    BigDecimal total = BigDecimal.ZERO;
    for (PricedLine item : items) {
        total = total.add(item.getUnitPrice()
                .multiply(BigDecimal.valueOf(item.getQuantity())));
    }
    return total;
}
```

Интерфейс прячет от метода весь внешний мир CartItemDto (универсальный квантор в действии): методу
доступны только цена и количество, испортить что-то ещё он не может. Iterable<? extends PricedLine> -
самый слабый из подходящих типов коллекции. Любая будущая реализация позиции подхватывается без правок.

___

#### Пример 3 - «Два билдера письма → один полиморфный билд»

Сервис EmailNotificationFactory письма о заказе и о регистрации собирались двумя методами
с одинаковым «скелетом»: Context → переменные → templateEngine.process → PendingEmail.

**_Было:_**

```java
public static PendingEmail createOrderEmail(OrderCreatedEvent event, ... ) {
    Context context = new Context();
    context.setVariable("order", event);
    context.setVariable("items", event.orderItems());
    context.setVariable("total", event.total());
    context.setVariable("note", event.note());
    context.setVariable("formattedDate", LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));

    String htmlContent = templateEngine.process("email/order-created", context);
    String subject = "Новый заказ инструментов #" + event.orderNumber();
    return new PendingEmail(adminEmail, subject, htmlContent);
}

public static PendingEmail createRegistrationEmail(UserRegistrationEvent event, ...) {
    Context context = new Context();
    context.setVariable("user", event);
    context.setVariable("formattedDate", LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")));

    String htmlContent = templateEngine.process("email/user-registration", context);
    String subject = "Новый пользователь зарегистрировался на ToolsMarket48";
    return new PendingEmail(adminEmail, subject, htmlContent);
}
```

Различаются только данными: шаблон, тема, формат даты и список переменных. Всё остальное продублировано.

**_Стало:_**

```java
private static PendingEmail build(String template, String subject,
                                  DateTimeFormatter dateFormat,
                                  Consumer<Context> populate,
                                  String adminEmail,
                                  SpringTemplateEngine templateEngine) {
    Context context = new Context();
    context.setVariable("formattedDate", LocalDateTime.now().format(dateFormat));
    populate.accept(context);

    String htmlContent = templateEngine.process(template, context);
    return new PendingEmail(adminEmail, subject, htmlContent);
}

public static PendingEmail createOrderEmail(OrderCreatedEvent event, ...) {
    return build("email/order-created",
            "Новый заказ инструментов #" + event.orderNumber(),
            ORDER_DATE,
            context -> {
                context.setVariable("order", event);
                context.setVariable("items", event.orderItems());
                context.setVariable("total", event.total());
                context.setVariable("note", event.note());
            },
            adminEmail, templateEngine);
}
```

«Что подставить в письмо» - это Consumer<Context>, то есть поведение стало параметром. Форма сборки письма
теперь одна на все уведомления, новый вид письма — это новые данные в вызове build, а не ещё одна копия
скелета. Публичные методы оставил прежними, чтобы не ломать EmailService.

___

#### Пример 4 - «Картинки товара и категории сохранялись двумя почти одинаковыми методами»

Сервис ImageStorageService, методы saveImage и saveCategoryImage отличались подпапкой, лимитом размера,
префиксом имени файла и типом результата — при полностью одинаковой записи на диск.

**_Было:_**

```java
public ProductImage saveImage(MultipartFile file, String productTitle) {
    if (file.isEmpty()) throw new IllegalArgumentException("Файл пустой");
    if (!isImage(file)) throw new IllegalArgumentException("Файл не является изображением");
    try {
        Path uploadDir = Paths.get(uploadPath, "products");
        Files.createDirectories(uploadDir);
        String fileExtension = getFileExtension(file.getOriginalFilename());
        String fileName = generateFileName(productTitle, fileExtension);
        Files.write(uploadDir.resolve(fileName), file.getBytes());
        return ProductImage.builder()
                .url(baseUrl + "/images/products/" + fileName)
                .alt(productTitle).sortOrder(0).build();
    } catch (IOException e) { throw new RuntimeException(...); }
}

public String saveCategoryImage(MultipartFile file, String categoryTitle) {
    // то же самое, но "categories", лимит 2MB и generateCategoryFileName
}
```

**_Стало:_**

```java
private String storeFile(MultipartFile file, String folder, String title,
                         long maxBytes, BiFunction<String, String, String> nameFactory) {
    if (file.isEmpty()) throw new IllegalArgumentException("Файл пустой");
    if (!isImage(file)) throw new IllegalArgumentException("Файл не является изображением");
    if (maxBytes > 0 && file.getSize() > maxBytes) {
        throw new IllegalArgumentException("Размер изображения не должен превышать "
                + (maxBytes / (1024 * 1024)) + "MB");
    }
    try {
        Path dir = Paths.get(uploadPath, folder);
        Files.createDirectories(dir);
        String ext = getFileExtension(file.getOriginalFilename());
        String fileName = nameFactory.apply(title, ext);
        Files.write(dir.resolve(fileName), file.getBytes());
        return baseUrl + "/images/" + folder + "/" + fileName;
    } catch (IOException e) { throw new RuntimeException("Не удалось сохранить изображение: " + e.getMessage(), e); }
}

public ProductImage saveImage(MultipartFile file, String productTitle) {
    String url = storeFile(file, "products", productTitle, 0, this::generateFileName);
    return ProductImage.builder().url(url).alt(productTitle).sortOrder(0).build();
}

public String saveCategoryImage(MultipartFile file, String categoryTitle) {
    return storeFile(file, "categories", categoryTitle, 2L * 1024 * 1024,
            this::generateCategoryFileName);
}
```

Подпапка и лимит стали значениями-параметрами, а политика имени файла - функцией (BiFunction, куда
передаётся generateFileName/generateCategoryFileName). Один код записи обслуживает оба вида картинок;
третий вид — это новые аргументы, а не третий метод.

___

#### Пример 5 - «Реальный импорт прайса и dry-run - один проход scan»

Сервис PriceImportService, методы importPrices и dryRunImport содержали одинаковый цикл анализа строк
(сбор изменений, одинаковых цен, ненайденных SKU). Разница лишь в том, что делает импорт с найденным
изменением, и каким запросом грузятся товары.

**_Было:_** (по сути два раза один и тот же цикл)

```java
for (PriceRow row : rows) {
    Product product = productMap.get(row.sku());
    if (product == null) { notFound.add(row.sku()); continue; }
    if (product.getPrice().compareTo(row.price()) != 0) {
        changes.add(new PriceChange(row.sku(), product.getTitle(),
                product.getPrice(), row.price()));
        // только в importPrices:
        product.setPrice(row.price());
        product.setUpdatedAt(Instant.now());
        productsToUpdate.add(product);
    } else { samePrice++; }
}
```

**_Стало:_**

```java
private PriceScan scan(List<PriceRow> rows, Map<String, Product> productMap,
                       BiConsumer<Product, PriceRow> onChanged) {
    List<PriceChange> changes = new ArrayList<>();
    int samePrice = 0;
    List<String> notFound = new ArrayList<>();

    for (PriceRow row : rows) {
        Product product = productMap.get(row.sku());
        if (product == null) { notFound.add(row.sku()); continue; }
        if (product.getPrice().compareTo(row.price()) != 0) {
            changes.add(new PriceChange(row.sku(), product.getTitle(),
                    product.getPrice(), row.price()));
            if (onChanged != null) onChanged.accept(product, row); // поведение — параметр
        } else { samePrice++; }
    }
    return new PriceScan(changes, samePrice, notFound);
}

// import: поведение «применить и накопить» + findAllBySkus
scan(rows, loadProducts(rows, productRepository::findAllBySkus), (product, row) -> {
    product.setPrice(row.price());
    product.setUpdatedAt(Instant.now());
    productsToUpdate.add(product);
});

// dry-run: поведения нет, только посчитать + findAllBySkusOptimized
scan(rows, loadProducts(rows, productRepository::findAllBySkusOptimized), null);
```

Проход по прайсу описан один раз. «Что делать с изменением» — это BiConsumer (в dry-run null - только
посчитать), а загрузчик товаров - тоже параметр (Function<Set<String>, List<Product>>), поэтому import и
предпросмотр ходят в БД разными запросами, не дублируя цикл. Ход и результат импорта при этом сохранены.

___

**_Вывод:_**

Раньше я считал «хорошим кодом» максимально точные типы и длинные говорящие имена. Но именно точность
множества и порождает мономорфизм: чем конкретнее тип, тем больше сущностей и реализаций ему соответствует,
и тем больше мест, где можно ошибиться. Обобщение делает обратное - сокращает пространство реализаций до
одной-двух и снимает необходимость уточнять имена, потому что уточнять больше нечего.

Для себя вынес три рабочих приёма, которыми и повышал полиморфизм в этих пяти местах:
1. **Наименее мощный интерфейс** - если методу нужны только getUnitPrice()/getQuantity(), он должен
   зависеть от узкого интерфейса (PricedLine), а не от всего DTO (пример 2).
2. **Функция как параметр** - то, что раньше было отдельной копией метода (маппер, наполнение письма,
   генерация имени файла, действие с изменением цены), передаётся в единственную реализацию аргументом.
3. **Тип как можно менее конкретный** - List<X> в Iterable<S>, подтипы - wildcards; берём самый слабый
   из подходящих типов.

Обратная сторона - соблазн обобщить всё подряд. Полиморфизм оправдан там, где вариантов реализации много и
именно их-то мы и хотим сократить; для одноразового куска кода это будет уже переусложнение. Но теперь,
когда вижу третью копию одного и того же метода под очередной конкретный тип, я сначала думаю, что здесь
является параметром, а не пишу четвёртую копию.


