package com.saleh.wadhkur

/**
 * Keeps legacy date-calculation code type-safe when a calculated month index is Long.
 * The valid Islamic month range is still checked by the caller before indexing.
 */
operator fun List<String>.get(index: Long): String = get(index.toInt())
