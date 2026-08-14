package com.forgeops.demoapi.web;

import com.forgeops.demoapi.domain.PatientRecordStore;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PatientController {

    private static final Logger log = LoggerFactory.getLogger(PatientController.class);

    private final PatientRecordStore store;

    public PatientController(PatientRecordStore store) {
        this.store = store;
    }

    /**
     * 检查记录查询。汇总逻辑取最后一条记录作为 latest ——
     * 患者无记录时抛 IndexOutOfBoundsException → 500（预埋 Bug，B1 验收复现项）。
     * 正确行为：空数据返回 total=0、latest=null，由前端展示空态。
     */
    @GetMapping("/patients/{patientId}/records")
    public Map<String, Object> records(@PathVariable String patientId) {
        List<PatientRecordStore.ExamRecord> records = store.findByPatient(patientId);
        log.info("query patient records patientId={} size={}", patientId, records.size());

        Map<String, Object> summary = new HashMap<>();
        summary.put("total", records.size());
        summary.put("latest", records.get(records.size() - 1));

        Map<String, Object> body = new HashMap<>();
        body.put("patientId", patientId);
        body.put("summary", summary);
        body.put("records", records);
        body.put("serverTime", Instant.now().toString());
        return body;
    }
}
