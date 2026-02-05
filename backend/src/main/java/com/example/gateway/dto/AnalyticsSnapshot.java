package com.example.gateway.dto;

import com.example.gateway.model.TrafficLog;
import com.example.gateway.service.AnalyticsService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsSnapshot {
    private AnalyticsUpdate summary;
    private List<AnalyticsService.TimeSeriesDataPoint> timeseries;
    private List<TrafficLog> traffic;
}