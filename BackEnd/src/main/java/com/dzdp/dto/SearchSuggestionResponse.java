package com.dzdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchSuggestionResponse {
    private String keyword;
    private Long requestId;
    private List<SearchSuggestionDTO> suggestions;
}
