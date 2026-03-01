### 2. Уровень классов.

#### 2.1. Класс слишком большой (нарушение SRP), или в программе создаётся слишком много его инстансов
Здесь я решил рассмотреть CartService, в котором нет разграничения ответственности и разделения на другие классы.
По идее должны заниматься работой другие классы - с товарами CartItemService, с конвертацией в ДТО и из CartDtoConverter и т.д.
Если такое возникает, то это плохой признак проектирования и нужно разделить логику и разнести по разным классам.
```java
@Service
public class CartService {
    // 1. Работа с корзиной (основное)
    public Cart getOrCreateCart(Long userId) {
        //...//
    }
    public void addProductToUserCart() {
        //...//
    }
    public void removeProductFromUserCart() {
        //...//
    }
    public void clearUserCart(Long userId) {
        //...//
    }
    
    // 2. Работа с товарами в корзине
    public void addProduct(Long cartId, Long productId) {
        //...//
    }
    public void addProductWithQuantity() {
        //...//
    }
    public void decreaseProductInUserCart() {
        //...//
    }
    
    // 3. Получение данных
    public List<CartItemDto> getUserCartItems(Long userId) {
        //...//
    }
    public List<CartItemDto> getCartItems(Long cartId) {
        //...//
    }
    public Cart getCartWithProducts(Long userId) {
        //...//
    }
    
    // 4. Конвертация (DTO)
    private List<CartItemDto> convertCartItemsToDto() {
        //...//
    }
    
    // 5. Бизнес-логика (расчеты)
    public BigDecimal calculateSummary(List<CartItemDto> items) {
        //...//
    }
    
    // 6. Проверки
    public boolean isProductInCart() {  }
    public int getProductQuantityInCart() { 
        //...//
    }
}
```

#### 2.2. Класс слишком маленький или делает слишком мало.
Данный класс создавался, чтобы использовать его для представлений в Thymeleaf и облегчить обновление кол-ва товаров в корзине,
но оказался не нужен.
```java
@ControllerAdvice
public class CartComponent {

    private final CartService cartService;

    public CartComponent(CartService cartService) {
        this.cartService = cartService;
    }

    @ModelAttribute("cartItemCount")
    public int getCartItemCount(@CookieValue(value = "sessionId", required = false)
                                String sessionId) {
        if (sessionId == null) {
            return 0;
        }
        try {
            Cart cart = cartService.getOrCreateCart(null, sessionId);
            return cartService.getCartItems(cart.getId()).size();
        } catch (Exception e) {
            return 0;
        }
    }
}
```

#### 2.3. В классе есть метод, который выглядит более подходящим для другого класса.
Данный класс является обычным ДТО классом, но содержит много методов которые должны рассчитываться например в DiscountService
и CartService где и должна рассчитываться стоимость со скидкой в корзине.
```java
@Data
@RequiredArgsConstructor
public class CartItemDto {
    private Long productId;
    private String productName;
    private String productSku;
    private String productTitle;
    private String productImageUrl;
    private String productImageAlt;
    private BigDecimal unitPrice;
    private BigDecimal unitPriceWithDiscount;
    private Integer quantity;
    private BigDecimal totalPrice;
    private BigDecimal totalPriceWithDiscount;
    private BigDecimal discountAmount;
    private BigDecimal discountPercentage;
    
    public CartItemDto(Long productId, String productName, String productSku,
                       String productTitle, String productImageUrl, String productImageAlt,
                       BigDecimal unitPrice, Integer quantity) {
        this.productId = productId;
        this.productName = productName;
        this.productSku = productSku;
        this.productTitle = productTitle;
        this.productImageUrl = productImageUrl;
        this.productImageAlt = productImageAlt;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        
        this.unitPriceWithDiscount = unitPrice;
        this.totalPrice = calculateTotalPrice();
        this.totalPriceWithDiscount = this.totalPrice;
        this.discountAmount = BigDecimal.ZERO;
        this.discountPercentage = BigDecimal.ZERO;
    }

    private BigDecimal calculateTotalPrice() {
        if (unitPrice != null && quantity != null) {
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
        return BigDecimal.ZERO;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice != null ? totalPrice : calculateTotalPrice();
    }

    public BigDecimal getTotalPriceWithDiscount() {
        if (totalPriceWithDiscount != null) {
            return totalPriceWithDiscount;
        }

        BigDecimal total = getTotalPrice();
        if (discountAmount != null && discountAmount.compareTo(BigDecimal.ZERO) > 0) {
            return total.subtract(discountAmount);
        }
        return total;
    }

    /**
     * Получить цену за единицу товара со скидкой
     * Если поле не заполнено, вычисляет из общей суммы со скидкой
     */
    public BigDecimal getUnitPriceWithDiscount() {
        if (unitPriceWithDiscount != null) {
            return unitPriceWithDiscount;
        }

        // Если есть общая сумма со скидкой и количество, вычисляем
        if (getTotalPriceWithDiscount() != null && quantity != null && quantity > 0) {
            return getTotalPriceWithDiscount()
                    .divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);
        }

        // Если ничего нет, возвращаем обычную цену
        return unitPrice;
    }

    public BigDecimal getDiscountAmount() {
        if (discountAmount != null) {
            return discountAmount;
        }

        if (discountPercentage != null && discountPercentage.compareTo(BigDecimal.ZERO) > 0 &&
                unitPrice != null && quantity != null) {
            return unitPrice
                    .multiply(discountPercentage.divide(BigDecimal.valueOf(100)))
                    .multiply(BigDecimal.valueOf(quantity))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }
}
```

#### 2.4. Класс хранит данные, которые загоняются в него в множестве разных мест в программе.
Обнаружил, что у меня почему-то не реализован общий метод обновления информации о пользователе, вместо этого
информация о нем может меняться в различных местах проекта.
```java
@Entity class User {
    // Поля изменяются в:
    
    // 1. AuthController (регистрация)
    @PostMapping("/register")
    public String register(@Valid UserDto userDto) {
        User user = new User();
        user.setEmail(userDto.getEmail());
        user.setPhone(userDto.getPhone());
    }
    
    // 2. UserController (редактирование профиля)
    @PostMapping("/update")
    public String updateProfile(@Valid UserProfileUpdateDto userDto) {
        User user = getCurrentUser();
        user.setEmail(userDto.getEmail());     // ← и здесь
        user.setPhone(userDto.getPhone());     // ← и здесь
        user.setFirstName(userDto.getFirstName());
        user.setLastName(userDto.getLastName());
    }
    
    // 3. AdminUserController (админка)
    @PostMapping("/admin/users/{id}/update")
    public String updateUser(@PathVariable Long id, ...) {
        User user = userService.findById(id);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setPhone(phone);
        user.setUserType(type);
        user.setNote(note);
        user.setEnabled(enabled);               
    }
    
    // 4. AdminUserController (смена типа)
    @PostMapping("/admin/users/{id}/change-user-type")
    public String changeUserType() {
        User user = userService.findById(id);
        user.setUserType(type);                  // ← и здесь
    }
    
    // 5. AdminUserController (статус)
    @PostMapping("/admin/users/{id}/toggle-status")
    public String toggleUserStatus() {
        User user = userService.findById(id);
        user.setEnabled(!user.isEnabled());      // ← и здесь
    }
}
```

#### 2.5. Класс зависит от деталей реализации других классов.
```java
// Класс сам создает зависимости
@Service
public class DiscountService {
    private ProductRepository productRepository;
    
    public DiscountService() {
        this.productRepository = new ProductRepository(); // Жесткая привязка
    }
}

@Service
@RequiredArgsConstructor
public class DiscountService {
    private final ProductRepository productRepository; // Внедрение через конструктор
}
```

#### 2.6. Приведение типов вниз по иерархии (родительские классы приводятся к дочерним).
Постарался придумать пример по расчету скидки в зависимости от типа скидки, обычно даункастинг лучше никогда не использовать и не
приводить типы родительского класса к дочернему.
```java
public class DiscountCalculator {
    public BigDecimal calculateDiscount(Object discount, User user) {
        if (discount instanceof PercentageDiscount) {
            PercentageDiscount pd = (PercentageDiscount) discount;
            return pd.calculate(user);
        } else if (discount instanceof FixedAmountDiscount) {
            FixedAmountDiscount fd = (FixedAmountDiscount) discount;
            return fd.calculate(user);
        } else if (discount instanceof WholeSaleDiscount) {
            WholeSaleDiscount ws = (WholeSaleDiscount) discount;
            return ws.calculate(user);
        }
        return BigDecimal.ZERO;
    }
}

// Как это лучше реализовать
public interface Discount {
    BigDecimal calculate(User user);
}

public class PercentageDiscount implements Discount {
    @Override
    public BigDecimal calculate(User user) { //...// 
    }
}

public class FixedAmountDiscount implements Discount {
    @Override
    public BigDecimal calculate(User user) { //...//
    }
}

public class DiscountCalculator {
    public BigDecimal calculateDiscount(Discount discount, User user) {
        return discount.calculate(user); // работает для всех типов
    }
}
```

#### 2.7. Когда создаётся класс-наследник для какого-то класса, приходится создавать классы-наследники и для некоторых других классов.
```java
// Иерархии жестко связаны, например захотели добавить новый тип пользователя
// и приходится добавлять новые классы 

class User { 
    //..//
}
class VipUser extends User { 
    //..//
}

class Cart {
    //..//
}
class VipCart extends Cart {
    //..//
}

class Order {
    //..//
}
class VipOrder extends Order {
    //..//
}

class DiscountCalculator {
    //..//
}
class VipDiscountCalculator extends DiscountCalculator { 
    //..//
}

// Лучше например использовать композицию и стратегии
class User {
    private DiscountStrategy discountStrategy;  // стратегия скидки
    private CartType cartType;                  // тип корзины
    //..//
}
```

#### 2.8. Дочерние классы не используют методы и атрибуты родительских классов, или переопределяют родительские методы.
Тут больше классический пример как из учебника, так и встречался в книге Паттернов проектирования.
```java
class Bird {
    void fly() {
        System.out.println("Полетели");
    }
}

class Penguin extends Bird {
    @Override
    void fly() {
        // Пингвины не летают
        // Ничего не делает
    }
}
```
___

### 3. Уровень приложения.

#### 3.1. Одна модификация требует внесения изменений в несколько классов.
Пример требуется изменить номер формирования заказа, но логика размазана по всему проекту.
```java
// Меняем формат номера заказа с "123" на "ORD-2024-123"
// Нужно менять:

// 1. OrderService при создании
order.setNumber("ORD-" + year + "-" + seq);

// 2. OrderController при отображении
model.addAttribute("orderNumber", "ORD-" + order.getNumber());

// 3. Все шаблоны, где отображается номер
<td th:text="${'ORD-' + order.number}">123</td>

// 4. EmailService в письмах
String subject = "Заказ " + "ORD-" + order.getNumber();
```

#### 3.2. Использование сложных паттернов проектирования там, где можно использовать более простой и незамысловатый дизайн. 
```java
// Паттерн "Стратегия" для простой сортировки товаров
public interface SortStrategy {
    List<Product> sort(List<Product> products);
}

public class PriceSortStrategy implements SortStrategy { //...//
}
public class NameSortStrategy implements SortStrategy { //...//
}
public class RatingSortStrategy implements SortStrategy { //...//
}

// Как проще использовать сортировку
public List<Product> sortProducts(List<Product> products, String sortBy) {
    return switch (sortBy) {
        case "price" -> products.stream()
                .sorted(Comparator.comparing(Product::getPrice))
                .toList();
        case "name" -> products.stream()
                .sorted(Comparator.comparing(Product::getName))
                .toList();
        default -> products;
    };
}
```

