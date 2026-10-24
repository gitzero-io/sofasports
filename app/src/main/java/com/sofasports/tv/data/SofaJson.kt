package com.sofasports.tv.data

import kotlinx.serialization.json.Json

/** One lenient Json instance for every remote API: unknown keys are ignored, nulls coerce to defaults. */
val SofaJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
}
