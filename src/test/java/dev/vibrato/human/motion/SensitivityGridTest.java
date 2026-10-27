package dev.vibrato.human.motion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SensitivityGridTest {

    private static final double[] SENSITIVITIES = {0, 0.2, 0.5, 0.83, 1};

    @Test
    void stepMatchesTheVanillaFormula() {
        assertEquals(0.15, SensitivityGrid.degreesPerPixel(0.5), 1e-9);
        assertEquals(0.6144, SensitivityGrid.degreesPerPixel(1), 1e-9);
    }

    @Test
    void quantizedValuesAreWholeMultiplesOfTheStep() {
        for (double sensitivity : SENSITIVITIES) {
            double step = SensitivityGrid.degreesPerPixel(sensitivity);
            for (double degrees = -180; degrees <= 180; degrees += 0.37) {
                double quantized = SensitivityGrid.quantize(degrees, sensitivity);
                double ratio = quantized / step;
                assertEquals(Math.rint(ratio), ratio, 1e-9, "degrees " + degrees + " sens " + sensitivity);
                assertTrue(Math.abs(quantized - degrees) <= step / 2 + 1e-9);
            }
        }
    }

    @Test
    void pixelsRoundTripThroughTheGrid() {
        double step = SensitivityGrid.degreesPerPixel(0.5);
        assertEquals(12, SensitivityGrid.pixels(12 * step, 0.5));
        assertEquals(12 * step, SensitivityGrid.quantize(12 * step, 0.5), 1e-9);
    }

    @Test
    void sameInputGivesSameOutput() {
        assertEquals(SensitivityGrid.quantize(33.3, 0.7), SensitivityGrid.quantize(33.3, 0.7));
    }

    @Test
    void sensitivityOutsideTheSliderIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> SensitivityGrid.degreesPerPixel(1.5));
        assertThrows(IllegalArgumentException.class, () -> SensitivityGrid.degreesPerPixel(-0.1));
    }
}
