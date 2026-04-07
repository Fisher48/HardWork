### Неочевидные проектные ошибки (1)

#### Пример 1
До
```java
@Transactional
public User registerUser(String username, String email, String password,
                         String firstName, String lastName, String phone) {
    
    if (existsByUsername(username)) {
        throw new IllegalArgumentException("Пользователь с таким именем уже существует");
    }

    if (existsByEmail(email)) {
        throw new IllegalArgumentException("Пользователь с таким email уже существует");
    }
    
    // ... //
}
```

После
```java
@Column(unique = true)
private String username;

@Column(unique = true)
private String email;

@Transactional
public User registerUser(String username, String email, String password,
                         String firstName, String lastName, String phone) {
    // ... //
    try {
        return userRepository.save(user);
    } catch (DataIntegrityViolationException e) {
        throw new UserAlreadyExistsException("Username или email уже заняты");
    }
}
```
Была типичная проверка на уже существующий email и username, хотя достаточно было явно задать это в сущности.
Что я и сделал убрав лишние проверки, добавил уникальность каждому полю и скрыв логику проверки.
___


#### Пример 2
До
```java
@Transactional
public void updateUser(Long userId, String firstName, String lastName, String email, 
                       String phone, UserType userType, String note, boolean enabled) {

    User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

    if (firstName != null) user.setFirstName(firstName);
    if (lastName != null) user.setLastName(lastName);
    if (email != null) user.setEmail(email);
    if (phone != null) user.setPhone(phone);
    if (userType != null) user.setUserType(userType);
    if (note != null) user.setNote(note);
    user.setEnabled(enabled);
    user.setUpdatedAt(Instant.now());

    userRepository.save(user);

    log.info("Пользователь {} обновлен администратором", user.getUsername());
}
```

После
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileUpdateDto {
    
    @NotBlank(message = "Email обязателен")
    @Email(message = "Неверный формат email")
    private String email;
    private String firstName;
    private String lastName;

    @Pattern(regexp ="^\\+?[78][\\s\\-]*\\d{3}[\\s\\-]*\\d{3}[\\s\\-]*\\d{2}[\\s\\-]*\\d{2}$",
            message = "Неверный формат телефона")
    private String phone;
    
    private String currentPassword;
    private String newPassword;
    private String confirmPassword;

    // Вспомогательные методы
    public boolean isPasswordChangeRequested() {
        return hasText(currentPassword)
                || hasText(newPassword)
                || hasText(confirmPassword);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    @AssertTrue(message = "Пароли не совпадают")
    public boolean isPasswordsMatch() {
        if (!hasText(newPassword) && !hasText(confirmPassword)) {
            return true; // пароль не меняем
        }
        return newPassword != null && newPassword.equals(confirmPassword);
    }
}

@Transactional
public void updateUser(Long userId, UserProfileUpdateDto dto) {
    User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
    
    user.setEmail(dto.getEmail());
    user.setFirstName(dto.getFirstName());
    user.setLastName(dto.getLastName());
    user.setPhone(dto.getPhone());
    user.setUserType(dto.getUserType());
    user.setNote(dto.getNote());
    user.setUpdatedAt(Instant.now());

    userRepository.save(user);
    log.info("Пользователь {} обновлен", user.getUsername());
}

```
Было куча if проверок на null при обновлении пользователя, заменил на обычную DTO с валидацией, 
где и реализована вся проверка по необходимым полям модели User.
___


#### Пример 3
До
```java
@PostMapping("/save")
public String saveDiscount(
        @RequestParam(required = false) Long id,
        @RequestParam(required = false) String userType,
        @RequestParam(required = false) String productType,
        @RequestParam(required = false) BigDecimal discountPercentage,
        @RequestParam(defaultValue = "false") boolean active,
        RedirectAttributes redirectAttributes) {
    
    if (userType == null || userType.isEmpty()) {
        redirectAttributes.addFlashAttribute("error", "Тип пользователя обязателен");
        return "redirect:/admin/discounts";
    }
    if (productType == null || productType.isEmpty()) {
        redirectAttributes.addFlashAttribute("error", "Тип товара обязателен");
        return "redirect:/admin/discounts";
    }
    if (discountPercentage == null) {
        redirectAttributes.addFlashAttribute("error", "Процент скидки обязателен");
        return "redirect:/admin/discounts";
    }
        
    // ... //
    
    return "redirect:/admin/discounts";
}
```

После
```java
@Data
public class DiscountDTO {

    private Long id;

    @NotNull(message = "Тип пользователя обязателен")
    private UserType userType;

    @NotNull(message = "Тип товара обязателен")
    private ProductType productType;

    @NotNull(message = "Процент скидки обязателен")
    @DecimalMin(value = "0.0", message = "Скидка не может быть отрицательной")
    @DecimalMax(value = "100.0", message = "Скидка не может превышать 100%")
    private BigDecimal discountPercentage;

    private boolean active = false;

    // Конструктор с валидацией
    public DiscountDTO {
        if (discountPercentage != null && discountPercentage.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Скидка не может быть отрицательной");
        }
        if (discountPercentage != null && discountPercentage.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Скидка не может превышать 100%");
        }
    }
}

// Чистый контроллер
@PostMapping("/save")
public String saveDiscount(@Valid @ModelAttribute DiscountDTO dto,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes) {

    if (bindingResult.hasErrors()) {
        redirectAttributes.addFlashAttribute("error", bindingResult.getAllErrors().getFirst().getDefaultMessage());
        return "redirect:/admin/discounts";
    }

    discountService.saveDiscount(dto);
    redirectAttributes.addFlashAttribute("success", "Скидка сохранена");
    return "redirect:/admin/discounts";
}

@Service
public class DiscountService {
    public void saveDiscount(DiscountDTO dto) {
        UserDiscount discount = discountRepository.findById(dto.getId()).orElseThrow();
        discount.setUserType(dto.getUserType());
        discount.setProductType(dto.getProductType());
        discount.setDiscountPercentage(dto.getDiscountPercentage());
        discount.setActive(dto.isActive());

        discountRepository.save(discount);
    }
}
```
В контроллере Discount много защитного кода, проверки на null. Вынес всё в отдельное ДТО с необходимой валидацией.
Контролер стал чище, убрались дополнительные проверки.
___


#### Пример 4
До
```java
/**
 * Расчет скидки для пользователя на конкретный товар
 */
public BigDecimal calculateDiscount(User user, Product product) {
    if (user == null || product == null || user.getUserType() == null) {
        return BigDecimal.ZERO;
    }

    // Получаем скидку для комбинации типа пользователя и типа товара
    Optional<UserDiscount> discountOpt = userDiscountRepository
            .findByUserTypeAndProductType(user.getUserType(), product.getProductType());

    if (discountOpt.isPresent() && discountOpt.get().isActive()) {
        return discountOpt.get().getDiscountPercentage();
    }

    return BigDecimal.ZERO;
}
```

После
```java
@Entity
public class User {
    
    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", length = 50)
    @Builder.Default
    private UserType userType = UserType.REGULAR;
    
    // ... //
}

public BigDecimal calculateDiscount(User user, Product product) {
    return userDiscountRepository
            .findByUserTypeAndProductType(user.getUserType(), product.getProductType())
            .filter(UserDiscount::isActive)
            .map(UserDiscount::getDiscountPercentage)
            .orElse(BigDecimal.ZERO);
}
```
Убрал проверку для UserType оно всегда по умолчанию создается как обычная скидка у пользователя.
Также Пользователь и товар всегда существуют по логике и дизайну, это излишняя проверка на дурака, которая больше не требуется.
И переписал метод в более функциональном стиле без if проверок.
___


#### Пример 5
До
```java
public void addNote(Long orderId, String note) {
    validateNote(note);

    Order order = getOrder(orderId);
    order.setNote(note);
    order.setUpdatedAt(Instant.now());

    orderRepository.save(order);
}

private void validateNote(String note) {
    if (!StringUtils.hasText(note)) {
        throw new OrderValidationException("note", "Примечание не может быть пустым");
    }
    if (note.length() > 1000) {
        throw new OrderValidationException("note",
                "Примечание слишком длинное (максимум 1000 символов)");
    }
}

@Entity
public class Order {
    // ... //

    @Column(columnDefinition = "text")
    private String note;

    // ... //
}

```

После
```java
public void addNote(Long orderId, String note) {
    Order order = getOrder(orderId);
    order.setNote(note);
    order.setUpdatedAt(Instant.now());
    orderRepository.save(order);
}
```
Обнаружил в коде валидацию количества символов в примечании и понял, что она не требуется, так как в модели тип text без ограничений.
В итоге убрал не нужную проверку поля note

Смотря на все эти защитные трюки "от дурака" понимаешь, что если ты начинаешь в самом начале проектировать дизайн и типы как надо, то и нет необходимости
на низком уровне делать какие-либо дополнительные проверки. Это все идёт из-за спешки, в погоне за временем и результатом. И стоит остановиться и посмотреть, 
что ты написал и начинаешь понимать, что тут проверка на проверке, хоть это и не асерты.