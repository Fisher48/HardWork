#### Божественная линия кода

**_Пример 1:_**
```java
// Было:
List<OrderItemDto> orderItemDtos = order.getOrderItems().stream()
                    .map(OrderItemDto::fromEntity)
                    .toList();

// Стало:
// Вынес маппинг в слой сервиса
List<OrderItemDto> orderItemDtos = orderService.mapToDto(order);

public List<OrderItemDto> mapToDto(Order order) {
    return order.getOrderItems().stream()
            .map(OrderItemDto::fromEntity)
            .toList();
}
```
___

**_Пример 2:_**
```java
// Было в контроллере
BigDecimal originalTotal = orderItemDtos.stream()
        .map(OrderItemDto::getTotalWithoutDiscount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

// Стало в контроллере
BigDecimal originalTotal = calculateOriginalTotal(orderItemDtos);

// Перенес метод в сервис для чистоты контроллера, контроллер был перегружен не нужной работой
public BigDecimal calculateOriginalTotal(List<OrderItemDto> items) {
    return items.stream()
            .map(OrderItemDto::getTotalWithoutDiscount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
}
```
___

**_Пример 3:_**
```java
// Было Проверка роли в контроллере
boolean isAdmin = user.getAuthorities().stream()
        .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));

// Стало
// Перенес в UserService логику
boolean isAdmin = userService.isAdmin(user);

public boolean isAdmin(User user) {
    return user.getAuthorities().stream()
            .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
}
```
___

**_Пример 4:_**
```java
// Было
// Получение типа пользователя для отображения в деталях заказа
model.addAttribute("userTypeDisplay", order.getUser().getUserType().getDisplayName());

// Стало
// Упростил получение типа пользователя, нет необходимости лезть в дебри через заказ
model.addAttribute("userTypeDisplay", currentUser.getUserType());

```
___

**_Пример 5:_**
```java
// Было
Pageable pageable = PageRequest.of(
        page != null ? Math.max(page - 1, 0) : 0,
        size != null ? size : 10
);

// Стало
// Разбил на методы (декомпозиция), теперь понятно что делает каждый метод
Pageable pageable = buildPageable(page, size);

private Pageable buildPageable(Integer page, Integer size) {

    int pageNumber = resolvePage(page);
    int pageSize = resolveSize(size);

    return PageRequest.of(pageNumber, pageSize);
}

private int resolvePage(Integer page) {
    if (page == null) {
        return 0;
    }
    return Math.max(page - 1, 0);
}

private int resolveSize(Integer size) {
    return size != null ? size : 10;
}

```

Что можно сказать в итоге, много строк обычно используется в стримах, но там я считаю что нет ничего такого, что критично, 
есть пару моментов когда там происходит много лишней обработки, которая должна быть в других сервисах.  
Бывают случаи когда ты по-быстрому, не обращая внимания на то, что уже влезаешь в сущность-сущность... и забываешь, что это и есть нарушение SRP и вообще всех канонов), 
для получения необходимых данных. Очевидно что строки, в которых происходит много действий (операций), очень сложно читать и анализировать.  
Необходимо периодически делать рефакторинг, да бы не создавался такой комок проблем в коде.


