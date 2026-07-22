package com.sql.tc.generator.dto;

import java.util.Map;

public class RenderRequest {
    private String rawXml;
    private Map<String, Object> selections;
    private Map<String, String> paramValues;
    private String bindStyle = "NAMED";

    public String getRawXml() {
        return rawXml;
    }

    public void setRawXml(String rawXml) {
        this.rawXml = rawXml;
    }

    public Map<String, Object> getSelections() {
        return selections;
    }

    public void setSelections(Map<String, Object> selections) {
        this.selections = selections;
    }

    public Map<String, String> getParamValues() {
        return paramValues;
    }

    public void setParamValues(Map<String, String> paramValues) {
        this.paramValues = paramValues;
    }

    public String getBindStyle() {
        return bindStyle;
    }

    public void setBindStyle(String bindStyle) {
        this.bindStyle = bindStyle;
    }
}