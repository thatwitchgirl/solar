package com.solar.launcher;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

/** ponytail: keyboard purpose ints must be unique — duplicate broke podcast search (was 4 = SOULSEEK_FIND). */
public class KeyboardPurposeConstantsTest {

    @Test
    public void keyboardPurposeValuesAreUnique() throws Exception {
        Set<Integer> seen = new HashSet<Integer>();
        for (Field f : MainActivity.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) continue;
            if (!f.getName().startsWith("KEYBOARD_")) continue;
            if (f.getType() != int.class) continue;
            f.setAccessible(true);
            int v = f.getInt(null);
            if (!seen.add(v)) {
                throw new AssertionError("duplicate KEYBOARD_* value " + v + " at " + f.getName());
            }
        }
    }
}
