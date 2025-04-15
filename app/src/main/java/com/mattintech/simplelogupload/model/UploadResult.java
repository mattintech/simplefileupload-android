package com.mattintech.simplelogupload.model;

public class UploadResult {
    private String code;
    private String error;

    public UploadResult(String code) {
        this.code = code;
        this.error = null;
    }

    public UploadResult(String error, boolean isError) {
        this.code = null;
        this.error = error;
    }

    public String getCode() {
        return code;
    }

    public String getError() {
        return error;
    }

    public boolean isSuccess() {
        return code != null && !code.isEmpty();
    }
}
