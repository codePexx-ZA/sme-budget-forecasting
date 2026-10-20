package com.smebudget.api.targets;

/** One warning from the C# rules service (inflation, fuel, electricity and similar). */
public record AlertView(String severity, String message, String resolutionStatus) {
}
