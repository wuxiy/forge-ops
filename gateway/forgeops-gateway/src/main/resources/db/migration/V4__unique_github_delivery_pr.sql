ALTER TABLE delivery_evidence
    ADD CONSTRAINT uq_delivery_evidence_github_pr UNIQUE (repository, pull_request_no);
