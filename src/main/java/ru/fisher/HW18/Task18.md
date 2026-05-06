## Интерфейс компактнее реализации?

### Призрачные состояния

**_Пример 1:_**
```java
public void addNote(Long orderId, String note) {
    validateNote(note);  // скрытое состояние
    Order order = getOrder(orderId);
    order.setNote(note);
    orderRepository.save(order);
}

private void validateNote(String note) {
    if (!StringUtils.hasText(note)) {
        throw new OrderValidationException("note", "Примечание не может быть пустым");
    }
    if (note.length() > 1000) {
        throw new OrderValidationException("note", "Примечание слишком длинное");
    }
}

///---------------------///

// Создаем тип, который включает ограничения
public record Note(String value) {
    public Note {
        if (value == null || value.isBlank()) {
            throw new OrderValidationException("Примечание не может быть пустым");
        }
        if (value.length() > 1000) {
            throw new OrderValidationException("Примечание слишком длинное (макс. 1000)");
        }
    }
}

public void addNote(Long orderId, Note note) {
    Order order = getOrder(orderId);
    order.setNote(note.value());
    orderRepository.save(order);
}
```

**_Пример 2:_**
```java
private List<PriceRow> parseSheet(Sheet sheet) {
    // Скрытые поля, влияют на поведение циклов указывают
    // на нахождение столбцов
    int articleCol = -1;
    int priceCol = -1;
    int headerRowIndex = -1;

    // Ищем строку заголовков
    for (Row row : sheet) {
        for (Cell cell : row) {
            if (cell.getCellType() == CellType.STRING) {
                String value = cell.getStringCellValue().toLowerCase();

                if (value.contains("артикул") || value.contains("sku") || value.contains("код")) {
                    articleCol = cell.getColumnIndex();
                }
                if (value.contains("прайс") || value.contains("цена") || value.contains("price")) {
                    priceCol = cell.getColumnIndex();
                }
            }
        }

        if (articleCol != -1 && priceCol != -1) {
            headerRowIndex = row.getRowNum();
            break;
        }
    }

    // Если не нашли заголовки в этом листе
    if (headerRowIndex == -1) {
        log.debug("Sheet '{}': no headers found, skipping", sheet.getSheetName());
        return List.of();
    }

    // Читаем строки товаров
    List<PriceRow> result = new ArrayList<>();

    for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum(); i++) {
        Row row = sheet.getRow(i);
        if (row == null) continue;

        String article = getString(row.getCell(articleCol));
        BigDecimal price = getPrice(row.getCell(priceCol));

        if (article == null || price == null) {
            continue; // не товар
        }

        result.add(new PriceRow(article, price));
    }

    return result;
}
```
___

### Погрешность/неточность

**_Пример 1:_**
Здесь жестко привязаны определенные артикулы, которые сужают логику кода и ограничивают её.
```java
// Из предыдущего примера также видно
private List<PriceRow> parseSheet(Sheet sheet) {
    // Скрытые поля, влияют на поведение циклов указывают
    // на нахождение столбцов
    int articleCol = -1;
    int priceCol = -1;
    int headerRowIndex = -1;

    // Ищем строку заголовков
    for (Row row : sheet) {
        for (Cell cell : row) {
            if (cell.getCellType() == CellType.STRING) {
                String value = cell.getStringCellValue().toLowerCase();

                // Жетская привязка к определенным артикулам
                if (value.contains("артикул") || value.contains("sku") || value.contains("код")) {
                    articleCol = cell.getColumnIndex();
                }
                if (value.contains("прайс") || value.contains("цена") || value.contains("price")) {
                    priceCol = cell.getColumnIndex();
                }
            }
        }

        if (articleCol != -1 && priceCol != -1) {
            headerRowIndex = row.getRowNum();
            break;
        }
    }

    // Если не нашли заголовки в этом листе
    if (headerRowIndex == -1) {
        log.debug("Sheet '{}': no headers found, skipping", sheet.getSheetName());
        return List.of();
    }

    // Читаем строки товаров
    List<PriceRow> result = new ArrayList<>();

    for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum(); i++) {
        Row row = sheet.getRow(i);
        if (row == null) continue;

        String article = getString(row.getCell(articleCol));
        BigDecimal price = getPrice(row.getCell(priceCol));

        if (article == null || price == null) {
            continue; // не товар
        }

        result.add(new PriceRow(article, price));
    }

    return result;
}

// Выносим в отдельную конфигурацию настройки по парсингу листа Excel
/**
 * Конфигурация парсинга листа Excel
 * @param articleColumnKeywords - ключевые слова для поиска колонки артикула
 * @param priceColumnKeywords - ключевые слова для поиска колонки цены
 * @param skipRowsBeforeHeader - сколько строк пропустить перед поиском заголовка
 * @param skipEmptyRows - пропускать ли пустые строки
 */
public record SheetParseConfig(
        List<String> articleColumnKeywords,
        List<String> priceColumnKeywords,
        int skipRowsBeforeHeader,
        boolean skipEmptyRows
) {
    public static final SheetParseConfig DEFAULT = new SheetParseConfig(
            List.of("артикул", "sku", "код", "article", "code"),
            List.of("прайс", "цена", "price", "cost"),
            0,
            true
    );

    public static SheetParseConfig russian() {
        return new SheetParseConfig(
                List.of("артикул", "код", "sku"),
                List.of("прайс", "цена", "price"),
                0,
                true
        );
    }

    public static SheetParseConfig english() {
        return new SheetParseConfig(
                List.of("sku", "article", "code"),
                List.of("price", "cost"),
                0,
                true
        );
    }
}

// Улучшенный метод
private List<PriceRow> parseSheet(Sheet sheet, SheetParseConfig config) {
    ColumnIndices indices = findColumnIndices(sheet, config);

    if (!indices.found()) {
        log.debug("Sheet '{}': no headers found", sheet.getSheetName());
        return List.of();
    }

    return readPriceRows(sheet, indices, config);
}

// Результат поиска колонок - явный тип
private record ColumnIndices(
        int articleCol,
        int priceCol,
        int headerRowIndex
) {
    public boolean found() {
        return articleCol != -1 && priceCol != -1 && headerRowIndex != -1;
    }
}

private ColumnIndices findColumnIndices(Sheet sheet, SheetParseConfig config) {
    int startRow = config.skipRowsBeforeHeader();

    for (int rowNum = startRow; rowNum <= sheet.getLastRowNum(); rowNum++) {
        Row row = sheet.getRow(rowNum);
        if (row == null) continue;

        int articleCol = -1;
        int priceCol = -1;

        for (Cell cell : row) {
            if (cell.getCellType() != CellType.STRING) continue;

            String value = cell.getStringCellValue().toLowerCase();

            if (matchesKeyword(value, config.articleColumnKeywords())) {
                articleCol = cell.getColumnIndex();
            }
            if (matchesKeyword(value, config.priceColumnKeywords())) {
                priceCol = cell.getColumnIndex();
            }
        }

        if (articleCol != -1 && priceCol != -1) {
            return new ColumnIndices(articleCol, priceCol, rowNum);
        }
    }

    return new ColumnIndices(-1, -1, -1);
}

private boolean matchesKeyword(String value, List<String> keywords) {
    return keywords.stream().anyMatch(value::contains);
}
```

**_Пример 2:_**
Существует вспомогательный класс форматирования цены, узкий интерфейс, жестко захаркоженая логика и 
сами валюты как строки через switch.
```java
public class PriceFormatter {

    private static final DecimalFormat PRICE_FORMAT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.forLanguageTag("ru-RU"));
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');

        PRICE_FORMAT = new DecimalFormat("#,###", symbols);
        PRICE_FORMAT.setGroupingUsed(true);
        PRICE_FORMAT.setGroupingSize(3);
    }

    public static String format(BigDecimal price) {
        if (price == null) {
            return "0";
        }
        // Округляем до целых рублей
        BigDecimal rounded = price.setScale(0, java.math.RoundingMode.HALF_UP);
        return PRICE_FORMAT.format(rounded);
    }

    public static String formatWithCurrency(BigDecimal price, String currency) {
        String formattedPrice = format(price);
        return formattedPrice + " " + getCurrencySymbol(currency);
    }

    private static String getCurrencySymbol(String currency) {
        return switch (currency.toUpperCase()) {
            case "RUB", "RUR" -> "₽";
            case "USD" -> "$";
            case "EUR" -> "€";
            default -> currency;
        };
    }
}

// Форматируем цену в указанной валюте. Создаем поддерживаемый и расширяемый набор валют через enum Currency
public enum Currency {
    RUB("₽", "рубль"),
    USD("$", "доллар"),
    EUR("€", "евро");

    private final String symbol;
    private final String name;

    // --- //
}
```

**_Пример 3:_**
Существует метод поиска товаров в админке, здесь жестко зашиты поля, по которым можно делать поиск.
```java
public Page<ProductAdminDto> search(
        String name,
        String sku,
        Long categoryId,
        Boolean active,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Pageable pageable) {

    Specification<Product> spec =
            (root, query, cb) -> cb.conjunction();

    if (name != null && !name.isBlank()) {
        spec = spec.and(ProductSpecification.nameLike(name));
    }

    if (sku != null && !sku.isBlank()) {
        spec = spec.and(ProductSpecification.skuLike(sku));
    }

    if (categoryId != null) {
        spec = spec.and(ProductSpecification.hasCategory(categoryId));
    }

    if (active != null) {
        spec = spec.and(ProductSpecification.hasStatus(active));
    }

    if (minPrice != null) {
        spec = spec.and(ProductSpecification.minPrice(minPrice));
    }

    if (maxPrice != null) {
        spec = spec.and(ProductSpecification.maxPrice(maxPrice));
    }

    Page<Product> productPage =
            productRepository.findAll(spec, pageable);

    return productPage.map(this::mapToDto);
}
```
Что делаем, изменяем сигнатуру и через паттерн строитель можем формировать определенные фильтры
```java
public interface ProductFilter {
    boolean matches(Product product);
}

public class ProductSearchRequest {
    private final List<ProductFilter> filters;
    private final Pageable pageable;
    
    // Строитель для удобного добавления фильтров
}

public Page<ProductAdminDto> search(ProductSearchRequest request) {
    // гибкий поиск без изменения сигнатуры
}
```

___

### Когда интерфейс явно не должен быть проще реализации
**_Пример 1:_**
```java
public interface TransferService {
    void transfer(Account from, Account to, BigDecimal amount);
}
```
Метод перевода денег, не учитывает множество факторов - нельзя понять результат, узнать комиссию, обработать ошибку и т.д.  
Если создать отдельный класс как результат и запрос перевода, в котором будут показаны все действия.
```java
public interface TransferService { 
    TransferResult transfer(TransferRequest request);
}

record TransferRequest(
        AccountId from, 
        AccountId to, 
        Money amount,
        TransferOptions options
) {}

record TransferResult(
        boolean success, 
        String error, 
        BigDecimal fee, 
        BigDecimal totalAmount, 
        Instant timestamp
) {}
```

**_Пример 2:_**
```java
public interface DataExporter {
    void export(String data, String destination);
}
```
Экспорт данных, сейчас просто данные и назначение. Но нужно учитывать много факторов - формат, тип файла, кодировка, разрешить ли перезапись и т.д.  
Лучше раскрыть бизнес логику добавив ExportRequest и ExportResult.

```java
public interface DataExporter {
    ExportResult export(ExportRequest request);
}

public record ExportRequest(
        String data, 
        DataFormat format,      // json, csv, xml
        DestinationType target, // file, url, email
        String destination,
        Charset encoding,
        boolean overwrite
) {}

public record ExportResult(
        boolean success, 
        String destinationPath, 
        ExportStatistics stats, 
        Optional<String> errors
) {}
```

**_Пример 3:_**
```java
public interface EmailSender {
    void send(String to, String subject, String body);
}
```
Отправка сообщений, учитывает много факторов и возможно параметры конфигурации были бы кстати в этом случае и
следует лучше расширить интерфейс с помощью типа EmailRequest.

```java
public interface EmailSender {
    EmailResult send(EmailRequest request);
}

public record EmailRequest(
        String to, 
        String from, 
        String subject, 
        String body, 
        List<String> cc, 
        List<Attachment> attachments, 
        Integer priority
) {}
```

Интерфейс не должен быть проще реализации в тех случаях, когда в реализации присутствует существенная для пользователя логика: 
параметры конфигурации, бизнес-правила или различные исходы выполнения. Если важная логика скрыта стоит расширить интерфейс через типы.  
Упрощение интерфейса в таких случаях приводит к тому, что пользователь не понимает, как правильно использовать API 
и какие гарантии он получает, а также как обрабатывать результат.  
Лучше скрыть как это работает, но при этом нужно показывать как этим пользоваться.

