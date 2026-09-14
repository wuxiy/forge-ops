package com.company.forgeops.v2.integration.inbox;

import com.company.forgeops.v2.integration.domain.IntegrationEvent;

/** Applies a persisted Inbox fact. It must not call a remote provider. */
public interface IntegrationApplier {
    ApplyResult apply(IntegrationEvent event);

    record ApplyResult(Disposition disposition, String detail) {
        public static ApplyResult applied() { return new ApplyResult(Disposition.APPLIED, null); }
        public static ApplyResult deferred(String detail) { return new ApplyResult(Disposition.DEFERRED, detail); }
        public static ApplyResult rejected(String detail) { return new ApplyResult(Disposition.REJECTED, detail); }
    }

    enum Disposition { APPLIED, DEFERRED, REJECTED }
}
