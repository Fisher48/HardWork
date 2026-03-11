### Правила простого проектного дизайна

#### Избавляться от точек генерации исключений, запрещая соответствующее ошибочное поведение на уровне интерфейса класса

**_Пример 1:
ДО_**
```java
// Контроллер
@PostMapping("/add")
    public String addToCart(@RequestParam Long productId,
                            @RequestParam(defaultValue = "1") Integer quantity,
                            @RequestParam(defaultValue = "cart") String redirectTo,
                            @RequestHeader(value = "Referer", required = false) String referer,
                            RedirectAttributes redirectAttributes) {
        //...
        cartService.addProductToUserCart(user.getId(), productId, quantity);
        // ....
    }
    
// Сервис
/**
 * Добавление товара в корзину пользователя
 */
@Transactional
public void addProductToUserCart(Long userId, Long productId, int quantity) {
   Cart cart = getOrCreateCart(userId);
   Product product = productRepository.findById(productId)
           .orElseThrow(() -> new IllegalArgumentException("Product not found"));

   // Точка генерации исключения
   if (quantity == null || quantity <= 0) {
      throw new IllegalArgumentException("Quantity must be positive");
   }

   Optional<CartItem> existingItem = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId);

   if (existingItem.isPresent()) {
      CartItem item = existingItem.get();
      item.setQuantity(item.getQuantity() + quantity);
      cartItemRepository.save(item);
   } else {
      CartItem item = CartItem.builder()
              .cart(cart)
              .product(product)
              .productName(product.getName())
              .productSku(product.getSku())
              .unitPrice(product.getPrice())
              .quantity(quantity)
              .build();
      cartItemRepository.save(item);
   }
}
```
**_ПОСЛЕ:_**
```java
// Добавил новый класс для определения количества
public class Quantity {
    private final int value;
    
    private Quantity(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        this.value = value;
    }
    
    public static Quantity of(int value) {
        return new Quantity(value);
    }
    
    public static Quantity ONE = new Quantity(1);
    
    public int getValue() {
        return value;
    }
    
    public Quantity increaseBy(Quantity other) {
        return new Quantity(this.value + other.value);
    }
    
    public Quantity decreaseBy(Quantity other) {
        int newValue = this.value - other.value;
        if (newValue <= 0) {
            throw new IllegalArgumentException("Cannot decrease below 1");
        }
        return new Quantity(newValue);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Quantity quantity = (Quantity) o;
        return value == quantity.value;
    }
}

// В контроллере:
@PostMapping("/add")
public String addToCart(@RequestParam Long productId,
                       @RequestParam(defaultValue = "1") Integer quantityValue,
                       // ...
                       ) {
    Quantity quantity = Quantity.of(quantityValue);
    cartService.addProductWithQuantity(cartId, productId, quantity);
    // ...
}

// В сервисе:
@Transactional
public void addProductToUserCart(Long userId, Long productId, Quantity quantity) {
   Cart cart = getOrCreateCart(userId);
   Product product = productRepository.findById(productId)
           .orElseThrow(() -> new IllegalArgumentException("Product not found"));

   Optional<CartItem> existingItem = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId);

   if (existingItem.isPresent()) {
      CartItem item = existingItem.get();
      item.setQuantity(item.getQuantity() + quantity.getValue());
      cartItemRepository.save(item);
   } else {
      CartItem item = CartItem.builder()
              .cart(cart)
              .product(product)
              .productName(product.getName())
              .productSku(product.getSku())
              .unitPrice(product.getPrice())
              .quantity(quantity.getValue())
              .build();
      cartItemRepository.save(item);
   }
}
```
В данном примере в контроллере при добавлении товаров в корзину, количество передавалось простым примитивом Integer и проверялось напрямую в контроллере.
Вспомнив "Быструю прокачку в ООП" я сразу добавил отдельный класс Quantity, в котором инкапсулирована логика проверки кол-ва, 
тем самым запрещая соответствующее ошибочное поведение на уровне интерфейса класса

**_Пример 2:
ДО_**
```java
// В сервисе
@Transactional
public Order updateStatus(Long orderId, OrderStatus newStatus) {
    validateStatusUpdate(orderId, newStatus);
    
    Order order = getOrder(orderId);
    validateStatusTransition(order.getStatus(), newStatus);
    
    order.setStatus(newStatus);
    order.setUpdatedAt(Instant.now());
    
    Order saved = orderRepository.save(order);
    log.debug("Статус заказа обновлен: id={}, номер={}, старый статус={}, новый статус={}", 
            orderId, saved.getOrderNumber(), order.getStatus(), newStatus);
    
    return saved;
}

private boolean isValidTransition(OrderStatus from, OrderStatus to) {
   return switch (from) {
      case CREATED ->
              to == OrderStatus.PROCESSING
                      || to == OrderStatus.PAID
                      || to == OrderStatus.COMPLETED
                      || to == OrderStatus.CANCELLED;

      case PROCESSING ->
              to == OrderStatus.PAID
                      || to == OrderStatus.COMPLETED
                      || to == OrderStatus.CANCELLED;

      case PAID ->
              to == OrderStatus.COMPLETED
                      || to == OrderStatus.CANCELLED;

      default -> false; // COMPLETED, CANCELLED
   };
}

private void validateStatusUpdate(Long orderId, OrderStatus newStatus) {
    if (orderId == null) {
        throw new OrderValidationException("orderId", "ID заказа не может быть null");
    }
    if (newStatus == null) {
        throw new OrderValidationException("status", "Статус не может быть null");
    }
}

private void validateStatusTransition(OrderStatus current, OrderStatus newStatus) {
    // Нельзя менять завершенные или отмененные заказы
    if (current == OrderStatus.COMPLETED || current == OrderStatus.CANCELLED) {
        throw new OrderFinalizedException(current.name());
    }

    // Проверяем корректный переход статуса
    if (!isValidTransition(current, newStatus)) {
        throw new InvalidStatusTransitionException(current.name(), newStatus.name());
    }
}
```

**_ПОСЛЕ_**
```java
// В класс Order
public class Order {
    // ....
    // Добавил методы проверки доступности действий
    public boolean canBeProcessed() {
        return status == OrderStatus.CREATED;
    }
    
    public boolean canBePaid() {
        return status == OrderStatus.CREATED || status == OrderStatus.PROCESSING;
    }
    
    public boolean canBeCompleted() {
        return status == OrderStatus.PAID;
    }
    
    public boolean canBeCancelled() {
        return status != OrderStatus.COMPLETED && status != OrderStatus.CANCELLED;
    }
    // Методы выполнения действий
    public void process() {
        if (!canBeProcessed()) {
            throw new OrderValidationException("Cannot process order in status: " + status);
        }
        this.status = OrderStatus.PROCESSING;
        this.updatedAt = Instant.now();
    }

    public void markAsPaid() {
        if (!canBePaid()) {
            throw new OrderValidationException("Cannot pay for order in status: " + status);
        }
        this.status = OrderStatus.PAID;
        this.updatedAt = Instant.now();
    }

    public void complete() {
        if (!canBeCompleted()) {
            throw new OrderValidationException("Cannot complete order in status: " + status);
        }
        this.status = OrderStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        if (!canBeCancelled()) {
            throw new OrderValidationException("Cannot cancel order in status: " + status);
        }
        this.status = OrderStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }
}

// В сервисе
@Transactional
public Order processOrder(Long orderId) {
    Order order = getOrder(orderId);
    order.process();
    return orderRepository.save(order);
}

@Transactional
public Order markOrderAsPaid(Long orderId) {
    Order order = getOrder(orderId);
    order.markAsPaid();
    return orderRepository.save(order);
}

@Transactional
public Order completeOrder(Long orderId) {
    Order order = getOrder(orderId);
    order.complete();
    return orderRepository.save(order);
}

@Transactional
public Order cancelOrder(Long orderId) {
    Order order = getOrder(orderId);
    order.cancel();
    return orderRepository.save(order);
}
```
Логика обработки статуса заказов предполагает определенную последовательность
и в случае некорректного обновления статуса заказа могло быть исключение при валидации в сервисе.
Теперь вся логика скрыта внутри класса Order, а в сервисе обновление заказа разделено на разные методы.
В дальнейшем это помогло улучшить UI в шаблонах и там теперь отображаются только доступные статусы, тем самым исключив возможность админу выбрать не корректный
(ранее при выборе просто было всплывающее окно об ошибке, с описанием неправильного порядка статуса).
___

#### Отказаться от дефолтных конструкторов без параметров и передавать конструктору обязательные аргументы;

**_Пример 1:
ДО_**
```java
@Entity
@Getter
@Setter
@Table(name = "cart_item")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItem {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @ManyToOne(fetch = FetchType.LAZY)
   @JoinColumn(name = "product_id", nullable = false)
   private Product product;

   private String productName;
   private String productSku;

   @Column(name = "unit_price", precision = 12, scale = 2, nullable = false)
   private BigDecimal unitPrice;

   private Quantity quantity;

   @ManyToOne(fetch = FetchType.LAZY)
   @JoinColumn(name = "cart_id", nullable = false)
   private Cart cart;
}
```

**_ПОСЛЕ_**
```java
@Entity
@Table(name = "cart_item")
@Builder
public class CartItem {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @ManyToOne(fetch = FetchType.LAZY)
   @JoinColumn(name = "product_id", nullable = false)
   private Product product;

   private String productName;
   private String productSku;

   @Column(name = "unit_price", precision = 12, scale = 2, nullable = false)
   private BigDecimal unitPrice;

   private Quantity quantity;

   @ManyToOne(fetch = FetchType.LAZY)
   @JoinColumn(name = "cart_id", nullable = false)
   private Cart cart;

   // Добавил конструктор с обязательными параметрами
   public CartItem(Cart cart, Product product, Quantity quantity) {
       this.cart = Objects.requireNonNull(cart, "Cart must not be null");
       this.product = Objects.requireNonNull(product, "Product must not be null");
       this.productName = product.getName();
       this.productSku = product.getSku();
       this.unitPrice = product.getPrice();
       this.quantity = Objects.requireNonNull(quantity, "Quantity must not be null");
       cart.addItem(this);
   }
}
```
В корзине Cart была аннотация @NoArgsConstructor, из-за чего была вероятность создать товар принадлежащий к корзине бзе самой корзины, чем бы вызвало ошибку.
Убрал данную аннотацию и вставил конструктор с обязательными параметрами, зачастую я вижу что некоторые разработчики исходя из их политики в компании всегда 
прописывают конструктор вручную с обязательными параметрами, это также поможет избежать проблем в будущем.

**_Пример 2:
ДО_**
```java
@Entity
@Getter
@Setter
@Table(name = "\"order\"")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {
   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(name = "order_number", nullable = false, unique = true)
   private Long orderNumber;

   @ManyToOne(fetch = FetchType.LAZY)
   @JoinColumn(name = "user_id", nullable = false)
   private User user;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false)
   private OrderStatus status = OrderStatus.CREATED;

   @Column(name = "total_price", precision = 12, scale = 2, nullable = false)
   private BigDecimal totalPrice;

   @Column(columnDefinition = "text")
   private String note;

   private Instant createdAt = Instant.now();
   private Instant updatedAt = Instant.now();

   @OneToMany(mappedBy = "order", cascade = CascadeType.ALL,
           orphanRemoval = true, fetch = FetchType.LAZY)
   @Builder.Default
   private Set<OrderItem> orderItems = new LinkedHashSet<>();

    // ... методы
}
```

**_ПОСЛЕ_**
```java
@Entity
@Table(name = "\"order\"")
@Builder
public class Order {
   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(name = "order_number", nullable = false, unique = true)
   private Long orderNumber;

   @ManyToOne(fetch = FetchType.LAZY)
   @JoinColumn(name = "user_id", nullable = false)
   private User user;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false)
   private OrderStatus status = OrderStatus.CREATED;

   @Column(name = "total_price", precision = 12, scale = 2, nullable = false)
   private BigDecimal totalPrice;

   @Column(columnDefinition = "text")
   private String note;

   private Instant createdAt = Instant.now();
   private Instant updatedAt = Instant.now();

   @OneToMany(mappedBy = "order", cascade = CascadeType.ALL,
           orphanRemoval = true, fetch = FetchType.LAZY)
   @Builder.Default
   private Set<OrderItem> orderItems = new LinkedHashSet<>();

   // Конструктор с обязательными полями
   public Order(User user, String orderNumber) {
      this.user = Objects.requireNonNull(user, "User must not be null");
      this.orderNumber = Objects.requireNonNull(orderNumber, "Order number must not be null");
      this.status = OrderStatus.CREATED;
      this.createdAt = Instant.now();
      this.totalPrice = BigDecimal.ZERO;
   }

    // ... методы
}
```
Здесь также по аналогии с корзиной в Заказе Order мы исключаем случаи, когда он может быть ошибочно инициализирован без пользователя, или без номера заказа.
Обязуя создавать сущность с обязательными полями
___

#### Избегать увлечения примитивными типами данных (Разрабатывать прикладную систему типов, на смысловом уровне моделирующую предметную область)

**_Пример 1:
ДО_**
```java
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    private String firstName;

    private String lastName;

    @Column(unique = true)
    private String email;

    //....
}
```

**_ПОСЛЕ_**
```java
@Embeddable
public class Email {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    
    @Column(name = "email", nullable = false, unique = true)
    private String value;
    
    public Email(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Email cannot be empty");
        }
        if (!EMAIL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid email format: " + value);
        }
        this.value = value.toLowerCase();
    }
    
    public String getValue() {
        return value;
    }
    
    public String getDomain() {
        return value.substring(value.indexOf('@') + 1);
    }
    
    public String getLocalPart() {
        return value.substring(0, value.indexOf('@'));
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Email email = (Email) o;
        return value.equals(email.value);
    }
    
    @Override
    public int hashCode() {
        return value.hashCode();
    }
    
    @Override
    public String toString() {
        return value;
    }
}

// В User:
@Entity
public class User implements UserDetails {
    // ...
    @Embedded
    private Email email;
    
    public void setEmail(Email email) {
        this.email = email;
    }
    // ...
}

// Использование:
user.setEmail(new Email("fish@example.ru"));
```
Вместо использования примитива строки для почты Email, можно создать тип Email. Он будет моделировать смысловую сущность как отдельную структуру.
Вся логика валидации email находится в этом классе. Хоть и ломбок сам может проверять корректность паттерна почты.

**_Пример 2:
ДО_**
```java
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Номер заказа как число
    @Column(name = "order_number", nullable = false, unique = true)
    private Long orderNumber;

    //....
}

private Long generateOrderNumber(Long userId) {
   LocalDateTime now = LocalDateTime.now();

   // 1. Дата и время (10 цифр): YYMMDDHHmm
   String dateTimePart = DateTimeFormatter.ofPattern("yyMMddHHmm").format(now);

   // 2. ID пользователя (до 4 цифр)
   String userIdPart = String.format("%04d", userId % 10000);

   // 3. Рандом (2 цифры) для уникальности
   String randomPart = String.format("%02d", ThreadLocalRandom.current().nextInt(100));

   // Объединяем
   String numberStr = dateTimePart + userIdPart + randomPart;

   return Long.parseLong(numberStr); // Пример: 2412151830123456
}
```

**_ПОСЛЕ_**
```java
@Embeddable
public class OrderNumber {

   private String value;

   protected OrderNumber() {}

   private OrderNumber(String value) {
      this.value = value;
   }

   public static OrderNumber generate(Long userId) {
      LocalDateTime now = LocalDateTime.now();

      String dateTimePart = DateTimeFormatter.ofPattern("yyMMddHHmm").format(now);
      String userIdPart = String.format("%04d", userId % 10000);
      String randomPart = String.format("%02d", ThreadLocalRandom.current().nextInt(100));

      return new OrderNumber(dateTimePart + userIdPart + randomPart);
   }

   public static OrderNumber of(String value) {
      // валидация формата
      if (value == null || !value.matches("\\d{16}")) {
         throw new IllegalArgumentException("Invalid order number format");
      }
      return new OrderNumber(value);
   }

   public String getValue() {
      return value;
   }

   @Override
   public String toString() {
      return value;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      OrderNumber that = (OrderNumber) o;
      return value.equals(that.value);
   }

   @Override
   public int hashCode() {
      return value.hashCode();
   }
}

// В Order:
@Entity
public class Order { 
    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY) 
    private Long id;
    
    @Embedded 
    private OrderNumber orderNumber;
    
    public Order(User user, OrderNumber orderNumber) {
        this.user = user;
        this.orderNumber = orderNumber;
        // ...
    }
}

// В сервисе:
Order order = new Order(user, OrderNumber.generate(user.getId()));
```
То же самое касается и номера заказа, так как по сути это не просто число, а набор из смысловых конструкций (дата и время, id пользователя, уникальный идентификатор).
В дальнейшем здесь можно изменить порядок генерации номера заказа по потребности

Какой можно подвести итог, исходя из всех 3-х правил проектного дизайна. Соблюдая эти 3 простых правила мы существенно упрощаем логику проекта и кода, 
ограничивая ошибочное поведение класса внутри него, задаем специальные типы, которые помогают разработчику ориентироваться и понимать код лучше, 
не даем создать сущность с пустыми полями, где это критично. Конечно когда ты пишешь код быстро, легко забыть все эти правила и писать как кажется проще (легче)
Но это "проще" позже, может вылиться в дольше и сложнее, когда потребуется какая-нибудь модификация или расширение кода. 
Уже сталкивался, когда захотел изменить поле доступности товара boolean = true/false, на доступность вида = в наличии/под заказ/на складе и пришлось думать,
как лучше сделать, заменить на другой тип поле или добавить новый, хотя он мог частично дублировать логику.
Иногда стоит подумать и остановится для анализа архитектуры проекта. 
Буду придерживаться данных правил, не забывая и про остальные.


