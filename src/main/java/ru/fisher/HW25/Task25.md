### Ускоряем код фреймворков в 100 раз

Для начала конечно включаем логирование SQL запросов и заранее готовлюсь к худшему зная Hibernate 
с его хваленными подборами ненужных связей, но всегда спешишь и пишешь не огладываясь назад.

````
spring.jpa.show-sql=true
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE
logging.level.org.hibernate.stat=DEBUG
Не стал делать - spring.jpa.properties.hibernate.format_sql=true, т.к. запросы выглядят громоздко и занимают много места 
````

#### Пример 1
Обычное открытие главной страницы проекта интернет-магазина ToolsMarket, ныне 18Вольт - ребрендинг).
По смыслу на главной станице загружаются все категории и затем подкатегории и дочерние при открытии.

**_Исходный код:_**
```java
// Контроллер
@GetMapping("/")
    public String home(Model model, Principal principal) {
        List<CategoryDto> parentCategories = categoryService.getParentCategoriesForHome();
        model.addAttribute("allCategories", parentCategories);
        model.addAttribute("categories", parentCategories);
        
        //......//

// Сервис
// Метод для получения только родительских категорий для главной страницы
public List<CategoryDto> getParentCategoriesForHome() {
    List<Category> parentCategories = categoryRepository.findByParentIsNullOrderBySortOrderAsc();
    return categoryMapperService.toDtoList(parentCategories);
}

// Репозиторий
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long>, JpaSpecificationExecutor<Category> {

    Optional<Category> findByTitle(String title);

    List<Category> findByParentIsNullOrderBySortOrderAsc();

    //.....//
```


**_Логи до:_**
```
org.hibernate.SQL: select c1_0.id,c1_0.created_at,c1_0.description,c1_0.image_url,c1_0.name,c1_0.parent_id,c1_0.sort_order,c1_0.thumbnail_url,c1_0.title from category c1_0 where c1_0.parent_id is null order by c1_0.sort_order
org.hibernate.SQL: select c1_0.parent_id,c1_0.id,c1_0.created_at,c1_0.description,c1_0.image_url,c1_0.name,c1_0.sort_order,c1_0.thumbnail_url,c1_0.title from category c1_0 where c1_0.parent_id=?
org.hibernate.SQL: select c1_0.parent_id,c1_0.id,c1_0.created_at,c1_0.description,c1_0.image_url,c1_0.name,c1_0.sort_order,c1_0.thumbnail_url,c1_0.title from category c1_0 where c1_0.parent_id=?
org.hibernate.SQL: select c1_0.parent_id,c1_0.id,c1_0.created_at,c1_0.description,c1_0.image_url,c1_0.name,c1_0.sort_order,c1_0.thumbnail_url,c1_0.title from category c1_0 where c1_0.parent_id=?
org.hibernate.SQL: select c1_0.parent_id,c1_0.id,c1_0.created_at,c1_0.description,c1_0.image_url,c1_0.name,c1_0.sort_order,c1_0.thumbnail_url,c1_0.title from category c1_0 where c1_0.parent_id=?
org.hibernate.SQL: select c1_0.parent_id,c1_0.id,c1_0.created_at,c1_0.description,c1_0.image_url,c1_0.name,c1_0.sort_order,c1_0.thumbnail_url,c1_0.title from category c1_0 where c1_0.parent_id=?
org.hibernate.SQL: select c1_0.parent_id,c1_0.id,c1_0.created_at,c1_0.description,c1_0.image_url,c1_0.name,c1_0.sort_order,c1_0.thumbnail_url,c1_0.title from category c1_0 where c1_0.parent_id=?
org.hibernate.SQL: select c1_0.parent_id,c1_0.id,c1_0.created_at,c1_0.description,c1_0.image_url,c1_0.name,c1_0.sort_order,c1_0.thumbnail_url,c1_0.title from category c1_0 where c1_0.parent_id=?
org.hibernate.SQL: select c1_0.parent_id,c1_0.id,c1_0.created_at,c1_0.description,c1_0.image_url,c1_0.name,c1_0.sort_order,c1_0.thumbnail_url,c1_0.title from category c1_0 where c1_0.parent_id=?
org.hibernate.SQL: select c1_0.parent_id,c1_0.id,c1_0.created_at,c1_0.description,c1_0.image_url,c1_0.name,c1_0.sort_order,c1_0.thumbnail_url,c1_0.title from category c1_0 where c1_0.parent_id=?
org.hibernate.SQL: select c1_0.parent_id,c1_0.id,c1_0.created_at,c1_0.description,c1_0.image_url,c1_0.name,c1_0.sort_order,c1_0.thumbnail_url,c1_0.title from category c1_0 where c1_0.parent_id=?
org.hibernate.SQL: select c1_0.parent_id,c1_0.id,c1_0.created_at,c1_0.description,c1_0.image_url,c1_0.name,c1_0.sort_order,c1_0.thumbnail_url,c1_0.title from category c1_0 where c1_0.parent_id=?
org.hibernate.SQL: select c1_0.parent_id,c1_0.id,c1_0.created_at,c1_0.description,c1_0.image_url,c1_0.name,c1_0.sort_order,c1_0.thumbnail_url,c1_0.title from category c1_0 where c1_0.parent_id=?
org.hibernate.SQL: select c1_0.parent_id,c1_0.id,c1_0.created_at,c1_0.description,c1_0.image_url,c1_0.name,c1_0.sort_order,c1_0.thumbnail_url,c1_0.title from category c1_0 where c1_0.parent_id=?
org.hibernate.SQL: select c1_0.parent_id,c1_0.id,c1_0.created_at,c1_0.description,c1_0.image_url,c1_0.name,c1_0.sort_order,c1_0.thumbnail_url,c1_0.title from category c1_0 where c1_0.parent_id=?
org.hibernate.SQL: select c1_0.parent_id,c1_0.id,c1_0.created_at,c1_0.description,c1_0.image_url,c1_0.name,c1_0.sort_order,c1_0.thumbnail_url,c1_0.title from category c1_0 where c1_0.parent_id=?
org.hibernate.SQL: select c1_0.parent_id,c1_0.id,c1_0.created_at,c1_0.description,c1_0.image_url,c1_0.name,c1_0.sort_order,c1_0.thumbnail_url,c1_0.title from category c1_0 where c1_0.parent_id=?
r.f.ToolsMarket.service.CategoryService: Загрузка категорий заняла: 170 мс
```

**_Код после улучшений:_**
Я прописал ручной запрос в репозитории
```java
@Query("""
        SELECT DISTINCT c FROM Category c
        LEFT JOIN FETCH c.children
        WHERE c.parent IS NULL
        ORDER BY c.sortOrder
    """)
    List<Category> findByParentIsNullOrderBySortOrderAsc();
```

**_Логи после улучшений:_**
```
org.hibernate.SQL: select distinct c1_0.id,c2_0.parent_id,c2_0.id,c2_0.created_at,c2_0.description,c2_0.image_url,c2_0.name,
                                   c2_0.sort_order,c2_0.thumbnail_url,c2_0.title,c1_0.created_at,c1_0.description,
                                   c1_0.image_url,c1_0.name,c1_0.parent_id,c1_0.sort_order,c1_0.thumbnail_url,c1_0.title 
                   from category c1_0 
                   left join category c2_0 on c1_0.id=c2_0.parent_id 
                   where c1_0.parent_id is null 
                   order by c1_0.sort_order
r.f.ToolsMarket.service.CategoryService: Загрузка категорий заняла: 140 мс
```
Видим сокращение кол-ва запросов с 11 до 1, но время выполнения сократилось не так значительно с 170 до 140 мс.
Типичная проблема N+1 запросов, при малой нагрузке на приложение не заметная, но стоит хотя бы 100 пользователям зайти на главную
страницу, то мы могли бы получить 1100 запросов вместо 100. Видно, что Hibernate всё еще управляет запросами.
___

#### Пример 2
Следующий пример я решил взять из работы админ панели текущего проекта - 
Показать админу список заказов с фильтрами (по статусу, поиску, пользователю), 
где для каждого заказа видны: номер, статус, сумма, дата, пользователь, количество товаров. 
И еще статистика по статусам сверху.

**_Исходный код:_**
```java
@GetMapping
public String listOrders(@RequestParam(required = false) String status,
                         @RequestParam(required = false) String search,
                         @RequestParam(required = false) Long userId,
                         Model model) {
    try {
        List<Order> orders = getFilteredOrders(status, search, userId);
       addOrderStatisticsToModel(model);
       
       model.addAttribute("users", userService.findAll());
       model.addAttribute("orders", orders);
       model.addAttribute("searchQuery", search);
       model.addAttribute("selectedUserId", userId);

       // Если фильтруем по пользователю, добавляем информацию о нем
       if (userId != null) {
           userService.findById(userId).ifPresent(user -> {
               model.addAttribute("selectedUser", user);
           });
       }

       return "admin/orders/index";

    } catch (Exception e) {
        log.error("Ошибка при получении списка заказов: статус={}, поиск={}, userId={}",
                status, search, userId, e);
        model.addAttribute(ERROR_MSG, "Ошибка при загрузке списка заказов");
        return "admin/orders/index";
    }
    
    // =========== Вспомогательные приватные методы ===========

    private List<Order> getFilteredOrders(String status, String search, Long userId) {
        if (userId != null) {
            return orderService.getUserOrders(userId);
        } else if (StringUtils.hasText(search)) {
            return searchOrders(search.trim());
        } else if (StringUtils.hasText(status)) {
            OrderStatus orderStatus = OrderStatus.valueOf(status);
            return orderService.getOrdersByStatus(orderStatus);
        } else {
            return orderService.getAllOrders();
        }
    }

    private List<Order> searchOrders(String searchQuery) {
        try {
            Long orderNumber = Long.parseLong(searchQuery);
            Order order = orderService.findByOrderNumber(orderNumber);
            return order != null ? List.of(order) : List.of();
        } catch (OrderNotFoundException e) {
            log.info("Заказ не найден по номеру: {}", searchQuery);
            return List.of();
        } catch (NumberFormatException e) {
            return orderService.searchOrders(searchQuery);
        }
    }

    private void addOrderStatisticsToModel(Model model) {
        model.addAttribute("newOrdersCount", orderService.countOrdersByStatus(OrderStatus.CREATED));
        model.addAttribute("paidOrdersCount", orderService.countOrdersByStatus(OrderStatus.PAID));
        model.addAttribute("completedOrdersCount", orderService.countOrdersByStatus(OrderStatus.COMPLETED));
        model.addAttribute("cancelledOrdersCount", orderService.countOrdersByStatus(OrderStatus.CANCELLED));
    }

    /**
     * Получение заказов пользователя
     */
    @Transactional(readOnly = true)
    public List<Order> getUserOrders(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    // Поиск заказов пользователя
    @EntityGraph(attributePaths = {"user", "user.userType", "orderItems", "orderItems.product"})
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Transactional(readOnly = true)
    public List<Order> getOrdersByStatus(OrderStatus status) {
        return orderRepository.findByStatusOrderByCreatedAtDesc(status);
    }
    
    // 1. Для всех заказов
    @EntityGraph(attributePaths = {"user", "user.userType", "orderItems"}) 
    @Query("SELECT o FROM Order o ORDER BY o.createdAt DESC") 
    List<Order> findAllByOrderByCreatedAtDesc();

    // 2. Для заказов по статусу
    @EntityGraph(attributePaths = {"user", "user.userType", "orderItems"})
    @Query("SELECT o FROM Order o WHERE o.status = :status ORDER BY o.createdAt DESC")
    List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);

    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }
```

**_Логи до:_**
```
org.hibernate.SQL: select o1_0.id,o1_0.created_at,o1_0.note,oi1_0.order_id,oi1_0.id,oi1_0.created_at,oi1_0.discount_amount,oi1_0.discount_percentage,oi1_0.has_discount,oi1_0.original_unit_price,oi1_0.product_id,oi1_0.product_name,oi1_0.product_sku,oi1_0.quantity,oi1_0.subtotal,oi1_0.unit_price,o1_0.order_number,o1_0.status,o1_0.total_price,o1_0.updated_at,o1_0.user_id,u1_0.id,u1_0.created_at,u1_0.email,u1_0.enabled,u1_0.first_name,u1_0.last_name,u1_0.note,u1_0.password,u1_0.phone,u1_0.updated_at,u1_0.user_type,u1_0.username from "order" o1_0 left join order_item oi1_0 on o1_0.id=oi1_0.order_id join users u1_0 on u1_0.id=o1_0.user_id order by o1_0.created_at desc
org.hibernate.SQL: select c1_0.id,c1_0.created_at,c1_0.session_id,c1_0.updated_at,c1_0.user_id from cart c1_0 where c1_0.user_id=?
org.hibernate.SQL: select c1_0.id,c1_0.created_at,c1_0.session_id,c1_0.updated_at,c1_0.user_id from cart c1_0 where c1_0.user_id=?
org.hibernate.SQL: select count(o1_0.id) from "order" o1_0 where o1_0.status=?
org.hibernate.SQL: select count(o1_0.id) from "order" o1_0 where o1_0.status=?
org.hibernate.SQL: select count(o1_0.id) from "order" o1_0 where o1_0.status=?
org.hibernate.SQL: select count(o1_0.id) from "order" o1_0 where o1_0.status=?
org.hibernate.SQL: select u1_0.id,u1_0.created_at,u1_0.email,u1_0.enabled,u1_0.first_name,u1_0.last_name,u1_0.note,u1_0.password,u1_0.phone,u1_0.updated_at,u1_0.user_type,u1_0.username from users u1_0
org.hibernate.SQL: select c1_0.id,c1_0.created_at,c1_0.session_id,c1_0.updated_at,c1_0.user_id from cart c1_0 where c1_0.user_id=?
org.hibernate.SQL: select c1_0.id,c1_0.created_at,c1_0.session_id,c1_0.updated_at,c1_0.user_id from cart c1_0 where c1_0.user_id=?
org.hibernate.SQL: select c1_0.id,c1_0.created_at,c1_0.session_id,c1_0.updated_at,c1_0.user_id from cart c1_0 where c1_0.user_id=?
org.hibernate.SQL: select c1_0.id,c1_0.created_at,c1_0.session_id,c1_0.updated_at,c1_0.user_id from cart c1_0 where c1_0.user_id=?
org.hibernate.SQL: select c1_0.id,c1_0.created_at,c1_0.session_id,c1_0.updated_at,c1_0.user_id from cart c1_0 where c1_0.user_id=?
org.hibernate.SQL: select r1_0.user_id,r1_1.id,r1_1.description,r1_1.name from user_roles r1_0 join roles r1_1 on r1_1.id=r1_0.role_id where r1_0.user_id=?
org.hibernate.SQL: select r1_0.user_id,r1_1.id,r1_1.description,r1_1.name from user_roles r1_0 join roles r1_1 on r1_1.id=r1_0.role_id where r1_0.user_id=?
org.hibernate.SQL: select r1_0.user_id,r1_1.id,r1_1.description,r1_1.name from user_roles r1_0 join roles r1_1 on r1_1.id=r1_0.role_id where r1_0.user_id=?
org.hibernate.SQL: select r1_0.user_id,r1_1.id,r1_1.description,r1_1.name from user_roles r1_0 join roles r1_1 on r1_1.id=r1_0.role_id where r1_0.user_id=?
org.hibernate.SQL: select r1_0.user_id,r1_1.id,r1_1.description,r1_1.name from user_roles r1_0 join roles r1_1 on r1_1.id=r1_0.role_id where r1_0.user_id=?
org.hibernate.SQL: select r1_0.user_id,r1_1.id,r1_1.description,r1_1.name from user_roles r1_0 join roles r1_1 on r1_1.id=r1_0.role_id where r1_0.user_id=?
org.hibernate.SQL: select r1_0.user_id,r1_1.id,r1_1.description,r1_1.name from user_roles r1_0 join roles r1_1 on r1_1.id=r1_0.role_id where r1_0.user_id=?
r.f.T.c.admin.AdminOrderController: Загрузка ORDERS заняла: 208 мс
```

**_Код после улучшений:_**
```java
// Контроллер
@GetMapping
public String listOrders(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) Long userId,
        Model model) {

   try {
      long start = System.currentTimeMillis();
      
      List<OrderAdminDto> orders = orderService.getOrdersForAdmin(status, search, userId);
      OrderStatisticsDto stats = orderService.getOrderStatistics(status, search, userId);
      List<UserFilterDto> users = orderService.getUsersForOrderFilter();

      model.addAttribute("orders", orders);
      model.addAttribute("users", users);
      model.addAttribute("searchQuery", search);
      model.addAttribute("selectedUserId", userId);
      model.addAttribute("selectedStatus", status);

      model.addAttribute("newOrdersCount", stats.getNewOrdersCount());
      model.addAttribute("paidOrdersCount", stats.getPaidOrdersCount());
      model.addAttribute("completedOrdersCount", stats.getCompletedOrdersCount());
      model.addAttribute("cancelledOrdersCount", stats.getCancelledOrdersCount());

      log.info("Страница заказов загружена: {} мс", System.currentTimeMillis() - start);
      return "admin/orders/index";

   } catch (Exception e) {
      log.error("Ошибка при получении списка заказов", e);
      model.addAttribute("errorMsg", "Ошибка при загрузке списка заказов");
      return "admin/orders/index";
   }
}

// В Сервисе
@Transactional(readOnly = true)
public List<OrderAdminDto> getOrdersForAdmin(String status, String search, Long userId) {
   long start = System.currentTimeMillis();
   List<OrderAdminDto> orders = orderAdminJdbc.findOrdersForAdmin(status, search, userId);
   log.info("Загрузка заказов для админки: {} записей, {} мс",
           orders.size(), System.currentTimeMillis() - start);
   return orders;
}

@Transactional(readOnly = true)
public OrderStatisticsDto getOrderStatistics(String status, String search, Long userId) {
   return orderAdminJdbc.getOrderStatistics(status, search, userId);
}

@Transactional(readOnly = true)
public List<UserFilterDto> getUsersForOrderFilter() {
   return orderAdminJdbc.findUsersWithOrders();
}


// Репозиторий для Jdbc
@Repository
@RequiredArgsConstructor
public class OrderAdminJdbcRepository {

   private final JdbcTemplate jdbcTemplate;

   /**
    * Динамический SQL: условия добавляются только если параметр не null.
    */
   public List<OrderAdminDto> findOrdersForAdmin(String status, String search, Long userId) {
      StringBuilder sql = new StringBuilder("""
              SELECT 
                  o.id,
                  o.order_number,
                  o.status,
                  o.total_price,
                  o.created_at,
                  o.note,
                  u.id as user_id,
                  COALESCE(NULLIF(TRIM(u.first_name || ' ' || u.last_name), ' '), u.username) as user_name,
                  u.email,
                  COALESCE(SUM(oi.quantity), 0) as items_count,
                  COUNT(DISTINCT oi.product_id) as products_count
              FROM "order" o
              JOIN users u ON u.id = o.user_id
              LEFT JOIN order_item oi ON oi.order_id = o.id
              WHERE 1=1
              """);

      List<Object> params = new ArrayList<>();

      if (status != null && !status.isBlank()) {
         sql.append(" AND o.status = ?");
         params.add(status);
      }
      if (search != null && !search.isBlank()) {
         sql.append(" AND CAST(o.order_number AS TEXT) LIKE ?");
         params.add("%" + search + "%");
      }
      if (userId != null) {
         sql.append(" AND o.user_id = ?");
         params.add(userId);
      }

      sql.append("""
               GROUP BY o.id, o.order_number, o.status, o.total_price, o.created_at, o.note,
                       u.id, u.first_name, u.last_name, u.username, u.email
              ORDER BY o.created_at DESC
              """);

      return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
         Timestamp createdAt = rs.getTimestamp("created_at");
         return OrderAdminDto.builder()
                 .id(rs.getLong("id"))
                 .orderNumber(rs.getLong("order_number"))
                 .status(rs.getString("status"))
                 .totalPrice(rs.getBigDecimal("total_price"))
                 .createdAt(createdAt != null ? createdAt.toInstant() : null)
                 .note(rs.getString("note"))
                 .userId(rs.getLong("user_id"))
                 .userName(rs.getString("user_name"))
                 .userEmail(rs.getString("email"))
                 .itemsCount(rs.getLong("items_count"))
                 .productsCount(rs.getLong("products_count"))
                 .build();
      }, params.toArray());
   }

   /**
    * Статистика
    */
   public OrderStatisticsDto getOrderStatistics(String status, String search, Long userId) {
      StringBuilder sql = new StringBuilder("""
              SELECT 
                  SUM(CASE WHEN status = 'CREATED' THEN 1 ELSE 0 END) as new_orders,
                  SUM(CASE WHEN status = 'PAID' THEN 1 ELSE 0 END) as paid_orders,
                  SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as completed_orders,
                  SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) as cancelled_orders
              FROM "order"
              WHERE 1=1
              """);

      List<Object> params = new ArrayList<>();

      if (status != null && !status.isBlank()) {
         sql.append(" AND status = ?");
         params.add(status);
      }
      if (search != null && !search.isBlank()) {
         sql.append(" AND CAST(order_number AS TEXT) LIKE ?");
         params.add("%" + search + "%");
      }
      if (userId != null) {
         sql.append(" AND user_id = ?");
         params.add(userId);
      }

      return jdbcTemplate.queryForObject(sql.toString(), (rs, rowNum) ->
                      OrderStatisticsDto.builder()
                              .newOrdersCount(rs.getLong("new_orders"))
                              .paidOrdersCount(rs.getLong("paid_orders"))
                              .completedOrdersCount(rs.getLong("completed_orders"))
                              .cancelledOrdersCount(rs.getLong("cancelled_orders"))
                              .build(),
              params.toArray()
      );
   }

   /**
    * Пользователи, у которых есть заказы — для фильтра.
    */
   public List<UserFilterDto> findUsersWithOrders() {
      String sql = """
              SELECT 
                  u.id,
                  COALESCE(NULLIF(TRIM(u.first_name || ' ' || u.last_name), ' '), u.username) as display_name
              FROM users u
              WHERE EXISTS (SELECT 1 FROM "order" o WHERE o.user_id = u.id)
              ORDER BY display_name
              """;

      return jdbcTemplate.query(sql, (rs, rowNum) ->
              UserFilterDto.builder()
                      .id(rs.getLong("id"))
                      .displayName(rs.getString("display_name"))
                      .build()
      );
   }
}

```

**_Логи после улучшений:_**
```
o.s.jdbc.core.JdbcTemplate: Executing prepared SQL statement [SELECT
    o.id,
    o.order_number,
    o.status,
    o.total_price,
    o.created_at,
    o.note,
    u.id as user_id,
    COALESCE(NULLIF(TRIM(u.first_name || ' ' || u.last_name), ' '), u.username) as user_name,
    u.email,
    COALESCE(SUM(oi.quantity), 0) as items_count,
    COUNT(DISTINCT oi.product_id) as products_count
FROM "order" o
JOIN users u ON u.id = o.user_id
LEFT JOIN order_item oi ON oi.order_id = o.id
WHERE 1=1
 GROUP BY o.id, o.order_number, o.status, o.total_price, o.created_at, o.note,
         u.id, u.first_name, u.last_name, u.username, u.email
ORDER BY o.created_at DESC
]
o.s.jdbc.core.JdbcTemplate: Executing prepared SQL query
o.s.jdbc.core.JdbcTemplate: Executing prepared SQL statement [SELECT
    SUM(CASE WHEN status = 'CREATED' THEN 1 ELSE 0 END) as new_orders,
    SUM(CASE WHEN status = 'PAID' THEN 1 ELSE 0 END) as paid_orders,
    SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as completed_orders,
    SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) as cancelled_orders
FROM "order"
WHERE 1=1
]
o.s.jdbc.core.JdbcTemplate: Executing SQL query [SELECT
    u.id,
    COALESCE(NULLIF(TRIM(u.first_name || ' ' || u.last_name), ' '), u.username) as display_name
FROM users u
WHERE EXISTS (SELECT 1 FROM "order" o WHERE o.user_id = u.id)
ORDER BY display_name
]
r.f.T.c.admin.AdminOrderController: Загрузка заказов для админки: 11 записей, 6 мс
```

Решил попробовать перейти на JdbcTemplate и написать чистые запросы. Сокращение времени колоссальное с 208 мс до 6 мс.
Hibernate заставляет меня лениться и не стараться вдумываться в запрос, что к чему, много лишнего идет.
Да это дольше и труднее, пришлось вспоминать как писать нормальные запросы в SQL. 
Постараюсь в проекте перепроверить все запросы, где подгружаются лишние связи. Сейчас нагрузка мала поэтому ничего не заметно,
но всему своё время и может быть не 1-2 пользователя в день будут нагружать приложение).
___

#### Пример 3
Третий пример я взял просмотр всех товаров на главной странице при переходе в нужную категорию.  
Логика такая, пользователь переходя на главную страницу видит все доступные категории, при переходе в нужную категорию он видит
подкатегории, либо сразу товары с пагинацией. На странице много разных связей Категории (Родительские и дочерние), Товары, Корзина, Пользователь.

**_Исходный код:_**
```java
// Контроллер
@GetMapping("/category/{title}")
public String category(@PathVariable String title,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "name_asc") String sort,
                       @AuthenticationPrincipal UserDetails userDetails,
                       Model model) {
   long start = System.currentTimeMillis();
   User user = null;
   if (userDetails != null) {
      user = userService.findByUsername(userDetails.getUsername()).orElse(null);
   }

   CategoryDto category = categoryService.findByTitle(title)
           .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

   // Создаем PageRequest с сортировкой
   PageRequest pageRequest = PageRequest.of(page, 12, productService.getSort(sort));
   Page<ProductListDto> products = productService.findByCategoryWithDiscounts(
           category.getId(), user, pageRequest);

   // Проверка товаров в корзине (только для авторизованных)
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

   // Добавляем информацию о товарах в корзине в каждый продукт
   List<ProductListDto> productsWithCartInfo = products.getContent().stream()
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
           products.getPageable(),
           products.getTotalElements()
   );

   model.addAttribute("category", category);
   model.addAttribute("products", enhancedProducts);
   model.addAttribute("cartProductQuantities", cartProductQuantities);
   model.addAttribute("currentSort", sort);

   log.info("Страница Товаров в категории #{} загружена: {} мс",
           category.getName(), System.currentTimeMillis() - start);
   return "catalog/category";
}

// В Сервисе
public Page<ProductListDto> findByCategoryWithDiscounts(Long categoryId, User user, Pageable pageable) {
   Page<Product> products = productRepository.findActiveByCategory(categoryId, pageable);

   return products.map(product -> {
      ProductListDto dto = productMapperService.toListDto(product, user);
      return dto;
   });
}

// В репозитории
@Query("SELECT p FROM Product p " +
        "JOIN p.categories c " +
        "WHERE c.id = :categoryId AND p.active = true")
Page<Product> findActiveByCategory(@Param("categoryId") Long categoryId, Pageable pageable);

// Маппер
public ProductListDto toListDto(Product product, User user) {
   ProductListDto dto = ProductListDto.builder()
           .id(product.getId())
           .name(product.getName())
           .title(product.getTitle())
           .shortDescription(product.getShortDescription())
           .sku(product.getSku())
           .price(product.getPrice())
           .currency(product.getCurrency())
           .active(product.isActive())
           .productType(product.getProductType())
           .createdAt(product.getCreatedAt())
           .build();

   // Устанавливаем главное изображение
   if (!product.getImages().isEmpty()) {
      ProductImage mainImage = product.getImages().iterator().next();
      dto.setMainImageUrl(mainImage.getUrl());
      dto.setImages(product.getImages().stream()
              .map(img -> ProductImageDto.builder()
                      .url(img.getUrl())
                      .alt(img.getAlt())
                      .sortOrder(img.getSortOrder())
                      .build())
              .toList());
   }

   // Рассчитываем скидку если есть пользователь и productType
   if (user != null && product.getProductType() != null) {
      BigDecimal discountPercentage = discountService.calculateDiscount(user, product);
      if (discountPercentage.compareTo(BigDecimal.ZERO) > 0) {
         dto.setDiscountPercentage(discountPercentage);
         dto.setDiscountedPrice(discountService.getPriceWithDiscount(user, product));
         dto.setHasDiscount(true);
      }
   }

   return dto;
}
```

**_Логи до:_**
```
org.hibernate.SQL: select distinct c1_0.id,... from category c1_0 left join category c2_0 on c1_0.id=c2_0.parent_id where c1_0.parent_id is null
org.hibernate.SQL: select u1_0.id,... from users u1_0 where u1_0.username=?
org.hibernate.SQL: select c1_0.id,... from cart c1_0 where c1_0.user_id=?
org.hibernate.SQL: select r1_0.user_id,... from user_roles r1_0 join roles r1_1 ...
org.hibernate.SQL: select distinct c1_0.id,... from category c1_0 left join category p1_0 on p1_0.id=c1_0.parent_id left join category c2_0 on c1_0.id=c2_0.parent_id where c1_0.title=?
org.hibernate.SQL: select p1_0.id,... from product p1_0 join product_category c1_0 on p1_0.id=c1_0.product_id where c1_0.category_id=? and p1_0.active=true
org.hibernate.SQL: select count(p1_0.id) from product p1_0 join product_category c1_0 on ...
org.hibernate.SQL: select i1_0.product_id,... from product_image i1_0 where i1_0.product_id=? order by i1_0.sort_order - 12 таких запросов
org.hibernate.SQL: select ud1_0.id,... from user_discounts ud1_0 where ud1_0.user_type=? and ud1_0.product_type=? - 12 таких запросов
org.hibernate.SQL: select c1_0.id,... from cart c1_0 left join users u1_0 on u1_0.id=c1_0.user_id where u1_0.id=?
org.hibernate.SQL: select distinct c1_0.id,... from cart c1_0 left join cart_item i1_0 on ... where c1_0.id=?
r.f.T.controller.CatalogController: Страница Товаров в категории #Ручной инструмент загружена: 78 мс
```
Не указываю весь лог, так как он большой. 
Но видно сразу что повторяющиеся запросы для получения изображений товаров 
и расчет скидок для каждого и все это только для 12 товаров с пагинацией 900+ страниц.


**_Код после улучшений:_**
```java
@Data
@Builder
public class CategoryPageData {
   private CategoryDto category;
   private Page<ProductCardDto> products;
   private Map<Long, Integer> cartProductQuantities;
   private long totalElements;
}

@Repository
@RequiredArgsConstructor
public class CategoryJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Получение товаров в категории
     */
    public Page<ProductCardDto> findProductsByCategory(
            Long categoryId,
            Long userId,
            String sort,
            int page,
            int size) {

        String orderBy = switch (sort) {
            case "price_asc" -> "p.price ASC";
            case "price_desc" -> "p.price DESC";
            case "popularity" -> "p.views DESC";
            default -> "p.name ASC";
        };

        String sql = """
            SELECT 
                p.id,
                p.title,
                p.name,
                p.sku,
                p.price,
                p.short_description,
                p.active,
                (SELECT pi.url FROM product_image pi 
                 WHERE pi.product_id = p.id 
                 ORDER BY pi.sort_order LIMIT 1) as main_image_url,
                ud.discount_percentage,
                ROUND(p.price * (1 - COALESCE(ud.discount_percentage, 0) / 100), 2) as discounted_price,
                CASE WHEN ud.discount_percentage IS NOT NULL AND ud.is_active = true 
                     THEN true ELSE false END as has_discount,
                CASE WHEN ci.id IS NOT NULL THEN true ELSE false END as in_cart,
                COALESCE(ci.quantity, 0) as cart_quantity
            FROM product p
            JOIN product_category pc ON pc.product_id = p.id
            LEFT JOIN user_discounts ud ON ud.user_type = ?
                AND ud.product_type = p.product_type
                AND ud.is_active = true
            LEFT JOIN cart_item ci ON ci.product_id = p.id
                AND ci.cart_id = (SELECT id FROM cart WHERE user_id = ?)
            WHERE pc.category_id = ? AND p.active = true
            ORDER BY """ + orderBy + """
            LIMIT ? OFFSET ?
            """;

        String userType = userId != null ? getUserType(userId) : "REGULAR";

        List<ProductCardDto> products = jdbcTemplate.query(
            sql,
            new Object[]{userType, userId, categoryId, size, page * size},
            (rs, rowNum) -> ProductCardDto.builder()
                .id(rs.getLong("id"))
                .title(rs.getString("title"))
                .name(rs.getString("name"))
                .sku(rs.getString("sku"))
                .price(rs.getBigDecimal("price"))
                .shortDescription(rs.getString("short_description"))
                .active(rs.getBoolean("active"))
                .mainImageUrl(rs.getString("main_image_url"))
                .discountPercentage(rs.getBigDecimal("discount_percentage"))
                .discountedPrice(rs.getBigDecimal("discounted_price"))
                .hasDiscount(rs.getBoolean("has_discount"))
                .inCart(rs.getBoolean("in_cart"))
                .cartQuantity(rs.getInt("cart_quantity"))
                .build()
        );
        
        long total = countProductsByCategory(categoryId);

        return new PageImpl<>(products, PageRequest.of(page, size), total);
    }

    public long countProductsByCategory(Long categoryId) {
        String sql = """
            SELECT COUNT(*)
            FROM product p
            JOIN product_category pc ON pc.product_id = p.id
            WHERE pc.category_id = ? AND p.active = true
            """;
        return jdbcTemplate.queryForObject(sql, new Object[]{categoryId}, Long.class);
    }

    private String getUserType(Long userId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT user_type FROM users WHERE id = ?",
                new Object[]{userId},
                String.class
            );
        } catch (Exception e) {
            return "REGULAR";
        }
    }
}

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapperService categoryMapperService;
    private final CategoryJdbcRepository categoryJdbcRepository;

    public Optional<CategoryDto> findByTitle(String title) {
        return categoryRepository.findByTitleWithJoins(title)
                .map(categoryMapperService::toDto);
    }

    /**
     * Получение всех данных для страницы категории
     */
    @Transactional(readOnly = true)
    public CategoryPageData getCategoryPage(String title, Long userId, String sort, int page, int size) {
        CategoryDto category = findByTitle(title)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        
        Page<ProductCardDto> products = categoryJdbcRepository.findProductsByCategory(
                category.getId(), userId, sort, page, size
        );

        return CategoryPageData.builder()
                .category(category)
                .products(products)
                .totalElements(products.getTotalElements())
                .build();
    }
}

@GetMapping("/category/{title}")
public String category(@PathVariable String title,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "name_asc") String sort,
                       @AuthenticationPrincipal UserDetails userDetails,
                       Model model) {

    long start = System.currentTimeMillis();

    Long userId = null;
    if (userDetails != null) {
        userId = userService.findByUsername(userDetails.getUsername())
                .map(User::getId)
                .orElse(null);
    }

    CategoryPageData pageData = categoryService.getCategoryPage(title, userId, sort, page, 12);

    model.addAttribute("category", pageData.getCategory());
    model.addAttribute("products", pageData.getProducts());
    model.addAttribute("cartProductQuantities", pageData.getCartProductQuantities());
    model.addAttribute("totalElements", pageData.getTotalElements());
    model.addAttribute("currentSort", sort);

    log.info("Страница Товаров в категории #{} загружена: {} мс",
            title, System.currentTimeMillis() - start);
    return "catalog/category";
}
```

**_Логи после улучшений:_**
```
o.s.jdbc.core.JdbcTemplate: Executing prepared SQL statement [SELECT
    p.id,
    p.title,
    p.name,
    p.sku,
    p.price,
    p.short_description,
    p.active,
    (SELECT pi.url FROM product_image pi WHERE pi.product_id = p.id ORDER BY pi.sort_order LIMIT 1) as main_image_url,
    ud.discount_percentage,
    ROUND(p.price * (1 - COALESCE(ud.discount_percentage, 0) / 100), 2) as discounted_price,
    CASE WHEN ud.discount_percentage IS NOT NULL AND ud.is_active = true THEN true ELSE false END as has_discount,
    CASE WHEN ci.id IS NOT NULL THEN true ELSE false END as in_cart,
    COALESCE(ci.quantity, 0) as cart_quantity
FROM product p
JOIN product_category pc ON pc.product_id = p.id
LEFT JOIN user_discounts ud ON ud.user_type = ?
    AND ud.product_type = p.product_type
    AND ud.is_active = true
LEFT JOIN cart_item ci ON ci.product_id = p.id
    AND ci.cart_id = (SELECT id FROM cart WHERE user_id = ?)
WHERE pc.category_id = ? AND p.active = true
ORDER BY p.name ASC
LIMIT ? OFFSET ?
]

o.s.jdbc.core.JdbcTemplate: Executing prepared SQL query [SELECT COUNT(*)
FROM product p
JOIN product_category pc ON pc.product_id = p.id
WHERE pc.category_id = ? AND p.active = true
]

r.f.T.controller.CatalogController: Страница Товаров в категории #Ручной инструмент:загружена: 38 мс
```
Также здесь видим улучшение с 78 мс до 38 мс, практически в 2 раза, но здесь еще есть моменты где можно глубже посмотреть на дополнительные запросы,
которые можно оптимизировать или исключить.

___

Глядя на все эти примеры еще раз убедился, что ORM это "приляпка". Разработчик им начинает пренебрегать и пользоваться везде и всюду, 
особенно если в проекте нет ограничений или это не легаси код. Знаю у некоторых в компаниях специально пишут на чистом SQL и все эти Jdbc.
Сейчас делая задания понял, что напрочь забыл написание сложных запросов, но именно зная логику своего кода ты можешь написать нормальный
запрос, который выгрузит необходимые данные не делая N+1 или не подгружая лишние сущности и поля.
Чаще ориентироваться на ленивую загрузку ведь данные лучше подгружать тогда, когда они потребуются, а не всё и сразу.
Хотя тут нужно всегда учитывать бизнес-логику и контекст. В простых запросах я думаю можно обойтись с ORM, но когда в дело идут куча взаимосвязанных сущностей и 
запрос усложняется, тогда стоит обратить внимание на логи SQL-логи и перепроверить себя на предмет использования обычного нативного запроса.








