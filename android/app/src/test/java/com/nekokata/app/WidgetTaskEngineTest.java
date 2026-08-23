package com.nekokata.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.Test;

public class WidgetTaskEngineTest {

    @Test
    public void parseSlotStartMinutes_readsStartOfRange() {
        assertEquals(Integer.valueOf(9 * 60 + 30), WidgetTaskEngine.parseSlotStartMinutes("09:30\u201310:00"));
        assertEquals(Integer.valueOf(8 * 60), WidgetTaskEngine.parseSlotStartMinutes("8:00"));
        assertNull(WidgetTaskEngine.parseSlotStartMinutes(""));
        assertNull(WidgetTaskEngine.parseSlotStartMinutes(null));
    }

    @Test
    public void formatTimeRange_usesEmDash() {
        assertEquals("09:30 — 10:00", WidgetTaskEngine.formatTimeRange("09:30\u201310:00"));
        assertEquals("09:30", WidgetTaskEngine.formatTimeRange("09:30"));
    }

    @Test
    public void getNextSlot_isTheFollowingId() {
        WidgetTaskEngine.Slot a = new WidgetTaskEngine.Slot("a", "08:00", "A", "train");
        WidgetTaskEngine.Slot b = new WidgetTaskEngine.Slot("b", "09:00", "B", "body");
        List<WidgetTaskEngine.Slot> slots = Arrays.asList(a, b);
        assertEquals("b", WidgetTaskEngine.getNextSlot(slots, a).id);
        assertNull(WidgetTaskEngine.getNextSlot(slots, b));
        assertNull(WidgetTaskEngine.getNextSlot(slots, null));
    }

    @Test
    public void dayProgress_countsDoneBySlotKey() {
        WidgetTaskEngine.Slot a = new WidgetTaskEngine.Slot("a", "08:00", "A", "train");
        WidgetTaskEngine.Slot b = new WidgetTaskEngine.Slot("b", "09:00", "B", "body");
        WidgetTaskEngine.DayProgress p = WidgetTaskEngine.dayProgress(
            Arrays.asList(a, b),
            new HashSet<>(Collections.singletonList("train:a"))
        );
        assertEquals(1, p.done);
        assertEquals(2, p.total);
        assertEquals(50, p.percent());
    }

    @Test
    public void taskState_defaultsCatAndSlotKey() {
        WidgetStateStore.TaskState t = new WidgetStateStore.TaskState("x", "Lift", "08:00", "08:00–09:00", "");
        assertEquals("train", t.cat);
        assertEquals("train:x", t.slotKey());
    }
}
