package com.dzdp.service;

import com.dzdp.dto.Result;

public interface ISearchService {
    Result querySuggestions(String keyword, Long requestId);

    Result queryResults(String keyword, String type);
}
