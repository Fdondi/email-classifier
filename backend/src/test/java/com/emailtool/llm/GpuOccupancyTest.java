package com.emailtool.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class GpuOccupancyTest {

    @Test
    void idleGpuIsNotBusy() {
        GpuOccupancy occupancy = GpuOccupancy.parse("512, 24576, 24000", "").orElseThrow();
        assertFalse(occupancy.hasForeignCompute());
        assertFalse(occupancy.tooLittleFree(18000));
    }

    @Test
    void lowFreeMemoryIsBusy() {
        GpuOccupancy occupancy = GpuOccupancy.parse("20000, 24576, 4000", "").orElseThrow();
        assertEquals(
                "GPU has 4000 MiB free (20000 MiB used); need 18000 MiB free to load the chat model",
                occupancy.busyReason(18000));
    }

    @Test
    void ignoresLmStudioComputeProcess() {
        GpuOccupancy occupancy =
                GpuOccupancy.parse("1024, 24576, 23000", "111, C:\\Users\\x\\lms.exe, 1024 MiB").orElseThrow();
        assertFalse(occupancy.hasForeignCompute());
        assertTrue(occupancy.hasChatModelCompute());
    }

    @Test
    void llamaStillListedMeansGpuNotReleased() {
        GpuOccupancy occupancy =
                GpuOccupancy.parse("16000, 24576, 8000", "99, llama.dll, 15000 MiB").orElseThrow();
        assertTrue(occupancy.stillHeldAfterUnload());
        assertTrue(occupancy.heldAfterUnloadReason().contains("llama"));
    }

    @Test
    void highUsedMemoryWithoutComputeIsStillHeld() {
        GpuOccupancy occupancy = GpuOccupancy.parse("18000, 24576, 6000", "").orElseThrow();
        assertTrue(occupancy.stillHeldAfterUnload());
    }

    @Test
    void idleAfterUnload() {
        GpuOccupancy occupancy = GpuOccupancy.parse("400, 24576, 24000", "").orElseThrow();
        assertFalse(occupancy.stillHeldAfterUnload());
    }

    @Test
    void foreignComputeIsBusy() {
        GpuOccupancy occupancy =
                GpuOccupancy.parse("8000, 24576, 16000", "222, chrome.exe, 8000 MiB").orElseThrow();
        assertTrue(occupancy.hasForeignCompute());
        assertTrue(occupancy.busyReason(18000).contains("chrome.exe"));
    }

    @Test
    void emptyCsvIsUnavailable() {
        assertEquals(Optional.empty(), GpuOccupancy.parse(" ", ""));
    }
}
