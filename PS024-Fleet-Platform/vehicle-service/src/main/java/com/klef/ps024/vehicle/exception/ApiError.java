package com.klef.ps024.vehicle.exception;

import java.util.Map;

public record ApiError(String message, Map<String, String> fieldErrors) {
}
