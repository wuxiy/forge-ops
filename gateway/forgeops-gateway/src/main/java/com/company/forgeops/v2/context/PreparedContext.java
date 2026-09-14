package com.company.forgeops.v2.context;

/** The only representation of browser-supplied context allowed into workflow storage or an Agent prompt. */
public record PreparedContext(String title, String description, String contentJson, String sha256, int redactionCount) {
}
