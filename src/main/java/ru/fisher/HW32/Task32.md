### Как проектировать программы in small (ко-рекурсия)

Как и в прошлых заданиях, работаю в основном проекте интернет-магазина **_ToolsMarket_** (18 Вольт).

Прочитал материал к заданию и статью «How to Design Co-Programs». 
Признаюсь честно: до этого я всегда строил функцию от входных данных.
Есть список - иду по нему циклом. Есть строка - режу её на части. А результат получался как-то сам
собой, в конце. В статье предлагается смотреть на задачу с другой стороны: сначала понять, **каким**
должен быть результат, и строить программу по его структуре. Для этого к результату задаётся три вопроса:

1. Когда выход пуст (тривиальный случай)?
2. Если выход не пуст, что у него в «голове» (первая часть результата)?
3. Из каких данных рекурсивно собирается его «хвост»?

Взял три метода из своего проекта, где выходной тип достаточно сложный, и переписал их по этой схеме.
Было сложно найти в своем проекте подходящие методы, нашел что смог.

___

#### Пример 1 - «CreateOrder: заказ собираю с головы, а не обхожу корзину»

В сервисе OrderService есть метод createOrder он создает из корзины заказ. 
На выходе: Order с итоговой ценой totalPrice и коллекция OrderItem (у каждой позиции считается цена, скидка и сумма). 
Как это работало «от входа»: обходим корзину циклом и тащим с собой две переменные-аккумулятора - total и totalDiscount.

**_Было:_**

```java
Order order = Order.builder()
        .orderNumber(orderNumberGenerator.next(user.getId()))
        .user(user)
        .note(note)
        .status(OrderStatus.CREATED)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .orderItems(new HashSet<>())
        .build();

BigDecimal total = BigDecimal.ZERO;
BigDecimal totalDiscount = BigDecimal.ZERO;

// Скидки пользователя — один запрос на заказ вместо запроса на позицию
Map<ProductType, BigDecimal> discounts = discountService.getDiscountsForUser(user);

for (CartItem ci : cartItems) {
    Product product = ci.getProduct();
    Integer quantity = ci.getQuantity();

    // Получаем оригинальную цену (без скидки)
    BigDecimal originalPrice = product.getPrice();

    // Рассчитываем скидку для пользователя
    BigDecimal discountPercentage = discountService.getDiscountPercentage(discounts, product);

    // Создаем OrderItem с учетом скидки
    OrderItem oi = OrderItem.createOrderItem(
            product,
            ci.getProductName(),
            ci.getProductSku(),
            quantity,
            originalPrice,
            originalPrice,
            discountPercentage
    );

    // Скидку берём из уже посчитанной позиции — она едина с каноном
    totalDiscount = totalDiscount.add(oi.getDiscountAmount());

    total = total.add(oi.getSubtotal());
    oi.setOrder(order);
    order.addOrderItem(oi);
}

order.setTotalPrice(total);
```

Теперь смотрю со стороны выхода - Order. Отвечаю на три вопроса:

1. Когда выхода не будет вообще?  
Корзина не найдена, анонимная или пустая. Это те же самые три исключения, что и раньше, но теперь я понимаю, что это разбор тривиального случая.
2. Что в голове выхода?  
Пустой Order: номер, пользователь, note, статус, время.
3. Из чего собирается хвост?  
Список позиций заказа: позиция из первой строки корзины (голова) плюс рекурсивный вызов на остатке корзины (хвост).

**_Стало:_**

```java
    // Программа следует за структурой выхода — Order:
    // 1. Когда выход тривиален? Корзина не найдена / анонимная / пустая — заказа нет.
    // 2. Какова голова? Пустой Order без позиций (orderHeaderOf).
    // 3. Из чего хвост? Список позиций, который возвращает orderItemsOf
    //    (голова — orderItemOf позиция, хвост — рекурсия по остатку корзины);
    //    тоталы — по готовым позициям (totalOf/totalDiscountOf).
    @Transactional
    public Order createOrder(Long cartId, String note) {
        // 1: тривиальный случай — заказа не будет
        Cart cart = cartRepository.findByIdWithProducts(cartId)
                .orElseThrow(() -> new IllegalArgumentException("Cart not found"));

        if (cart.getUser() == null) {
            throw new IllegalStateException("Cannot create order from anonymous cart");
        }

        if (cart.getItems().isEmpty()) {
            throw new IllegalStateException("Cart is empty");
        }

        User user = cart.getUser();

        // 2: голова выхода
        Order order = orderHeaderOf(user, note);

        // Скидки пользователя — один запрос на заказ вместо запроса на позицию
        Map<ProductType, BigDecimal> discounts = discountService.getDiscountsForUser(user);

        // 3: позиции множества выстраиваем в список — у рекурсии появится «голова»
        List<OrderItem> items = orderItemsOf(new ArrayList<>(cart.getItems()), 0, discounts);

        // Готовые позиции привязываем к заказу (двусторонняя связь order/OrderItem)
        for (OrderItem item : items) {
            item.setOrder(order);
            order.addOrderItem(item);
        }

        // Тотал — сумма позиций, а не отдельный аккумулятор при обходе корзины
        order.setTotalPrice(totalOf(order.getOrderItems()));
        // ... сохранение, очистка корзины, письмо о заказе
    }

    // Голова выхода: Order без позиций.
    private Order orderHeaderOf(User user, String note) {
        return Order.builder()
                .orderNumber(orderNumberGenerator.next(user.getId()))
                .user(user)
                .note(note)
                .status(OrderStatus.CREATED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .orderItems(new HashSet<>())
                .build();
    }

    // Строим список позиций заказа по списку корзины: позиция с headIndex
    // (голова) плюс всё, что вернет рекурсия на остатке (хвост).
    // Когда позиции кончились, то возвращается пустой список и это база рекурсии.
    private List<OrderItem> orderItemsOf(List<CartItem> lines, int headIndex,
                                         Map<ProductType, BigDecimal> discounts) {
        if (headIndex == lines.size()) {
            return List.of();
        }

        List<OrderItem> tail = orderItemsOf(lines, headIndex + 1, discounts);

        List<OrderItem> result = new ArrayList<>(tail.size() + 1);
        result.add(orderItemOf(lines.get(headIndex), discounts));
        result.addAll(tail);
        return result;
    }

    // Позиция заказа из одной строки корзины, с учётом скидки пользователя.
    private OrderItem orderItemOf(CartItem ci, Map<ProductType, BigDecimal> discounts) {
        Product product = ci.getProduct();

        // Получаем оригинальную цену (без скидки)
        BigDecimal originalPrice = product.getPrice();

        // Рассчитываем скидку для пользователя
        BigDecimal discountPercentage =
                discountService.getDiscountPercentage(discounts, product);

        return OrderItem.createOrderItem(
                product,
                ci.getProductName(),
                ci.getProductSku(),
                ci.getQuantity(),
                originalPrice,
                originalPrice,
                discountPercentage
        );
    }

    // Стоимость заказа — сумма subtotal по позициям.
    private BigDecimal totalOf(Set<OrderItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem item : items) {
            total = total.add(item.getSubtotal());
        }
        return total;
    }

    // Сумма скидок по позициям заказа.
    private BigDecimal totalDiscountOf(Set<OrderItem> items) {
        BigDecimal totalDiscount = BigDecimal.ZERO;
        for (OrderItem item : items) {
            totalDiscount = totalDiscount.add(item.getDiscountAmount());
        }
        return totalDiscount;
    }
```

Что мне тут понравилось. Раньше цена как бы жила в двух местах: в позиции заказа и в аккумуляторе
цикла. Теперь позиции сначала собираются целиком (orderItemsOf возвращает готовый список), а суммы
считаются уже по готовым позициям - аккумуляторы ушли совсем. Второй плюс: orderItemsOf и orderItemOf
ничего снаружи не меняют, просто возвращают результат, такую сборку легко проверить тестом.Ы

___

#### Пример 2 - «Sitemap: документ складывается из своих частей»

Есть контроллер SitemapController, и метод sitemap, он конструирует весь sitemap.xml: строка XML-заголовка, запись главной страницы,
запись на каждую категорию, запись на каждый товар и закрывающий тег. 
Как это было - весь документ собирался в одном методе, два цикла стояли прямо посреди сборки текста.

**_Было:_**

```java
public String sitemap() {
    long start = System.currentTimeMillis();
    int urlCount = 0;

    StringBuilder xml = new StringBuilder(1024 * 1024); // буфер ~1 MB
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

    // 1. Главная
    xml.append(url(baseUrl, "/", "1.0", "daily", Instant.now()));
    urlCount++;

    // Категории: row[0]=title, row[1]=createdAt
    for (Object[] row : categoryService.findAllForSitemap()) {
        String title = (String) row[0];
        Instant createdAt = (Instant) row[1];
        xml.append(url(baseUrl, "/category/" + encode(title), "0.8", "weekly", createdAt));
        urlCount++;
    }

    // Товары: row[0]=title, row[1]=createdAt, row[2]=updatedAt
    for (Object[] row : productService.findAllForSitemap()) {
        // ... то же самое, только priority 0.9
        //     и lastmod = updatedAt с fallback на createdAt
        urlCount++;
    }

    xml.append("</urlset>\n");
    // ...
}
```

Здесь сама структура выхода хорошо видна и её можно записать в одну строчку:  
**_Документ = шапка + запись главной + записи категорий + записи товаров + футер_**

И три вопроса к этой структуре:

1. Когда у секции нет записей?   
Когда выборка из БД пустая - секция не выдаёт ничего.
2. Что такое голова записи `<url>`?  
Константы секции (priority и changefreq) плюс путь из title строки - это собирает метод url(...).
3. Из чего хвост?  
Из следующих строк той же выборки - по одной записи url(...) на каждую строку.

**_Стало:_**

```java
    // Программа следует за структурой выхода — sitemap.xml:
    // 1. Когда запись секции отсутствует? Выборка из БД пуста — секция пуста.
    // 2. Какова голова записи <url>? 
    //    Константы секции (priority/changefreq) и путь из title строки (url(...)).
    // 3. Из чего хвост? Следующие строки той же выборки.
    public String sitemap() {
        long start = System.currentTimeMillis();
        int urlCount = 0;

        StringBuilder xml = new StringBuilder(1024 * 1024); // буфер ~1 MB

        // шапка
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        // 1. Главная
        xml.append(homeUrl());
        urlCount++;

        // 2. Категории
        List<Object[]> categories = categoryService.findAllForSitemap();
        xml.append(categoryUrls(categories));
        urlCount += categories.size();

        // 3. Товары
        List<Object[]> products = productService.findAllForSitemap();
        xml.append(productUrls(products));
        urlCount += products.size();

        // Футер
        xml.append("</urlset>\n");

        long duration = System.currentTimeMillis() - start;
        log.info("Sitemap generated in {} ms, {} URLs, size ~{} KB",
                duration, urlCount, xml.length() / 1024);

        return xml.toString();
    }

    private String homeUrl() {
        return url(baseUrl, "/", "1.0", "daily", Instant.now());
    }

    // Секция категорий: по записи url(...) на строку выборки.
    private CharSequence categoryUrls(List<Object[]> categories) {
        StringBuilder section = new StringBuilder();
        for (Object[] row : categories) {
            String title = (String) row[0];
            Instant createdAt = (Instant) row[1];
            section.append(url(baseUrl, "/category/" + encode(title), "0.8", "weekly", createdAt));
        }
        return section;
    }

    // Секция товаров; lastmod = updatedAt с fallback'ом на createdAt.
    private CharSequence productUrls(List<Object[]> products) {
        StringBuilder section = new StringBuilder();
        for (Object[] row : products) {
            String title = (String) row[0];
            Instant createdAt = (Instant) row[1];
            Instant updatedAt = (Instant) row[2];
            section.append(url(baseUrl, "/product/" + encode(title), "0.9", "weekly",
                    updatedAt != null ? updatedAt : createdAt));
        }
        return section;
    }
```

Три вопроса подсказали, по каким частям разбивать метод: у каждой секции документа свой метод
с понятным именем, и тело sitemap теперь читается как оглавление документа. Касаемо реализации,
внутри секций остались обычные циклы, а не рекурсия. Каталог измеряется десятками тысяч товаров,
такая глубина рекурсии в Java может упереться в стек, поэтому хвост секции я собираю циклом. Но
общую форму результата (шапка + секции + футер) метод повторяет один в один - и в этом была
основная польза ко-подхода для этого места.

___

#### Пример 3 - «сreateOrUpdateProduct: на выходе сразу товар и отчёт»

Есть сервис YmlOfferImporter, в нем метод createOrUpdateProduct при разборе каждого оффера выдаёт сразу **два** результата:
изменённый Product и запись отчёта ProductChangeSummary, где зафиксировано, что поменялось (13 полей «было/стало»). 
Метод тянул это через шесть локальных переменных-булевых: каждая заводилась отдельно, а складывались в одну сводку только в самом конце метода.

**_Было:_**

```java
boolean changed = false;
boolean nameChanged = false;
boolean priceChanged = false;
boolean descriptionChanged = false;

if (!Objects.equals(product.getName(), name)) {
    log.info("  ИМЯ: {} → {}", product.getName(), name);
    product.setName(name);
    changed = true;
    nameChanged = true;
}
if (product.getPrice().compareTo(price) != 0) {
    product.setPrice(price);
    changed = true;
    priceChanged = true;
}
if (!Objects.equals(product.getDescription(), description)) {
    product.setDescription(description);
    changed = true;
    descriptionChanged = true;
}
// То же самое для картинок и атрибутов, 
// и в конце методадлинный билдер ProductChangeSummary из всех этих флагов
```

Смотрю со стороны выхода. На каждое поле товара, которое может поменяться, выходит ровно пара:
сама мутация товара и флаг в отчёте. Значит, в коде эта пара должна жить рядом, в одном месте.
Ответы на вопросы для этого выхода:

1. Когда у поля диффа нет результата?   
   Когда значение из фида совпало с тем, что уже в товаре - тогда не было ни мутации, ни флага.
2. Что в голове?  
   Мутация поля товара плюс выставление его флага - оба в одном if.
3. Из чего хвост?  
   Следующие поля диффа; в конце из готового диффа собирается сводка.

**_Стало:_**

```java
    // Флаги диффа одного товара — из них собирается ProductChangeSummary
    private static class OfferDiff {
        boolean isNew;
        boolean nameChanged;
        boolean priceChanged;
        boolean descriptionChanged;
        boolean imagesChanged;
        boolean attributesChanged;

        boolean anyChanged() {
            return nameChanged || priceChanged || descriptionChanged || imagesChanged || attributesChanged;
        }
    }

    private void createOrUpdateProduct(String sku, String name, String description, BigDecimal price,
                                       Category category, Map<String, String> params,
                                       Map<String, String> paramUnits,
                                       List<String> pictures, ImportContext ctx,
                                       boolean dryRun) {
        // ... подгрузка/создание товара по sku, снимок значений «до» — как было

        OfferDiff diff = new OfferDiff();
        diff.isNew = isNew;

        if (!Objects.equals(product.getName(), name)) {
            log.info("  ИМЯ: {} → {}", product.getName(), name);
            product.setName(name);
            diff.nameChanged = true;
        }

        if (product.getPrice().compareTo(price) != 0) {
            log.info("  ЦЕНА: {} → {}", product.getPrice(), price);
            product.setPrice(price);
            diff.priceChanged = true;
        }

        if (!Objects.equals(product.getDescription(), description)) {
            product.setDescription(description);
            diff.descriptionChanged = true;
        }

        int newImageCount = pictures.size();
        boolean imagesChanged = oldImageCount != newImageCount || !oldImageUrls.equals(new HashSet<>(pictures));
        if (imagesChanged) {
            log.info("  КАРТИНКИ: SKU={} | было={} → стало={}", sku, oldImageCount, newImageCount);
            diff.imagesChanged = true;
        }
        handleImages(product, pictures, ctx);

        diff.attributesChanged = handleAttributes(product, category, params, paramUnits, ctx, dryRun);

        product.setUpdatedAt(Instant.now());
        // ... привязка категории из фида и регистрация товара в ImportContext

        boolean changed = diff.anyChanged();

        // Добавляем в список на сохранение. "Кто изменил" проставляем только
        // когда есть реальные изменения (или товар новый), чтобы idempotent-импорт
        // не затирал автора последних правок при полном совпадении данных фида.
        if (changed || diff.isNew) {
            product.setUpdatedByUserId(ctx.getCurrentUserId());

            // Отчёт собирается из готового диффа
            ctx.getProductsToSave().add(product);
            ctx.getChanges().add(productChangeSummaryOf(sku, product, oldName, oldPrice,
                    oldImageCount, newImageCount, diff));
        }
    }

    // Половина двойного выхода: ProductChangeSummary из готового диффа
    private ProductChangeSummary productChangeSummaryOf(String sku, Product product,
                                                        String oldName, BigDecimal oldPrice,
                                                        int oldImageCount, int newImageCount,
                                                        OfferDiff diff) {
        return ProductChangeSummary.builder()
                .sku(sku)
                .changeType(diff.isNew
                        ? ProductChangeSummary.ChangeType.NEW
                        : ProductChangeSummary.ChangeType.UPDATED)
                .oldName(oldName)
                .newName(product.getName())
                .nameChanged(diff.nameChanged)
                .oldPrice(oldPrice)
                .newPrice(product.getPrice())
                .priceChanged(diff.priceChanged)
                .oldImageCount(oldImageCount)
                .newImageCount(newImageCount)
                .imagesChanged(diff.imagesChanged)
                .descriptionChanged(diff.descriptionChanged)
                .attributesChanged(diff.attributesChanged)
                .build();
    }
```

Главный выигрыш здесь в том, что раньше согласованность флага с самой мутацией держалась только на
моей аккуратности: шесть переменных жили в разных местах метода, и любую из них можно было забыть
синхронизировать. Теперь флаг выставляется ровно там же, где меняется поле товара, а сводка
собирается из уже готового диффа - рассинхронизировать их стало просто невозможно.

___

**_Вывод:_**

Во всех предыдущих заданиях я строил функцию «от входа»: смотрел, какие данные пришли, резал их на
шаги - а результат вырисовывался только к концу. Здесь направление обратное: сначала записываешь,
каким должен быть результат, и прямо из этой записи выпадает скелет программы. Проверил приём на трёх
методах со сложным выходом - и он сработал не для всех трёх, что тоже доказало, 
что использовать такой подход где попало или "везде" не нужно.
Если по результату функции можно пройти три вопроса и получить внятные ответы -
структура программы почти написана, дальше остаётся только аккуратно её записать.
