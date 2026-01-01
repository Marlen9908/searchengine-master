package searchengine.dto.statistics;

public class ApiResponse {

    private boolean result;
    private String error;

    public ApiResponse() {
    }

    public ApiResponse(boolean result) {
        this.result = result;
    }

    public ApiResponse(boolean result, String error) {
        this.result = result;
        this.error = error;
    }

    public boolean isResult() {
        return result;
    }

    public void setResult(boolean result) {
        this.result = result;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}

