#ifndef file_saver_ffi_h
#define file_saver_ffi_h

#include <stdint.h>
#include <stdbool.h>

typedef struct {
    bool success;
    const char* filePath;
    const char* fileUri;
    const char* errorCode;
    const char* errorMessage;
} FSaveResult;

typedef void (*FSaveResultCallback)(FSaveResult*);

void* file_saver_init(void);

void file_saver_save_bytes_async(
    void* instance,
    const uint8_t* fileData,
    int64_t fileDataLength,
    const char* baseFileName,
    const char* extension,
    const char* mimeType,
    const char* subDir,
    int32_t conflictMode,
    FSaveResultCallback callback
);

void file_saver_free_result(FSaveResult* result);

void file_saver_dispose(void* instance);

#endif
