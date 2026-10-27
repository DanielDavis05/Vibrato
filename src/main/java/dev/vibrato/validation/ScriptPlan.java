package dev.vibrato.validation;

import java.util.ArrayList;
import java.util.List;

/** Parser for the opt-in development script; delays are relative to the preceding action. */
public final class ScriptPlan {
    public enum Kind { CLIENT, VANILLA, WAIT_IDLE, EXIT }
    public record Step(double delaySeconds, Kind kind, String text, double timeoutSeconds) {}

    public static List<Step> parse(List<String> lines) {
        List<Step> steps = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("\\s+", 2);
            try {
                if (parts.length != 2) throw new IllegalArgumentException("expected delay and action");
                double delay = number(parts[0]);
                String text = parts[1].trim();
                Kind kind;
                double timeout = 0;
                if (text.equals("exit")) kind = Kind.EXIT;
                else if (text.startsWith("wait-idle ")) {
                    kind = Kind.WAIT_IDLE;
                    timeout = number(text.substring("wait-idle ".length()).trim());
                } else if (text.startsWith("@") && text.length() > 1) kind = Kind.CLIENT;
                else if (text.startsWith("/") && text.length() > 1) kind = Kind.VANILLA;
                else throw new IllegalArgumentException("unknown action");
                steps.add(new Step(delay, kind, text, timeout));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("script line " + (i + 1) + ": " + e.getMessage(), e);
            }
        }
        return List.copyOf(steps);
    }

    private static double number(String value) {
        double n = Double.parseDouble(value);
        if (!Double.isFinite(n) || n < 0 || n > 86400) throw new IllegalArgumentException("seconds must be between 0 and 86400");
        return n;
    }
}
