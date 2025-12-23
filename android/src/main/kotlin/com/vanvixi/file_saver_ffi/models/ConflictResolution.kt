package com.vanvixi.file_saver_ffi.models

enum class ConflictResolution(val value: Int) {
    /**
     * Automatically rename file with (1), (2), (3) suffix pattern
     * Example: file.txt → file (1).txt → file (2).txt
     */
    AUTO_RENAME(0),

    /**
     * Overwrite existing file
     * WARNING: Data loss possible
     */
    OVERWRITE(1),

    /**
     * Fail operation if file exists
     * Returns error code: FILE_EXISTS
     */
    FAIL(2),

    /**
     * Skip operation silently if file exists
     * Returns success with existing file path
     */
    SKIP(3);

    companion object {
        /**
         * Converts integer value to ConflictResolution
         *
         * @param value Integer value (0-3)
         * @return Corresponding ConflictResolution, defaults to AUTO_RENAME if invalid
         */
        fun fromInt(value: Int): ConflictResolution =
            entries.find { it.value == value } ?: AUTO_RENAME
    }
}
