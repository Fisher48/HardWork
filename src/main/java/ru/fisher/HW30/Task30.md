### Про раздутость кода (2) — Неименованные кусочки, которые избыточно оформлять функциями

Как и в прошлый раз работаю в основном проекте интернет-магазина **ToolsMarket** (18 Вольт).

В этом задании вторая половина раздутости — код, который не дублируется, но при этом читается
как «просто несколько строк», хотя каждый такой кусок отвечает за что-то одно и конкретное. Если его
не назвать или описать, через какое-то время его никто не поймёт (хоть я и один в проекте), зачем он там и что сломается, если его убрать.
А выносить такие куски в отдельные методы — ошибка: они не переиспользуются, у них нет собственного
контракта ошибок, и метод просто добавит «функцию на один вызов», которых в проекте и так хватает.

---

#### Пример 1 — «Снимок корзины по актуальной цене»

`CartService.convertCartItemsToDto`, внутри lambda'ы конвертации:

```java
// «Снимок корзины по актуальной цене» — единый канонический расчёт:
// цена из каталога (снимок cart_item.unitPrice разъезжался с итогами заказа),
// скидка округляется один раз на единицу, поэтому корзина не расходится с OrderItem.
BigDecimal unitPrice = product.getPrice();
BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
BigDecimal discountAmount = DiscountMath.lineDiscount(unitPrice, discountPercentage, item.getQuantity());
```

---

#### Пример 2 — «Карта количества товаров в корзине»

`CatalogController.search`, сразу после получения `searchResults`:

```java
// «Карта количества товаров в корзине» — best-effort lookup для каталога:
// заполняется только у авторизованных, при ошибке остаётся пустой, каталог не падает
Map<Long, Integer> cartProductQuantities = new HashMap<>();

if (user != null) {
    try {
        Cart cart = cartService.getOrCreateCart(user.getId());
        List<CartItemDto> cartItems = cartService.getCartItems(cart.getId());

        for (CartItemDto cartItem : cartItems) {
            if (cartItem.getProductId() != null) {
                cartProductQuantities.put(cartItem.getProductId(), cartItem.getQuantity());
            }
        }
    } catch (Exception e) {
        log.warn("Ошибка при проверке корзины: {}", e.getMessage());
    }
}
```

---

#### Пример 3 — «Обогащение карточек состоянием корзины»

`CatalogController.search`:

```java
// «Обогащение карточек состоянием корзины» — пересборка содержимого страницы
// (inCart/cartQuantity) поверх исходной пагинации, чтобы сохранить страницы и total
List<ProductListDto> productsWithCartInfo = searchResults.getContent().stream()
        .map(product -> {
            ProductListDto enhancedProduct = new ProductListDto();
            BeanUtils.copyProperties(product, enhancedProduct);

            Integer cartQuantity = cartProductQuantities.get(product.getId());
            if (cartQuantity != null && cartQuantity > 0) {
                enhancedProduct.setInCart(true);
                enhancedProduct.setCartQuantity(cartQuantity);
            } else {
                enhancedProduct.setInCart(false);
                enhancedProduct.setCartQuantity(0);
            }

            return enhancedProduct;
        })
        .toList();

Page<ProductListDto> enhancedProducts = new PageImpl<>(
        productsWithCartInfo,
        searchResults.getPageable(),
        searchResults.getTotalElements()
);
```

---

#### Пример 4 — «Итоги корзины для REST-ответа»

`CartRestController.buildResponse`:

```java
// «Итоги корзины для REST-ответа» — суммы по каждой позиции
// со страховкой от NPE: цена → 0, цена со скидкой → полная цена, количество → 0
BigDecimal totalAmount = BigDecimal.ZERO;
BigDecimal totalDiscount = BigDecimal.ZERO;
BigDecimal totalWithDiscount = BigDecimal.ZERO;

for (CartItemDto item : items) {
    // Получаем значения с проверкой на null
    BigDecimal unitPrice = item.getUnitPrice() != null ?
            item.getUnitPrice() : BigDecimal.ZERO;
    BigDecimal unitPriceWithDiscount = item.getUnitPriceWithDiscount() != null ?
            item.getUnitPriceWithDiscount() : unitPrice;
    Integer quantity = item.getQuantity() != null ? item.getQuantity() : 0;

    // Рассчитываем суммы для этого товара
    BigDecimal itemTotalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));
    BigDecimal itemTotalPriceWithDiscount = unitPriceWithDiscount.multiply(BigDecimal.valueOf(quantity));
    BigDecimal itemDiscount = itemTotalPrice.subtract(itemTotalPriceWithDiscount);

    // Добавляем к общим суммам
    totalAmount = totalAmount.add(itemTotalPrice);
    totalWithDiscount = totalWithDiscount.add(itemTotalPriceWithDiscount);
    totalDiscount = totalDiscount.add(itemDiscount);
}
```

---

#### Пример 5 — «Подготовка формы редактирования товара»

`ProductAdminController.edit`:

```java
// «Подготовка формы редактирования товара» — две lookup-структуры:
// preselect категорий (множество id) и текущие значения характеристик (id характеристики → значение)
Set<Long> productCategoryIds = product.getCategories().stream()
        .map(Category::getId)
        .collect(Collectors.toSet());

Map<Long, String> currentValues = product.getAttributeValues().stream()
        .collect(Collectors.toMap(
                av -> av.getAttribute().getId(),
                ProductAttributeValue::getValue)
        );
```

---

**_Вывод:_**

Раздутость, оказывается, бывает двух видов, и лечатся они противоположно.  
Первый — **дублирование** его видно, его ловит ревью по чек-листу «не копипасть», лечится выносом в общий класс
(`DiscountMath`, `CurrentUserService`, `SortParser`).  
Второй — **безымянные куски** код не повторяется,
метрики его не видят, но он несёт инвариант (деградация корзины, сохранение пагинации, источник цены),
и при этом не переиспользуется нигде. Такие куски как раз и порождают мусор: их либо не замечают, либо
спешат «оформить в метод», и появляется функция на один вызов с тремя параметрами, которая ничего не
даёт.

Отсюда правило, которое я запомнил, если есть кусок кода, который живёт в одном месте и не имеет собственного контракта
ошибок и не переиспользуется — его можно не выносить в метод, а называть, документировать в том месте где он используется. 
Метод — это что-то из разряда «кто-то может вызвать его повторно», а комментарий к коду — это описание «этот кусок значит вот это или то»;
использовать второе вместо первого нужно ровно там, где повторного вызова не будет.


