package com.forgeops.demoapi.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** 演示用内存数据：P-10001 有数据，其余患者为空（触发预埋 Bug）。 */
@Component
public class PatientRecordStore {

    public record ExamRecord(String id, String patientId, String name, String status, Instant time) {
    }

    private final Map<String, List<ExamRecord>> byPatient = new ConcurrentHashMap<>();

    public PatientRecordStore() {
        byPatient.put("P-10001", List.of(
                new ExamRecord("EX-9001", "P-10001", "血常规", "DONE", Instant.parse("2026-08-10T02:11:00Z")),
                new ExamRecord("EX-9002", "P-10001", "心电图", "DONE", Instant.parse("2026-08-12T05:30:00Z")),
                new ExamRecord("EX-9003", "P-10001", "胸部CT", "PENDING", Instant.parse("2026-08-14T08:02:00Z"))));
    }

    public List<ExamRecord> findByPatient(String patientId) {
        return byPatient.getOrDefault(patientId, List.of());
    }
}
