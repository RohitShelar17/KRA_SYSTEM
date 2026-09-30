package com.company.kra.modules.task.dto;

/** Counts behind the status tabs on the task screens. */
public record TaskBoardCountsResponse(long total, long notStarted, long inProgress, long completed,
                                      long onHold, long blocked, long overdue) {
}
