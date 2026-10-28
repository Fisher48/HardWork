### Мутабельность как упрощение модели данных

В этом задании я решил придумать отдельный маленький пример. На небольшом классе лучше видно
разницу между значением и конкретным объектом, и не отвлекают детали рабочего проекта.

Есть класс `Book` в библиотеке: он `final`, его код мы менять не можем.
В нём есть ISBN и название, но нет шифра полки, где физически лежит экземпляр. 
Нам нужно добавить каждой книге поле `shelfCode`.

```java
public final class Book {
    private final String isbn;
    private String title;

    public Book(String isbn, String title) {
        this.isbn = isbn;
        this.title = title;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Book book)) return false;
        return Objects.equals(isbn, book.isbn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isbn);
    }
}
```

Про наследника речи не идёт: класс `final`. Можно было бы сделать обёртку `ShelfBook`, но это новый
тип, который пришлось бы протаскивать через все методы ради одного поля. Для такой мелочи это
действительно выглядит как стрельба из пушки по воробьям.

___

#### Первая попытка - обычный HashMap

Сначала я сделал самое очевидное: положил книги в `HashMap`.

```java
Map<Book, String> shelfByBook = new HashMap<>();

Book book1 = new Book("978-5-4461-4654-1", "Spring по-быстрому");
Book book2 = new Book("978-5-4461-4654-1", "Spring по-быстрому");

shelfByBook.put(book1, "A-1");
shelfByBook.put(book2, "B-2");
```

Я ожидал, что в словаре будет две записи. Но получил:

```
book1.equals(book2): true
shelfByBook.size(): 1
shelfByBook.get(book1): B-2
```

Вторая книга затёрла первую. Причина в том, что `HashMap` сравнивает ключи через `equals` и `hashCode`,
а у `Book` они переопределены по ISBN. Для модели две физические копии одной книги - разные экземпляры,
но для `HashMap` они одинаковые, потому что ISBN у них один.

___

#### Как я разобрался с IdentityHashMap

Сначала я подумал: возьму `book.hashCode()`. Но он у меня тоже считается по ISBN, значит, проблема
остаётся той же. Погуглил «java map key by object identity», полез в документацию и нашёл
`System.identityHashCode(Object)` возвращает хэш-код, связанный с идентичностью объекта, 
независимо от переопределённого методом `hashCode()` поведения. При этом уникальность хэша не гарантируется.

Дальше в javadoc `IdentityHashMap` я прочитал главное: этот словарь сравнивает ключи по ссылке (`==`),
а не через `equals`. То есть две разные книги с одинаковым ISBN для него разные ключи, а одна и та же
ссылка — один и тот же ключ.
___

#### Решение со словарём

Дополнительное поле `shelfCode` живёт не в `Book`, а в отдельном словаре. Ключ - конкретный объект книги.

```java
public final class ShelfRegistry {
    private final Map<Book, String> shelfByBook = new IdentityHashMap<>();

    public void put(Book book, String shelfCode) {
        shelfByBook.put(book, shelfCode);
    }

    public String get(Book book) {
        return shelfByBook.get(book);
    }

    public int size() {
        return shelfByBook.size();
    }
}

ShelfRegistry registry = new ShelfRegistry();

registry.put(book1, "A-1");
registry.put(book2, "B-2");

registry.size();
registry.get(book1);
registry.get(book2);
```

Теперь в словаре две записи: у `book1` полка `A-1`, у `book2` — `B-2`. 
Обе копии сохранились, хотя по значениям полей они равны.

Можно было сделать буквально как в задании - хранить в ключе хэш объекта:

```java
Map<Integer, String> shelfByHash = new HashMap<>();

shelfByHash.put(System.identityHashCode(book1), "A-1");
shelfByHash.put(System.identityHashCode(book2), "B-2");
```

Но в javadoc `System.identityHashCode` написано, что уникальность хэша не гарантируется. Если два
разных объекта получат одинаковый хэш, вторая запись затрёт первую, и я об этом даже не узнаю.
`IdentityHashMap` внутри тоже использует identity-хэш, но сам разрешает коллизии и дополнительно
сравнивает ссылки. Поэтому я оставил его, а не `Map<Integer, String>`.

___

#### Главная ловушка

Словарь работает, пока я передаю в него те же самые объекты. Если книгу пересоздать, связь потеряется:

```java
Book recreated = new Book("978-5-4461-4654-1", "Spring по-быстрому");
String shelf = registry.get(recreated);
```

Здесь `shelf` будет `null`. Формально это та же книга с тем же ISBN, но для `IdentityHashMap` это новый объект, 
а значит, другой ключ. Если книги загружаются из БД или приходят из внешнего API заново, шифр полки может потеряться.

___

**_Вывод:_**  

Упрощение или усложнение, для меня здесь есть и то, и другое, но на разных уровнях.  
Упростилось то, что класс остается закрытым и не появляется наследник ради одного значения, 
доп. данные можно прикреплять там где они нужны, а не тащить через весь код.   
А усложнилось то, что поля лежат в разных классах и связь здесь неявная, все держится на идентичности объекта и
стоит пересоздать объект и данные потеряются.  
В итоге для конкретного класса код стал проще, а вот модель данных — сложнее. Это тактический
приём для временного технического поля: кэш, состояние UI, промежуточный этап импорта. 
Если `shelfCode` - это бизнес-данные, которые должны жить в БД, то проблема не в `Book`, а в том, что в модели нет
сущности с инвентарным номером и полкой. Тогда правильнее добавить её, а не прятать поле
в словарь. Я думаю что для доменных данных лучше явно добавить сущность или поле, даже если для этого придётся сделать миграцию.


