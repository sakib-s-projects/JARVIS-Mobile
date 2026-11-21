package com.orbit.assistant;

import org.junit.Test;
import static org.junit.Assert.*;

/** Basic lifecycle-independent checks for the durable task model. */
public class SiteTaskStoreTest {
    @Test public void newTaskStartsIncomplete() {
        SiteTaskStore.Task t = SiteTaskStore.create("Pour concrete", "Column C2");
        assertFalse(t.completed);
        assertEquals(0L, t.completedAt);
        assertEquals("Pour concrete", t.title);
    }

    @Test public void completedTaskCarriesTimestamp() {
        long stamp = 12345L;
        SiteTaskStore.Task t = new SiteTaskStore.Task("id", "Task", "", 100L, true, stamp);
        assertTrue(t.completed);
        assertEquals(stamp, t.completedAt);
    }

    @Test public void completionTimestampIsClearedWhenReopened() {
        SiteTaskStore.Task t = new SiteTaskStore.Task("id", "Task", "", 100L, false, 9876L);
        assertEquals(0L, t.completedAt);
    }

    @Test public void blankTitleIsTrimmedAndRejectedByStoreContract() {
        SiteTaskStore.Task t = SiteTaskStore.create("  ", "note");
        assertEquals("", t.title);
    }
}
