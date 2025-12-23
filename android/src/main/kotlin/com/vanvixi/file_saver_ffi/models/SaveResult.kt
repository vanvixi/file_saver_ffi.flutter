package com.vanvixi.file_saver_ffi.models

data class SaveResult(
    val isSuccess: Boolean,
    val filePath: String? = null,
    val uri: String? = null,
    val errorMessage: String? = null,
    val errorCode: String? = null
) {
    companion object {
        /**
         * Creates a success result
         *
         * @param filePath The absolute file path where the file was saved
         * @param uri The content URI for the saved file
         * @return SaveResult with success = true
         */
        fun success(filePath: String, uri: String) = SaveResult(
            isSuccess = true,
            filePath = filePath,
            uri = uri
        )

        /**
         * Creates a failure result
         *
         * @param errorCode Machine-readable error code
         * @param errorMessage Human-readable error description
         * @return SaveResult with success = false
         */
        fun failure(errorCode: String, errorMessage: String) = SaveResult(
            isSuccess = false,
            errorCode = errorCode,
            errorMessage = errorMessage
        )
    }
}
