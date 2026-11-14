package com.scout.springserver.dto;

/**
 * Route-job progress (RouteProgressSnapshot in route-progress.service.ts), sent
 * by the REST endpoints and the `route-progress` socket event.
 * status: 'idle' | 'in_progress' | 'done' | 'error'.
 */
public class RouteProgressResponseDTO {
    private String status;
    private Integer completed;
    private Integer total;
    private String error;
    private String startedAt;
    private String finishedAt;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getCompleted() {
        return completed;
    }

    public void setCompleted(Integer completed) {
        this.completed = completed;
    }

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(String startedAt) {
        this.startedAt = startedAt;
    }

    public String getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(String finishedAt) {
        this.finishedAt = finishedAt;
    }
}
