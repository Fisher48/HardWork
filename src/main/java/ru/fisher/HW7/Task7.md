### Истинное наследование

Ознакомился с паттернами проектирования, но конечно по началу всё кажется понятным, но без практики на вскидку определять где и какой паттерн мне кажется пока сложно.
А уже применять это конечно тоже будет нелегко сначала. Буду разбираться, пока не увижу что-то подобное на практике.
Основная сложность в задании была найти реально стоящий пример с "неистинным" наследованием (методы родительских классов переопределяются в классах-наследниках).
Постараюсь написать самостоятельно насколько я понял паттерн "Посетитель" (Visitor). Рабочих примеров пока нет.

**_Пример кода с "неистинным" наследованием:_**
```java
abstract class Handler {
    abstract String export();
    abstract String importData();
}

class JsonHandler extends Handler {
    @Override
    String export() {
        return "JSON handling: .....";
    }

    @Override
    String importData() {
        return "Import JSON data:.....";
    }

}

class CsvHandler extends Handler {
    @Override
    String export() {
        return "CSV handling: .....";
    }

    @Override
    String importData() {
        return "Import CSV data:....";
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

class ImportVisitor implements Visitor {
    @Override
    public String visit(JsonHandler jsonHandler) {
        return "JSON Handler...";
    }

    @Override
    public String visit(CsvHandler csvHandler) {
        return "CSV Handler...";
    }
}
```

**_Вывод:_**  
Я решил написать такой учебный пример, где существует некий обработчик форматов данных.  
В первой версии кода существует АТД класс Handler с методом export(), который затем переопределяется в дочерних классах
JsonHandler и CsvHandler, классическое "неистинное" наследование.
Дочерние классы знают про родительские методы, при добавлении или расширении методов можно добавить свои методы 
в дочерние или изменять в каждом классе.
По сути теряется смысл в родительском классе, ведь все новые унаследованные классы полностью переписывают поведение с нуля. 
Если появляется новый класс, то вся логика будет дальше растягиваться по иерархии.

Во второй версии кода применил паттерн Visitor (насколько я его понял).
Реализуем интерфейс с методами для любого типа обработчика, метод АТД класса использует интерфейс вместо начальной реализации.
Бизнес-логика вынесена в отдельные Visitor's (Посетители), Handler уже простые и не содержат логику.
Новые операции добавляются как отдельные классы. Конечно это сложнее понимать, и каждый новый Handler это правка в Посетителя.
Если операций мало, то такой паттерн избыточен и лучше ограничится обычным полиморфизмом.
