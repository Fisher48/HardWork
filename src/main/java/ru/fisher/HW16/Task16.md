### Неочевидные проектные ошибки (2)

Задание было не простое и проектов где можно рефакторить у меня не много, но я постарался в своём пет-проекте поискать, что можно улучшить и отрефакторить.
Пока у меня нет какого-то рабочего кода, где я бы мог поискать что-то, глаз замылен на пет-проекте и кажется, что всё работает и лучше ничего не трогать). 
Что и говорит о том, что писал я код уже неверно, ведь он боится изменений...

**_Пример 1_**

[**_Ссылка на коммит_**](https://github.com/Fisher48/ToolsMarket/commit/4c61ba053d90a156e88d9ddded954c22483f1ac1)

В первом примере я решил показать полностью коммит, который посвящен глобальному изменению. Мне кажется его можно отнести к полноценному рефакторингу.
В данном случае изначально в коде была возможность добавлять товары в корзину для не зарегистрированных пользователей, была анонимная корзина, которая
затем мержилась при создании заказа, если пользователь собирался авторизоваться. Естественно в коде приходилось держать информацию о сессии и строить логику для 2-х
разных корзин. В целом, я думаю так во многих интернет магазинах. Но в моем случае, заказ только для зарегистрированных пользователей и, чтобы добавлять товар в корзину
нужно, чтобы человек был зарегистрирован в приложении. Я решил полностью отказаться от анонимной корзины и проверки сессии, в пользу только авторизованных пользователей.
Тем самым упростив логику и весь код в проекте. Убрано лишнее состояние анонимной корзины и дополнительные проверки.
___

**_Пример 2_**  
**_БЫЛО_**
```java
public class OrderService {
    @Transactional
    public Order createOrder(Long cartId) {
        Cart cart = cartRepository.findByIdWithProducts(cartId)
                .orElseThrow(() -> new IllegalArgumentException("Cart not found"));

        if (cart.getUser() == null) {
            throw new IllegalStateException("Cannot create order from anonymous cart");
        }

        Set<CartItem> cartItems = cart.getItems();
        if (cartItems.isEmpty()) {
            throw new IllegalStateException("Cart is empty");
        }

        User user = cart.getUser();
        Order order = Order.builder()
                .orderNumber(generateOrderNumber(user.getId()))
                .user(user)
                .status(OrderStatus.CREATED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .orderItems(new HashSet<>())
                .build();

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;

        for (CartItem ci : cartItems) {
            Product product = ci.getProduct();
            Integer quantity = ci.getQuantity();

            // Получаем оригинальную цену (без скидки)
            BigDecimal originalPrice = product.getPrice();

            // Рассчитываем скидку для пользователя
            BigDecimal discountPercentage = discountService.calculateDiscount(user, product);

            // Создаем OrderItem с учетом скидки
            OrderItem oi = OrderItem.createOrderItem(
                    product,
                    ci.getProductName(),
                    ci.getProductSku(),
                    quantity,
                    originalPrice,           // Исходная цена
                    originalPrice,           // originalUnitPrice (та же цена без скидки)
                    discountPercentage       // Процент скидки
            );

            // Если есть скидка, пересчитываем
            if (discountPercentage.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal discountPerUnit = originalPrice
                        .multiply(discountPercentage)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                BigDecimal itemDiscount = discountPerUnit.multiply(BigDecimal.valueOf(quantity));
                totalDiscount = totalDiscount.add(itemDiscount);
            }

            total = total.add(oi.getSubtotal());
            oi.setOrder(order);
            order.addOrderItem(oi);
        }

        order.setTotalPrice(total);

        Order saved = orderRepository.save(order);

        cart.clear();
        cartRepository.save(cart);

        log.info("Заказ создан: id={}, номер={}, цена={}, скидка={}",
                order.getId(), order.getOrderNumber(), order.getTotalPrice(), totalDiscount);

        eventPublisher.publishEvent(new OrderCreatedEvent(
                order.getId(),
                order.getOrderNumber(),
                order.getOrderItems().stream()
                        .map(OrderItemDto::fromEntity).toList(),
                order.getTotalPrice(),
                order.getUser().getEmail()
        ));

        return saved;
    }
}
```

**_СТАЛО_**
```java
// Фабрика для создания OrderItem
@Component
public class OrderItemFactory {
    
    public OrderItem createFromCartItem(CartItem cartItem, User user) {
        Product product = cartItem.getProduct();
        BigDecimal discount = calculateDiscountForUser(user, product);
        
        return OrderItem.builder()
            .product(product)
            .productName(cartItem.getProductName())
            .productSku(cartItem.getProductSku())
            .quantity(cartItem.getQuantity())
            .originalUnitPrice(product.getPrice())
            .discountPercentage(discount)
            .build();
    }
    
    private BigDecimal calculateDiscountForUser(User user, Product product) {
        // логика расчета скидки
    }
}

// Фабрика для создания заказа
@Component
public class OrderFactory {
    
    private final OrderItemFactory orderItemFactory;
    private final OrderPricing orderPricing;
    
    public Order createFromCart(Cart cart) {
        User user = cart.getUser();
        
        Order order = Order.builder()
            .orderNumber(generateOrderNumber(user.getId()))
            .user(user)
            .status(OrderStatus.CREATED)
            .orderItems(new HashSet<>())
            .build();
        
        for (CartItem cartItem : cart.getItems()) {
            OrderItem item = orderItemFactory.createFromCartItem(cartItem, user);
            order.addOrderItem(item);
        }

        OrderPricing.OrderTotal totals = orderPricing.calculateTotal(order.getOrderItems());
        order.setTotalPrice(totals.total());
        
        return order;
    }

    // ... //
}

// OrderPricing - отвечает только за расчеты
@Component
public class OrderPricing {

    /**
     * Рассчитать итоговую сумму заказа
     */
    public OrderTotal calculateTotal(Set<OrderItem> items) {
        BigDecimal subtotal = items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDiscount = items.stream()
                .filter(OrderItem::isHasDiscount)
                .map(OrderItem::getDiscountAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal total = subtotal.subtract(totalDiscount);

        return new OrderTotal(subtotal, totalDiscount, total);
    }

    /**
     * Результат расчета - неизменяемый объект
     */
    public record OrderTotal(
            BigDecimal subtotal,      // сумма без скидок
            BigDecimal totalDiscount, // общая скидка
            BigDecimal total          // итоговая сумма
    ) {}
}

@Service
@AllArgsConstructor
public class OrderService {
    
    private final OrderFactory orderFactory;
    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;
    
    @Transactional
    public Order createOrder(Long cartId) {
        Cart cart = cartRepository.findByIdWithProducts(cartId)
            .orElseThrow(() -> new IllegalArgumentException("Cart not found"));
        
        Order order = orderFactory.createFromCart(cart);
        Order saved = orderRepository.save(order);
        
        cart.clear();
        cartRepository.save(cart);
        
        eventPublisher.publishEvent(new OrderCreatedEvent(saved));
        
        log.info("Заказ создан: id={}, номер={}", saved.getId(), saved.getOrderNumber());
        return saved;
    }

    // ... //
}
```
Есть сервис OrderService, в котором много логики по созданию и формированию заказов, но если присмотреться, то в основном методе создания заказа смешано практически 
всё в кучу: получение данных из корзины, расчет скидок, создание OrderItem, расчет итогов, сохранение и отправка события (уведомление на почту).
После рефакторинга я разделил границы и добавил фабрику заказов, фабрику товаров для заказа и сервис по формированию цены. 
Стало - расчет цен в OrderPricing, создание OrderItem в OrderItemFactory, создание заказа в OrderFactory. 
И сам OrderService стал чище и проще тестировать каждую логику по отдельности.

Реализовал тест по созданию заказа, они проверяют как работает скидка при создании заказа в зависимости от конкретного типа пользователя и типа товара.
```java
@SpringBootTest
@Transactional
class CreateOrderUseCaseTest {
    
    @Autowired
    private OrderService orderService;
    
    @Autowired
    private CartService cartService;
    
    @Autowired
    private ProductRepository productRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Test
    @DisplayName("VIP пользователь получает скидку 10% на ручной инструмент при оформлении заказа")
    void vipUserGets10PercentDiscountOnElectronics() {
        // VIP пользователь с товаром в корзине
        User vipUser = createUser(UserType.VIP);
        Product screwdriver = createProduct(ProductType.HAND_TOOL, new BigDecimal("50000"));
        addToCart(vipUser, screwdriver, 1);
        
        // оформляет заказ
        Order order = orderService.createOrderFromUserCart(vipUser.getId());
        
        // цена должна быть со скидкой 10%
        BigDecimal expectedTotal = new BigDecimal("45000");
        assertThat(order.getTotalPrice()).isEqualByComparingTo(expectedTotal);
        
        // в OrderItem сохранена информация о скидке
        OrderItem item = order.getOrderItems().iterator().next();
        assertThat(item.getDiscountPercentage()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(item.getOriginalUnitPrice()).isEqualByComparingTo(new BigDecimal("50000"));
        assertThat(item.getUnitPrice()).isEqualByComparingTo(new BigDecimal("45000"));
        assertThat(item.isHasDiscount()).isTrue();
    }
    
    @Test
    @DisplayName("Обычный пользователь не получает скидку на ручной инструмент")
    void regularUserGetsNoDiscount() {
        // обычный пользователь
        User regularUser = createUser(UserType.REGULAR);
        Product screwdriver = createProduct(ProductType.HAND_TOOL, new BigDecimal("50000"));
        addToCart(regularUser, screwdriver, 1);
        
        Order order = orderService.createOrderFromUserCart(regularUser.getId());
        
        // цена без скидки
        assertThat(order.getTotalPrice()).isEqualByComparingTo(new BigDecimal("50000"));
        
        OrderItem item = order.getOrderItems().iterator().next();
        assertThat(item.getDiscountPercentage()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(item.isHasDiscount()).isFalse();
    }
    
    @Test
    @DisplayName("Скидка применяется только к подходящим товарам")
    void discountAppliedOnlyToEligibleProducts() {
        // VIP пользователь с разными товарами
        User vipUser = createUser(UserType.VIP);
        Product screwdriver = createProduct(ProductType.HAND_TOOL, new BigDecimal("50000"));
        Product sawchain = createProduct(ProductType.OTHER, new BigDecimal("10000"));
        addToCart(vipUser, screwdriver, 1);
        addToCart(vipUser, sawchain, 3);
        
        Order order = orderService.createOrderFromUserCart(vipUser.getId());
        assertThat(order.getTotalPrice()).isEqualByComparingTo(new BigDecimal("48000"));
        
        // Проверяем каждый товар
        for (OrderItem item : order.getOrderItems()) {
            if (item.getProduct().getProductType() == ProductType.HAND_TOOL) {
                assertThat(item.getDiscountPercentage()).isEqualByComparingTo(new BigDecimal("10"));
            } else {
                assertThat(item.getDiscountPercentage()).isEqualByComparingTo(BigDecimal.ZERO);
            }
        }
    }
    
    @Test
    @DisplayName("Нельзя создать заказ из пустой корзины")
    void cannotCreateOrderFromEmptyCart() {
        // пользователь с пустой корзиной
        User user = createUser(UserType.REGULAR);
        
        assertThatThrownBy(() -> orderService.createOrderFromUserCart(user.getId()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cart is empty");
    }
    
    private User createUser(UserType type) {
        User user = User.builder()
            .username("test_" + System.currentTimeMillis())
            .email("test_" + System.currentTimeMillis() + "@test.com")
            .userType(type)
            .build();
        return userRepository.save(user);
    }
    
    private Product createProduct(ProductType type, BigDecimal price) {
        Product product = Product.builder()
            .name("Test Product")
            .sku("SKU_" + System.currentTimeMillis())
            .productType(type)
            .price(price)
            .active(true)
            .build();
        return productRepository.save(product);
    }
    
    private void addToCart(User user, Product product, int quantity) {
        cartService.addToCart(user.getId(), product.getId(), quantity);
    }
}
```

___

**_Пример 3_**  
**_БЫЛО_**
```java
@Service
@Slf4j
@RequiredArgsConstructor
public class PriceImportService {

    private final PriceExcelParser excelParser;
    private final ProductRepository productRepository;

    @Transactional
    public ImportResult importPrices(InputStream is, String filename) throws IOException {
        List<PriceRow> rows = excelParser.parse(is);

        List<PriceChange> changes = new ArrayList<>();
        int samePrice = 0;
        List<String> notFound = new ArrayList<>();

        // Запрос к БД в цикле для каждой строки
        for (PriceRow row : rows) {
            Optional<Product> productOpt = productRepository.findBySku(row.sku());

            if (productOpt.isPresent()) {
                Product product = productOpt.get();

                if (product.getPrice().compareTo(row.price()) != 0) {
                    changes.add(new PriceChange(
                            row.sku(),
                            product.getTitle(),
                            product.getPrice(),
                            row.price()
                    ));

                    // Сохранение в БД в цикле для каждого изменения
                    product.setPrice(row.price());
                    product.setUpdatedAt(Instant.now());
                    productRepository.save(product);

                    log.info("Updated price for {} ({}): {} -> {}",
                            product.getTitle(), row.sku(),
                            product.getPrice(), row.price());
                } else {
                    samePrice++;
                }
            } else {
                notFound.add(row.sku());
                log.warn("Product with SKU {} not found", row.sku());
            }
        }

        return new ImportResult(
                changes.size(),
                samePrice,
                notFound.size(),
                notFound,
                changes,
                filename,
                false
        );
    }
}
```

**_СТАЛО_**
```java
// Репозиторий с пакетным запросом
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    // Для пакетного запроса
    @Query("SELECT p FROM Product p WHERE p.sku IN :skus")
    List<Product> findAllBySkuIn(@Param("skus") List<String> skus);
}

// Сервис с пакетной обработкой
@Service
@RequiredArgsConstructor
@Slf4j
public class PriceImportService {

    private final PriceExcelParser parser;
    private final ProductRepository productRepository;

    @Transactional
    public ImportResult importPrices(InputStream is, String filename) throws IOException {
        // 1. Парсим Excel
        List<PriceRow> rows = parser.parse(is);
        
        if (rows.isEmpty()) {
            log.warn("No rows to import from {}", filename);
            return ImportResult.empty(filename);
        }

        // 2. Один запрос к БД для всех SKU
        List<String> skus = rows.stream()
                .map(PriceRow::sku)
                .toList();
        
        Map<String, Product> productMap = productRepository
                .findAllBySkuIn(skus)
                .stream()
                .collect(Collectors.toMap(Product::getSku, p -> p));

        // 3. Обрабатываем в памяти (без запросов к БД)
        List<Product> toUpdate = new ArrayList<>();
        List<PriceChange> changes = new ArrayList<>();
        List<String> notFound = new ArrayList<>();

        for (PriceRow row : rows) {
            Product product = productMap.get(row.sku());

            if (product == null) {
                notFound.add(row.sku());
                log.warn("Product with SKU {} not found", row.sku());
                continue;
            }

            // Только если цена изменилась
            if (product.getPrice().compareTo(row.price()) != 0) {
                changes.add(new PriceChange(
                        row.sku(),
                        product.getTitle(),
                        product.getPrice(),
                        row.price()
                ));

                product.setPrice(row.price());
                product.setUpdatedAt(Instant.now());
                toUpdate.add(product);
            }
        }

        // 4. Сохраняем сразу все
        if (!toUpdate.isEmpty()) {
            productRepository.saveAll(toUpdate);
            log.info("Batch updated {} products", toUpdate.size());
        }

        // 5. Формируем результат
        return new ImportResult(
                changes.size(),
                rows.size() - changes.size() - notFound.size(),
                notFound.size(),
                notFound,
                changes,
                filename,
                false
        );
    }
}
```

Давно хотел отрефакторить код импорта цен (обновления цен из файла от поставщиков), так как скорость была не очень и как обычно спешка.
Возникли проблемы: N+1 запрос к БД - для каждой строки отдельный запрос findBySku(), сохранение в цикле - для каждого изменения отдельный save(),
при 1000 строк в Excel → 1000 запросов на поиск + до 1000 на сохранение соответственно медленно и нагружает БД.
Использовал батч запрос, который сразу подгружает все sku из БД и сохраняем все одним saveAll(). Так скажем здесь больше рефакторинг производительности.


