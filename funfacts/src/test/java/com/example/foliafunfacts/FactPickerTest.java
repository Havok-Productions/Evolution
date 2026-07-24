package com.example.foliafunfacts;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FactPickerTest {
    @Test
    void returnsNullForEmptyList() {
        assertNull(new FactPicker().pick(List.of(), FactOrder.RANDOM));
    }

    @Test
    void sequentialOrderWrapsAround() {
        FactPicker picker = new FactPicker();
        List<String> facts = List.of("one", "two");

        assertEquals("one", picker.pick(facts, FactOrder.SEQUENTIAL));
        assertEquals("two", picker.pick(facts, FactOrder.SEQUENTIAL));
        assertEquals("one", picker.pick(facts, FactOrder.SEQUENTIAL));
    }

    @Test
    void randomOrderDoesNotImmediatelyRepeat() {
        FactPicker picker = new FactPicker();
        List<String> facts = List.of("one", "two", "three");

        String previous = picker.pick(facts, FactOrder.RANDOM);
        for (int i = 0; i < 100; i++) {
            String current = picker.pick(facts, FactOrder.RANDOM);
            assertNotEquals(previous, current);
            previous = current;
        }
    }
}
