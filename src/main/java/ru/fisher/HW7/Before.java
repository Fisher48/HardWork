package ru.fisher.HW7;

public class Before {
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

}


