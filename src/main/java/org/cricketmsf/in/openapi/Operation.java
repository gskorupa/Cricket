/*
 * Copyright 2020 Grzegorz Skorupa <g.skorupa at gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.cricketmsf.in.openapi;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author greg
 */
public class Operation extends Element {

    private String description;
    private String summary;
    private List<Parameter> parameters = new ArrayList<>();
    private List<Response> responses = new ArrayList<>();
    private List<String> tags = new ArrayList<>();

    public Operation() {
        parameters = new ArrayList<>();
        responses = new ArrayList<>();
        tags = new ArrayList<>();
    }

    public Operation description(String description) {
        setDescription(description);
        return this;
    }

    public Operation summary(String summary) {
        setSummary(summary);
        return this;
    }

    public Operation response(Response response) {
        responses.add(response);
        return this;
    }

    public Operation parameter(Parameter parameter) {
        parameters.add(parameter);
        return this;
    }

    public Operation tag(String tag) {
        if (!tags.contains(tag)) {
            tags.add(tag);
        }
        return this;
    }

    public String toYaml(String indent) {
        StringBuilder sb = new StringBuilder();
        if (tags.size() > 0) {
            sb.append(indent).append("tags:").append(lf);
            tags.forEach(tag -> {
                sb.append(indent).append("- \"").append(tag).append("\"").append(lf);
            });
        }
        if (null != description) {
            sb.append(indent).append("description: \"").append(getDescription()).append("\"").append(lf);
        }
        if (null != summary) {
            sb.append(indent).append("summary: \"").append(getSummary()).append("\"").append(lf);
        }
        if (parameters.size() > 0) {
            sb.append(indent).append("parameters:").append(lf);
            parameters.forEach(parameter -> {
                sb.append(indent).append("- name: \"").append(parameter.getName()).append("\"").append(lf);
                sb.append(parameter.toYaml(indent + indentStep));
            });
        }
        if (responses.size() > 0) {
            sb.append(indent).append("responses:").append(lf);
            responses.forEach(response -> {
                sb.append(response.toYaml(indent + indentStep));
            });
        }
        return sb.toString();
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return the summary
     */
    public String getSummary() {
        return summary;
    }

    /**
     * @param summary the summary to set
     */
    public void setSummary(String summary) {
        this.summary = summary;
    }

    /**
     * @return the parameters
     */
    public List<Parameter> getParameters() {
        return parameters;
    }

    /**
     * @param parameters the parameters to set
     */
    public void setParameters(List<Parameter> parameters) {
        this.parameters = parameters;
    }

    /**
     * @return the responses
     */
    public List<Response> getResponses() {
        return responses;
    }

    /**
     * @param responses the responses to set
     */
    public void setResponses(List<Response> responses) {
        this.responses = responses;
    }
}