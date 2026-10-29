package com.klef.ps024.maintenance.exception;

import java.util.Map;

public record ApiError(String message, Map<String, String> fieldErrors) {
}
