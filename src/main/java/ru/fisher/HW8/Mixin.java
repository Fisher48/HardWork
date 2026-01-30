package ru.fisher.HW8;

import ru.fisher.HW7.After;

public class Mixin {
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

    class ExportVisitor implements Visitor {
        @Override
        public String visit(JsonHandler jsonHandler) {
            return "JSON Handler...";
        }

        @Override
        public String visit(CsvHandler csvHandler) {
            return "CSV Handler...";
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

}
