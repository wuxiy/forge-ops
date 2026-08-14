package com.forgeops.demoapi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.forgeops.demoapi.domain.PatientRecordStore;
import com.forgeops.demoapi.web.RequestIdFilter;
import com.forgeops.demoapi.web.RequestLogStore;
import com.forgeops.demoapi.web.VersionProvider;

/**
 * 记录正确行为契约（Acceptance Criteria）：
 * 1. 有数据患者：200，records 非空，summary.total=3
 * 2. 无数据患者：200，summary.total=0、latest=null、records=[]（FB-1002 修复后行为）
 */
@WebMvcTest(excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
        type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
        classes = RequestIdFilter.class))
@Import({PatientRecordStore.class, VersionProvider.class, RequestLogStore.class})
class PatientControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void patientWithDataReturnsRecords() throws Exception {
        mvc.perform(get("/api/patients/P-10001/records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.total").value(3))
                .andExpect(jsonPath("$.summary.latest").isNotEmpty())
                .andExpect(jsonPath("$.records[0].name").value("血常规"));
    }

    @Test
    void patientWithoutDataShouldReturnEmptyState() throws Exception {
        mvc.perform(get("/api/patients/P-99999/records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.total").value(0))
                .andExpect(jsonPath("$.summary.latest").value((Object) null))
                .andExpect(jsonPath("$.records").isEmpty());
    }
}
