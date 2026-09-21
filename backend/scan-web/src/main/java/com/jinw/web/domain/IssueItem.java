package com.jinw.web.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class IssueItem {
    private int index;
    private String description;
    private String suggestion;
    private String fileName;
    private int lineNumber;
}