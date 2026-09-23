### Про раздутость кода

Как обычно работаю в основном проекте интернет-магазина **ToolsMarket** (18 Вольт).

#### Пример 1 - Расчет скидок:

Просмотрев проект более глубоко, нашел 7 мест от контроллеров до сервисов, где логика расчета скидки дублируется, раздувая код и порождая
возможность нагородить ошибки. Метод `price.multiply(product).divide(100)` используется в 7 файлах. 
Необходимо помнить или даже вспоминать, а корректно ли все описано в каждом из этих мест. И даже переопределяется местами меняя расчет скидки.

**_Было:_**
```java
// DiscountService
BigDecimal discountAmount = price
    .multiply(discountPercentage)
    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
    .multiply(quantity);

// OrderItem.createOrderItem
BigDecimal discountPerUnit = finalOriginalPrice
    .multiply(discountPercentage)
    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
item.discountAmount = discountPerUnit.multiply(BigDecimal.valueOf(quantity));
item.unitPrice = finalOriginalPrice.subtract(discountPerUnit);

// CartItemDto.getDiscountAmount
return unitPrice
    .multiply(discountPercentage.divide(BigDecimal.valueOf(100)))
    .multiply(BigDecimal.valueOf(quantity))
    .setScale(2, RoundingMode.HALF_UP);

// ProductDto.getDiscountedPrice
BigDecimal discountMultiplier = BigDecimal.ONE
    .subtract(discountPercentage.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
return price.multiply(discountMultiplier).setScale(2, RoundingMode.HALF_UP);
```

Сделал отдельный утилитарный класс DiscountMath, который содержит в себе всю логику расчета скидок.  

**_Стало_**:

```java
public final class DiscountMath {
    private DiscountMath() {}

    public static boolean hasDiscount(BigDecimal pct) {
        return pct != null && pct.signum() > 0;
    }

    public static BigDecimal perUnitDiscount(BigDecimal price, BigDecimal pct) {
        return price.multiply(pct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal priceAfterDiscount(BigDecimal price, BigDecimal pct) {
        if (!hasDiscount(pct)) return price;
        return price.subtract(perUnitDiscount(price, pct));
    }

    public static BigDecimal lineDiscount(BigDecimal unitPrice, BigDecimal pct, long qty) {
        if (!hasDiscount(pct)) return BigDecimal.ZERO;
        return perUnitDiscount(unitPrice, pct).multiply(BigDecimal.valueOf(qty));
    }
}
```
___

#### Пример 2 - Правила отмены заказа размазаны по контроллерам:

Может не такой сильный пример, но имеет место.
Отмена заказа также была в 2-х метода контроллеров OrderController и UserController, логика дублируется.
Используется в `OrderController.viewOrder`, `OrderController.cancelOrder`, `UserController.orderDetail`, `UserController.cancelOrder`

**Было**:

```java
// OrderController
private boolean canCancelOrder(Order order) {
    return OrderStateFactory.of(order).cancellable();
}

// UserController
private boolean canCancelOrder(Order order, Long userId) {
    boolean cancellable = OrderStateFactory.of(order).cancellable();
    if (!cancellable) return false;
    if (userId != null) return order.belongsToUser(userId);
    return order.getStatus() == OrderStatus.CREATED;
}
```

Сделал один общий метод для заказа Order.

**Стало**:

```java
// Order
public boolean canBeCancelledBy(Long userId) {
    return switch (status) {
        case CREATED, PROCESSING -> userId != null && userId.equals(this.userId);
        default -> false;
    };
}
```
---

#### Пример 3 - Получение текущего пользователя:

Код получения текущего пользователя нужен в методах для проверки прав в определенных местах и отметки об создании и обновлении товаров.
Он дублируется во многих контролерах местах.
(OrderController, UserController, ProductAdminController, CatalogController, CartRestController, ExcelImportController, XmlParserController).

**Было**:

```java
// 1-й случай UserDetails - копия в 7 местах
Long currentUserId = userService.findByUsername(userDetails.getUsername())
    .map(User::getId)
    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

// 2-й случай Authentication - копия в 2 местах + приватный helper
private Long getCurrentUserId(Authentication authentication) {
    if (authentication != null && authentication.isAuthenticated()) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails) {
            String username = ((UserDetails) principal).getUsername();
            return userService.findByUsername(username)
                .map(User::getId).orElse(null);
        }
    }
    return null;
}

// 3-й случай в CartRestController - это отдельный helper с try-catch
private Long getUserId(UserDetails userDetails) { // ... // }
```
Реализовал единый сервис CurrentUserService, где и находится вся логика получения и проверки текущего пользователя.

**Стало**:

```java
public class CurrentUserService {
    private final UserService userService;

    public Optional<User> findCurrentUser(UserDetails ud) {
        if (ud == null) return Optional.empty();
        return userService.findByUsername(ud.getUsername());
    }

    public Long currentUserId(UserDetails ud) {
        return findCurrentUser(ud).map(User::getId).orElse(null);
    }

    public Long currentUserId(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof UserDetails ud)) return null;
        return currentUserId(ud);
    }
}
```
___

**_Вывод:_**  
Для меня раздутость кода всегда была связана с кучей дублирующихся функций и методов, которые можно централизовать и управлять ими в одном месте,
а не вспоминать каждый раз, где они написаны и могут отличаться по логике от единого правильного метода. Отсюда и следует ключевой принцип устранения раздутости
каждый инвариант определяется ровно один раз. Все остальные места не «соблюдают его на память», а обращаются к единому источнику. 
Это превращает «инвариант, который нужно помнить в N местах» в «инвариант, который определён в 1 месте» — а остальные места просто вызывают его.
Буду чаще обращать на это внимание, всему виной спешка и плохое отношение к проектированию. Сделай побыстрее лишь бы работало, 
но сейчас есть время для рефакторинга и улучшения логики работы кода, правда это отнимет больше времени чем бы я сразу писал чистый и хороший код.

