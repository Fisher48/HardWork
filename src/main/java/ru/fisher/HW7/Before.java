package ru.fisher.HW7;

public class Before {
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

}


