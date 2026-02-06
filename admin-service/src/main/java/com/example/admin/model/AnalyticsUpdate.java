package com.example.admin.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsUpdate {
    private Long requestsAllowed;
    private Long requestsBlocked;
    private Integer activePolicies;
}
