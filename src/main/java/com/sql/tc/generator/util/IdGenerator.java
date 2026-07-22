package com.sql.tc.generator.util;

public class IdGenerator {
    private int counter = 0;

    public String next() {
        return "n" + (counter++);
    }
}
