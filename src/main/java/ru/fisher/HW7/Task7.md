### Истинное наследование

Ознакомился с паттернами проектирования, но конечно по началу всё кажется понятным, но без практики на вскидку определять где и какой паттерн мне кажется пока сложно.
А уже применять это конечно тоже будет нелегко сначала. Буду разбираться, пока не увижу что-то подобное на практике.
Основная сложность в задании была найти реально стоящий пример с "неистинным" наследованием (методы родительских классов переопределяются в классах-наследниках).
Постараюсь написать самостоятельно насколько я понял паттерн "Посетитель" (Visitor). Рабочих примеров пока нет.

**_Пример кода с "неистинным" наследованием:_**
```java
abstract static class Handler {
    String export() {
        return "Handling....";
    }
}

static class JsonHandler extends Handler {
    @Override
    String export() {
        return "JSON handling: .....";
    }
}

static class CsvHandler extends Handler {
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

abstract static class Handler {
    abstract String accept(Visitor visitor);
}

static class JsonHandler extends Handler {
    @Override
    String accept(Visitor visitor) {
        return visitor.visit(this);
    }
}

static class CsvHandler extends Handler {
    @Override
    String accept(Visitor visitor) {
        return visitor.visit(this);
    }
}
```
