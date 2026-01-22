### Истинное наследование

Ознакомился с паттернами проектирования, но конечно по началу всё кажется понятным, но без практики на вскидку определять где и какой паттерн мне кажется пока сложно.
А уже применять это конечно тоже будет нелегко сначала. Буду разбираться, пока не увижу что-то подобное на практике.
Основная сложность в задании была найти реально стоящий пример с "неистинным" наследованием (методы родительских классов переопределяются в классах-наследниках).
Постараюсь написать самостоятельно насколько я понял паттерн "Посетитель" (Visitor). Рабочих примеров пока нет.

**_Пример кода с "неистинным" наследованием:_**
```java
abstract class Handler {
    String export() {
        return "Handling....";
    }
}

class JsonHandler extends Handler {
    @Override
    String export() {
        return "JSON handling: .....";
    }
}

class CsvHandler extends Handler {
    @Override
    String export() {
        return "CSV handling: .....";
    }
}
```
___

**_Пример кода с "истинным" наследованием (применение паттерна Visitor):_**
```java
interface Visitor {
    String visit(JsonHandler jsonHandler);
    String visit(CsvHandler csvHandler);
}

abstract class Handler {
    abstract String accept(Visitor visitor);
}

class JsonHandler extends Handler {
    @Override
    String accept(Visitor visitor) {
        return visitor.visit(this);
    }
}

class CsvHandler extends Handler {
    @Override
    String accept(Visitor visitor) {
        return visitor.visit(this);
    }
}

class ExportVisitor implements Visitor {

    @Override
    public String visit(JsonHandler handler) {
        return "JSON handling: .....";
    }

    @Override
    public String visit(CsvHandler handler) {
        return "CSV handling: .....";
    }
}
```

Вывод:
Я решил написать такой учебный пример где существует некий обработчик форматов данных.
В первой версии кода существует АТД класс Handler с методом export(), который затем переопределяется в дочерних классах
JsonHandler и CsvHandler, классическое "неистинное" наследование.
Дочерние классы знают про родительские методы, при добавлении или расширении методов можно добавить свои методы 
в дочерние или изменять в каждом классе, поведение распределяется по иерархии классов.

Во второй версии кода применил паттерн Visitor (насколько я его понял).
Реализуем интерфейс с методами для любого типа обработчика, метод АТД класса использует интерфейс вместо начальной реализации.
Бизнес-логика вынесена в отдельные Visitor's (Посетители), Handler уже 
