package com.company.kra.modules.appraisal.dto;

import java.util.List;

public record CycleLaunchResponse(Long cycleId, String cycleName, int participantsCreated,
                                  int alreadyPresent, List<String> messages) {
}
