package com.example.ums.processing.model;

/**
 * cp_source 精简视图：入库时取 source_name 快照用。
 */
public class SourceBrief {

    private String sourceId;
    private String sourceName;

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
}
