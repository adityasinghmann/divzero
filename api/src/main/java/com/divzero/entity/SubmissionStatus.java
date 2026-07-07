package com.divzero.entity;

public enum SubmissionStatus {
    QUEUED("Queued"),
    RUNNING("Running"),
    ACCEPTED("Accepted"),
    WRONG_ANSWER("Wrong Answer"),
    COMPILATION_ERROR("Compilation Error"),
    RUNTIME_ERROR("Runtime Error"),
    TIME_LIMIT_EXCEEDED("Time Limit Exceeded"),
    INTERNAL_ERROR("Internal Error");

    private final String displayVerdict;

    SubmissionStatus(String displayVerdict) {
        this.displayVerdict = displayVerdict;
    }

    public String displayVerdict() {
        return displayVerdict;
    }

    public boolean terminal() {
        return switch (this) {
            case ACCEPTED,
                 WRONG_ANSWER,
                 COMPILATION_ERROR,
                 RUNTIME_ERROR,
                 TIME_LIMIT_EXCEEDED,
                 INTERNAL_ERROR -> true;
            case QUEUED, RUNNING -> false;
        };
    }
}
