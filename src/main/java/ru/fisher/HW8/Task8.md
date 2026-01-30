### Visitor с примесями

В Java как таковое множественное наследование не применяется, но миксины (примеси) можно реализовать в виде дефолтного метода в интерфейсе, 
пример с примесями относительно прошлого примера с обработчиком:

```java
 interface Visitor {
    String visit(JsonHandler jsonHandler);
    String visit(CsvHandler csvHandler);
}

interface Loggable {
    default void log(String message) {
        System.out.println("log.info: " + message);
    }

    default void errLog(Exception exception, String message) {
        System.out.println(message + exception);
    }
}

abstract class Handler {
    abstract String accept(Visitor visitor);
}

class JsonHandler extends Handler implements Loggable {
    @Override
    String accept(Visitor visitor) {
        log("In JSON:");
        try {
            return visitor.visit(this);
        } catch (Exception e) {
            errLog(e, "Error");
            return "Ошибка обработки JSON";
        }
    }

}

class CsvHandler extends Handler implements Loggable {

    @Override
    String accept(Visitor visitor) {
        log("In CSV:");
        try {
            return visitor.visit(this);
        } catch (Exception e) {
            errLog(e, "Error");
            return "Ошибка обработки CSV";
        }
    }
}
```
  
В результате применения примеси, мы можем создать базовое поведение в дефолтном методе интерфейса, затем класс может имплементировать 
эти методы от интерфейса при необходимости и не обязательно, чтобы они присутствовали в классе.  
И таким образом получается выбирать необходимое поведение для разных классов на основе интерфейсов.
Получается вместо того, чтобы каждый класс сам реализовывал свою функциональность, вместо этого это делают миксины.