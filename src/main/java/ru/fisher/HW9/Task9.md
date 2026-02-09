#### Ясный код-2

**_1.1. Методы, которые используются только в тестах._**

```java
// До
@Transactional
public void addProductToUserCart(Long userId, Long productId) {
    Cart cart = getOrCreateCart(userId, null);
    addProduct(cart.getId(), productId);
}

@Transactional
public void addProduct(Long cartId, Long productId) {
    Cart cart = cartRepository.findById(cartId)
            .orElseThrow(() -> new IllegalArgumentException("Cart not found"));
    Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Product not found"));

    // Ищем существующий элемент корзины
    CartItem existing = cartItemRepository.findByCartIdAndProductId(cartId, productId)
            .orElse(null);

    if (existing != null) {
        existing.setQuantity(existing.getQuantity() + 1);
        cartItemRepository.save(existing); return;
    }

    // Создаём новый CartItem
    CartItem item = new CartItem();
    item.setCart(cart);
    item.setProduct(product);
    item.setProductName(product.getName());
    item.setProductSku(product.getSku());
    item.setUnitPrice(product.getPrice());
    item.setQuantity(1);
    cartItemRepository.save(item);
}

// Данный метод использовался только в тестах (порядка 20 раз).
// Означает, что тестируется уже не та логика, которая требуется для работы

@Test
void addProductCreatesCartItem() {
    // Создаем корзину
    String sessionId = UUID.randomUUID().toString();
    Cart cart = cartService.getOrCreateCart(null, sessionId);

    // Создаем продукт
    Product product = createAndSaveProduct("Test-Product", BigDecimal.valueOf(10000.00));
    
    cartService.addProduct(cart.getId(), product.getId());
    
    List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
    assertThat(items).hasSize(1);

    CartItem item = items.getFirst();
    assertThat(item.getProduct().getId()).isEqualTo(product.getId());
    assertThat(item.getQuantity()).isEqualTo(1);
    assertThat(item.getUnitPrice().doubleValue())
            .isEqualTo(product.getPrice().doubleValue());
    assertThat(item.getProductName()).isEqualTo("Test-Product");
    assertThat(item.getProductSku()).isEqualTo("SKU-" + product.getName());
}

```
___
```java
// После
// Я просто убрал данный метод из сервиса и сократил кол-во тестируемых методов
// И оставил только тот, который используется везде
@Transactional
public void addProductWithQuantity(Long cartId, Long productId, Integer quantity) {
    if (quantity == null || quantity <= 0) {
        throw new IllegalArgumentException("Quantity must be positive");
    }

    Cart cart = cartRepository.findById(cartId)
            .orElseThrow(() -> new IllegalArgumentException("Cart not found"));

    Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Product not found"));

    CartItem existing = cartItemRepository.findByCartIdAndProductId(cartId, productId)
            .orElse(null);

    if (existing != null) {
        existing.setQuantity(existing.getQuantity() + quantity);
        cartItemRepository.save(existing);
        return;
    }

    // Создаем новый CartItem с указанным количеством
    CartItem item = new CartItem();
    item.setCart(cart);
    item.setProduct(product);
    item.setProductName(product.getName());
    item.setProductSku(product.getSku());
    item.setUnitPrice(product.getPrice());
    item.setQuantity(quantity);

    cartItemRepository.save(item);
}
```
Со временем в проекте возникают различные изменения и код, который вначале был необходим вытесняется или заменяется на другой.
Я решил не изменять методы в начале, а оставлять их на всякий случай, а по идее я должен был просто изменить данным метод на другой, 
при изменении логики добавления товара в корзину.  

**_1.2. Цепочки методов. Метод вызывает другой метод, который вызывает другой метод, вызывает другой метод и далее и далее._**

```java
// До
@Transactional
public Order updateStatus(Long orderId, OrderStatus newStatus) {
    validateStatusUpdate(orderId, newStatus);

    Order order = getOrder(orderId);
    validateStatusTransition(order.getStatus(), newStatus);

    order.setStatus(newStatus);
    order.setUpdatedAt(Instant.now());

    Order saved = orderRepository.save(order);
    log.info("Статус заказа обновлен: id={}, номер={}, старый статус={}, новый статус={}",
            orderId, saved.getOrderNumber(), order.getStatus(), newStatus);

    return saved;
}

// 1-й вызов
private void validateStatusUpdate(Long orderId, OrderStatus newStatus) {
    if (orderId == null) {
        throw new OrderValidationException("orderId", "ID заказа не может быть null");
    }
    if (newStatus == null) {
        throw new OrderValidationException("status", "Статус не может быть null");
    }
}

// 2-й вызов
@Transactional(readOnly = true)
public Order getOrder(Long id) {
    return orderRepository.findByIdWithItems(id)
            .orElseThrow(() -> new OrderNotFoundException(id));
}

// 3-й вызов
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

// 4-й вызов
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
```
___

```java
// После - всё в одном методе
@Transactional
public Order updateStatus(Long orderId, OrderStatus newStatus) {

    if (orderId == null) {
        throw new OrderValidationException("orderId", "ID заказа не может быть null");
    }

    if (newStatus == null) {
        throw new OrderValidationException("status", "Статус не может быть null");
    }

    Order order = getOrder(orderId);

    OrderStatus current = order.getStatus();

    if (current == OrderStatus.COMPLETED || current == OrderStatus.CANCELLED) {
        throw new OrderFinalizedException(current.name());
    }

    boolean allowed = switch (current) {
        case CREATED -> newStatus == PROCESSING
                || newStatus == PAID
                || newStatus == COMPLETED
                || newStatus == CANCELLED;

        case PROCESSING -> newStatus == PAID
                || newStatus == COMPLETED
                || newStatus == CANCELLED;

        case PAID -> newStatus == COMPLETED
                || newStatus == CANCELLED;

        default -> false;
    };

    if (!allowed) {
        throw new InvalidStatusTransitionException(current.name(), newStatus.name());
    }

    order.setStatus(newStatus);
    order.setUpdatedAt(Instant.now());

    return orderRepository.save(order);
}
```
Смотря на ситуацию "ДО" кажется, всё понятно, методы разбиты на более мелкие методы, названия описательны, но если захочется посмотреть что делает каждый из этих методов внутри,
потребуется ходить по вложенным методам. Иногда через чур сильное ветвление может сбить столку, поэтому не всегда такое дробление (декомпозиция) может идти на пользу.  

**_1.3. У метода слишком большой список параметров._**
```java
// До
// Контролер в котором используется следующий метод registerUser(...) с большим списком параметров
@PostMapping("/register")
public String register(@ModelAttribute("userDto") @Valid UserDto userDto,
                       BindingResult bindingResult,
                       Model model,
                       HttpSession session,
                       @CookieValue(value = "sessionId", required = false) String sessionId) {
    
    if (bindingResult.hasErrors()) {
        addCaptchaAttributes(model);
        return "auth/register";
    }

    if (!userDto.getPassword().equals(userDto.getConfirmPassword())) {
        model.addAttribute("passwordError", "Пароли не совпадают");
        addCaptchaAttributes(model);
        return "auth/register";
    }

    try {
        User user = userService.registerUser(
                userDto.getUsername(),
                userDto.getEmail(),
                userDto.getPassword(),
                userDto.getFirstName(),
                userDto.getLastName(),
                userDto.getPhone()
        );

        log.info("Пользователь зарегистрирован: {}", user.getUsername());
        
        if (StringUtils.hasText(sessionId)) {
            try {
                cartService.mergeCartToUser(sessionId, user.getId());
                session.removeAttribute("hasAnonymousCart");
                session.removeAttribute("anonymousSessionId");
                model.addAttribute("cartMerged", true);
            } catch (Exception e) {
                log.warn("Не удалось объединить корзину: {}", e.getMessage());
            }
        }

        return "redirect:/auth/login?success";

    } catch (IllegalArgumentException e) {
        model.addAttribute("errorMessage", e.getMessage());
        addCaptchaAttributes(model);
        return "auth/register";
    }
}

// Метод для регистрации нового пользователя
@Transactional
public User registerUser(String username, String email, String password,
                         String firstName, String lastName, String phone) {

    if (existsByUsername(username)) {
        throw new IllegalArgumentException("Пользователь с таким именем уже существует");
    }

    if (existsByEmail(email)) {
        throw new IllegalArgumentException("Пользователь с таким email уже существует");
    }

    User user = User.builder()
            .username(username)
            .email(email)
            .password(passwordEncoder.encode(password))
            .firstName(firstName)
            .lastName(lastName)
            .phone(phone)
            .enabled(true)
            .roles(new HashSet<>())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

    // Добавляем роль USER по умолчанию
    Role userRole = roleRepository.findByName(Role.ROLE_USER)
            .orElseGet(() -> createRole(Role.ROLE_USER, "Обычный пользователь"));

    user.getRoles().add(userRole);

    User saved = userRepository.save(user);
    log.info("Пользователь зарегистрирован: {}", username);

    return saved;
}
```
___

```java
// После
// Классическое использование ДТО сущности в registerUser(userDto)
@PostMapping("/register")
public String register(@ModelAttribute("userDto") @Valid UserDto userDto,
                       BindingResult bindingResult,
                       Model model,
                       HttpSession session,
                       @CookieValue(value = "sessionId", required = false) String sessionId) {
    
    if (bindingResult.hasErrors()) {
        addCaptchaAttributes(model);
        return "auth/register";
    }

    if (!userDto.getPassword().equals(userDto.getConfirmPassword())) {
        model.addAttribute("passwordError", "Пароли не совпадают");
        addCaptchaAttributes(model);
        return "auth/register";
    }

    try {
        User user = userService.registerUser(userDto);

        log.info("Пользователь зарегистрирован: {}", user.getUsername());

        // Если была анонимная корзина - объединяем
        if (StringUtils.hasText(sessionId)) {
            try {
                cartService.mergeCartToUser(sessionId, user.getId());
                session.removeAttribute("hasAnonymousCart");
                session.removeAttribute("anonymousSessionId");
                model.addAttribute("cartMerged", true);
            } catch (Exception e) {
                log.warn("Не удалось объединить корзину: {}", e.getMessage());
            }
        }

        return "redirect:/auth/login?success";

    } catch (IllegalArgumentException e) {
        model.addAttribute("errorMessage", e.getMessage());
        addCaptchaAttributes(model);
        return "auth/register";
    }
}

@Transactional
public User registerUser(UserDto dto) {

    if (existsByUsername(dto.getUsername())) {
        throw new IllegalArgumentException("Пользователь с таким именем уже существует");
    }

    if (existsByEmail(dto.getEmail())) {
        throw new IllegalArgumentException("Пользователь с таким email уже существует");
    }

    User user = User.builder()
            .username(dto.getUsername())
            .email(dto.getEmail())
            .password(passwordEncoder.encode(dto.getPassword()))
            .firstName(dto.getFirstName())
            .lastName(dto.getLastName())
            .phone(dto.getPhone())
            .enabled(true)
            .roles(new HashSet<>())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

    Role userRole = roleRepository.findByName(Role.ROLE_USER)
            .orElseGet(() -> createRole(Role.ROLE_USER, "Обычный пользователь"));

    user.getRoles().add(userRole);

    return userRepository.save(user);
}
```
Здесь я явно забыл, что у меня уже есть ДТО для модели User под определенные условия. Можно ошибиться в порядке передачи параметров.
Банальная ошибка и спешка, нужно всё перепроверять и не спешить. Стало намного лучше и по канону и корректно.  

**_1.4. Странные решения. Когда несколько методов используются для решения одной и той же проблемы, создавая несогласованность._**
```java
// До - 2 метода делающих практически одно и тоже, полная несогласованность.
@Transactional
public void addProductWithQuantity(Long cartId, Long productId, Integer quantity) {
    if (quantity == null || quantity <= 0) {
        throw new IllegalArgumentException("Quantity must be positive");
    }

    Cart cart = cartRepository.findById(cartId)
            .orElseThrow(() -> new IllegalArgumentException("Cart not found"));

    Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Product not found"));

    CartItem existing = cartItemRepository.findByCartIdAndProductId(cartId, productId)
            .orElse(null);

    if (existing != null) {
        existing.setQuantity(existing.getQuantity() + quantity);
        cartItemRepository.save(existing);
        return;
    }

    // Создаем новый CartItem с указанным количеством
    CartItem item = new CartItem();
    item.setCart(cart);
    item.setProduct(product);
    item.setProductName(product.getName());
    item.setProductSku(product.getSku());
    item.setUnitPrice(product.getPrice());
    item.setQuantity(quantity);

    cartItemRepository.save(item);
}

/**
 * Обновление количества товара в корзине
 */
@Transactional
public void updateQuantity(Long cartId, Long productId, Integer quantity) {
    if (quantity == null || quantity < 0) {
        throw new IllegalArgumentException("Quantity cannot be negative");
    }

    if (quantity == 0) {
        removeProduct(cartId, productId);
        return;
    }

    CartItem item = cartItemRepository.findByCartIdAndProductId(cartId, productId)
            .orElseThrow(() -> new IllegalArgumentException("Item not found in cart"));

    item.setQuantity(quantity);
    cartItemRepository.save(item);
}
```
___

```java
// После - оставил только 1 метод, т.к второй устарел и не используется больше в коде.
@Transactional
public void addProductWithQuantity(Long cartId, Long productId, Integer quantity) {
    if (quantity == null || quantity <= 0) {
        throw new IllegalArgumentException("Quantity must be positive");
    }

    Cart cart = cartRepository.findById(cartId)
            .orElseThrow(() -> new IllegalArgumentException("Cart not found"));

    Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Product not found"));

    CartItem existing = cartItemRepository.findByCartIdAndProductId(cartId, productId)
            .orElse(null);

    if (existing != null) {
        existing.setQuantity(existing.getQuantity() + quantity);
        cartItemRepository.save(existing);
        return;
    }

    // Создаем новый CartItem с указанным количеством
    CartItem item = new CartItem();
    item.setCart(cart);
    item.setProduct(product);
    item.setProductName(product.getName());
    item.setProductSku(product.getSku());
    item.setUnitPrice(product.getPrice());
    item.setQuantity(quantity);

    cartItemRepository.save(item);
}
```
Со временем проект растет и количество кода тоже неуклонно растет. Возникают моменты когда ты либо меняешь метод на новый.
Или для подстраховки создаешь новый, думая если что вернуться к предыдущему методу. Так я и делал, боясь поломать логику работы.
Сейчас убрал лишний код, если пройтись уже по готовому проекту с десяток таких методов точно можно найти.  

**_1.5. Чрезмерный результат. Метод возвращает больше данных, чем нужно вызывающему его компоненту._**
```java
// До

```
___

```java
// После

```