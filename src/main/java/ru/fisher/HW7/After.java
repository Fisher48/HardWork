package ru.fisher.HW7;

public class After {
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

    static class ExportVisitor implements Visitor {
        @Override
        public String visit(JsonHandler jsonHandler) {
            return "JSON Handler...";
        }

        @Override
        public String visit(CsvHandler csvHandler) {
            return "CSV Handler...";
        }
    }

    static class ImportVisitor implements Visitor {
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
