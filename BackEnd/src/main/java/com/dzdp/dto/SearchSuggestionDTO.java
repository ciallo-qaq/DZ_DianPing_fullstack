package com.dzdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchSuggestionDTO {
    private String suggestionText;
    private String type;
    private Long sourceId;
    private String title;
    private String subtitle;
    private String matchType;
}
