### Как найти смысл данных в вашем проекте - 2

**_Пример 1:_**
```java
@Getter
@Setter
public class OrderItemDto {
    private Long productId;
    private String productName;
    private String productSku;
    private String productTitle; // для URL страницы товара
    private String productImageUrl; // URL изображения товара
    private String productImageAlt; // alt текст изображения
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
    private BigDecimal originalPrice; // Цена без скидки

    // Скидка на момент заказа
    private BigDecimal discountPercentage;
    private BigDecimal discountAmount;
    private boolean hasDiscount;
    
    public static OrderItemDto fromEntity(OrderItem item) {
        OrderItemDto dto = new OrderItemDto();
        dto.setProductId(item.getProduct().getId());
        dto.setProductName(item.getProductName());
        dto.setProductSku(item.getProductSku());
        dto.setQuantity(item.getQuantity());
        dto.setUnitPrice(item.getUnitPrice());
        dto.setSubtotal(item.getSubtotal());

        // Получаем данные из связанного Product
        Product product = item.getProduct();
        dto.setProductTitle(product.getTitle() != null ?
                product.getTitle() : product.getName());

        // Получаем изображение
        if (!product.getImages().isEmpty()) {
            ProductImage mainImage = product.getImages().stream().findFirst().orElse(null);
            dto.setProductImageUrl(mainImage.getUrl());
            dto.setProductImageAlt(mainImage.getAlt() != null ?
                    mainImage.getAlt() : product.getName());
        }

        // Используем только сохраненные данные из БД
        if (item.getOriginalUnitPrice() != null) {
            dto.setOriginalPrice(item.getOriginalUnitPrice());
            dto.setHasDiscount(item.isHasDiscount());

            if (item.isHasDiscount() && item.getDiscountAmount() != null) {
                dto.setDiscountAmount(item.getDiscountAmount());
                dto.setDiscountPercentage(item.getDiscountPercentage());
            } else {
                // Если нет скидки
                dto.setOriginalPrice(item.getUnitPrice());
                dto.setHasDiscount(false);
            }
        } else {
            // Защита на случай если миграция не сработала
            dto.setOriginalPrice(item.getUnitPrice());
            dto.setHasDiscount(false);
        }

        return dto;
    }
    // ... //
}
```

```java
public record OrderItemDto(
        Long productId,
        String productName,
        String productSku,
        String productTitle,
        String productImageUrl,
        String productImageAlt,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal,
        BigDecimal originalPrice,
        BigDecimal discountPercentage,
        BigDecimal discountAmount,
        boolean hasDiscount
) {
    
    public static OrderItemDto fromEntity(OrderItem item) {
        Product product = item.getProduct();
        ProductImage mainImage = product.getImages().stream().findFirst().orElse(null);
        
        BigDecimal originalPrice = item.getOriginalUnitPrice() != null
                ? item.getOriginalUnitPrice()
                : item.getUnitPrice();

        boolean hasDiscount = item.isHasDiscount() && item.getDiscountAmount() != null;

        return new OrderItemDto(
                product.getId(),
                item.getProductName(),
                item.getProductSku(),
                product.getTitle() != null ? product.getTitle() : product.getName(),
                mainImage != null ? mainImage.getUrl() : null,
                mainImage != null && mainImage.getAlt() != null ? mainImage.getAlt() : product.getName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getSubtotal(),
                originalPrice,
                hasDiscount ? item.getDiscountPercentage() : null,
                hasDiscount ? item.getDiscountAmount() : null,
                hasDiscount
        );
    }
    // ... //
}
```

Есть класс DTO показывающий предмет из заказа, он мутабелен и его можно случайно изменить и у клиента в заказе будут побочные эффекты от таких манипуляций. 
Нет гарантии, что код может быть изменен и в процессе передачи между слоями (сервис -> контролер -> представление).
А так мы преобразуем обычный класс в record и делаем его иммутабельным при создании. Пример может не сильно отражает логику, но суть понятна.

___

**_Пример 2:_**
```java
@Entity
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

    // Методы выполнения действий
    public void process() {
        if (!canBeProcessed()) {
            throw new IllegalStateException("Cannot process order in status: " + status);
        }
        this.status = OrderStatus.PROCESSING;
        this.updatedAt = Instant.now();
    }

    public void markAsPaid() {
        if (!canBePaid()) {
            throw new IllegalStateException("Cannot pay for order in status: " + status);
        }
        this.status = OrderStatus.PAID;
        this.updatedAt = Instant.now();
    }

    public void complete() {
        if (!canBeCompleted()) {
            throw new IllegalStateException("Cannot complete order in status: " + status);
        }
        this.status = OrderStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        if (!canBeCancelled()) {
            throw new IllegalStateException("Cannot cancel order in status: " + status);
        }
        this.status = OrderStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }
}
```

```java
// 1. Иммутабельная запись об изменении статуса
public record StatusChange(
                OrderStatus status,
                String changedBy,
                Instant changedAt,
                String comment 
        ) {}

// 2. Заказ хранит список изменений
@Entity
public class Order {
    
    
    //...//
    
    @Id
    private Long id;

    @ElementCollection
    @CollectionTable(name = "order_status_history")
    @OrderBy("changedAt ASC")
    private List<StatusChange> statusHistory = new ArrayList<>();

    // Текущий статус вычисляется из истории
    public OrderStatus getCurrentStatus() {
        if (statusHistory.isEmpty()) return OrderStatus.CREATED;
        return statusHistory.get(statusHistory.size() - 1).status();
    }

    // Добавление нового статуса
    public void addStatusChange(OrderStatus newStatus, String changedBy, String comment) {
        OrderStatus current = getCurrentStatus();

        // Проверка допустимости перехода
        if (!isValidTransition(current, newStatus)) {
            throw new InvalidStatusTransitionException(current.name(), newStatus.name());
        }

        statusHistory.add(new StatusChange(newStatus, changedBy, Instant.now(), comment));
    }

    // Вспомогательные методы
    public Optional<StatusChange> getStatusChange(OrderStatus targetStatus) {
        return statusHistory.stream()
                .filter(change -> change.status() == targetStatus)
                .findFirst();
    }

    public Duration getTimeInStatus(OrderStatus from, OrderStatus to) {
        StatusChange fromChange = getStatusChange(from).orElseThrow();
        StatusChange toChange = getStatusChange(to).orElseThrow();
        return Duration.between(fromChange.changedAt(), toChange.changedAt());
    }

    public void process(String changedBy, String comment) {
        addStatusChange(OrderStatus.PROCESSING, changedBy, comment);
    }
    
    // ... //
}

// 3. И уже можно вот так..
// Создание заказа
Order order = new Order();
order.addStatusChange(OrderStatus.CREATED, "system", "Заказ создан");

// Оплата
order.addStatusChange(OrderStatus.PAID, "payment_system", "Оплата картой ****1234");

// Обработка
order.addStatusChange(OrderStatus.PROCESSING, "admin@mail.com", "Передан в доставку");

// Доставка
order.addStatusChange(OrderStatus.COMPLETED, "system", "Доставлен клиенту");

// Теперь можно показать клиенту историю заказа
for (StatusChange change : order.getStatusHistory()) {
    System.out.println(change.changedAt() + ": " + change.status() + " (" + change.comment() + ")");
}

```
Не совсем корректный пример, но давно хотел и вот думаю тут как раз добавить историю статусов заказа, т.к сейчас не возможно отследить когда и что было, кто поменял статус.
После добавления StatusChange иммутабельной сущности можно строить и отчеты и видеть всю историю со статусами заказа. Код стал понятнее, статус заказа не размазан по коду.
Хоть это не изменения работы по ссылке, но теперь каждый раз мы не меняем сам заказ, когда требуется обновить статус, а уже работаем с отдельной сущностью.
Мы добавляем запись в список statusHistory. Сам заказ остается тем же объектом, просто у него появляется новая запись в истории.
___

**_Пример 3:_**
```java
public class User {
    private Long id;
    private String name;
    private String address;
    
    public User(Long id, String name, String address) {
        this.id = id;
        this.name = name;
        this.address = address;
    }
    
    public void updateAddress(String newAddress) {
        this.address = newAddress;
    }
}

// Использование
User user = new User(1L, "Денис", "ул. Задорожная, 16");
user.updateAddress("ул. Острякова, 15");
```

```java
// Иммутабельный пользователь
public record User(Long id, String name, String address) {}

// Запись об изменении (иммутабельная)
public record UserChangeLog(
    Long userId,
    String oldAddress,
    String newAddress,
    Instant timestamp
) {}

// Сервис с историей
public class UserHistoryService {
    private final List<UserChangeLog> history = new ArrayList<>();
    
    public User updateAddress(User user, String newAddress) {
        history.add(new UserChangeLog(
            user.id(),
            user.address(),
            newAddress,
            Instant.now()
        ));
        return new User(user.id(), user.name(), newAddress);
    }
    
    public List<UserChangeLog> getHistory(Long userId) {
        return history.stream()
            .filter(log -> log.userId().equals(userId))
            .toList();
    }
}

// Использование
UserHistoryService service = new UserHistoryService();
User user = new User(1L, "Иван", "ул. 50 лет НЛМК, 1");
user = service.updateAddress(user, "ул. Пушкина, 12");
user = service.updateAddress(user, "ул. Гоголя, 13");

service.getHistory(1L).forEach(log -> {
    System.out.println(log.timestamp() + ": " + 
                       log.oldAddress() + " → " + log.newAddress());
});
```
Учебный пример, при каждом изменении пользователь перезаписан, если мы хотим поддерживать историю о пользователе и хранить адреса пользователя, 
сделаем все иммутабельно в record и добавим некий лог истории, чтобы отслеживать адрес пользователя при каждом обновлении, 
нет необходимости заменять объект каждый, раз создается новый.  

В целом я еще много раз перечитывал материал, очень сложная тема и не легко в своем учебном проекте найти места, 
где лучше сделать вместо передачи сущностей по ссылке удобнее использовать иммутабельные состояния. Просто так ставить везде final или менять дто на record 
это как раз бы и выглядело как механически заменить передачу объектов по ссылке в передачу их копий. Постарался показать как я это все понял.
Понятно что мутабельность тоже нужна в некотором смысле объекты необходимо изменять, после изучения курса по Функциональному проектированию, хотелось бы полностью 
перейти на иммутабельность - каждый раз новый объект, это конечно помогает избежать много проблем (гонки состояний, ошибки в тестах, скрытые изменения в методах и т.д.).
Здесь нужна практика и долго и усердно проектировать заранее всё снижая зависимости, чтобы потом мои изменения ощутимо помогали в упрощении кода и уменьшении зависимостей.